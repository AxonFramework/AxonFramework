/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.deadletter.jpa;

import com.thoughtworks.xstream.XStream;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueueOverflowException;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.NoSuchDeadLetterException;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * JPA-backed implementation of the {@link SequencedDeadLetterQueue}, used for storing dead letters containing
 * {@link EventMessage Eventmessages} as a {@link DeadLetterEntry}.
 * <p>
 * Keeps the insertion order intact by saving an incremented index within each sequence, backed by the
 * {@link DeadLetterEntry#getIndex()} property.
 * <p>
 * When processing an item, single execution across all applications is guaranteerd by setting the
 * {@link DeadLetterEntry#getProcessingStarted()} property, locking other processes out of the sequence for the
 * configured {@code claimDuration} (30 seconds by default).
 * <p>
 * Configuring a chain of upcasters is not supported.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class JpaSequencedDeadLetterQueue<M extends EventMessage<?>> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * {@link Clock} instance used to set the time on new {@link DeadLetter}s. To fix the time while testing set this
     * value to a constant value.
     */
    public static Clock clock = Clock.systemUTC();


    private final String processingGroup;
    private final EntityManagerProvider entityManagerProvider;
    private final List<DeadLetterJpaConverter<EventMessage<?>>> converters;
    private final TransactionManager transactionManager;
    private final int maxSequences;
    private final int maxSequenceSize;
    private final Serializer serializer;
    private final Duration claimDuration;

    protected <T extends EventMessage<?>> JpaSequencedDeadLetterQueue(Builder<T> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
        this.entityManagerProvider = builder.entityManagerProvider;
        this.transactionManager = builder.transactionManager;
        this.serializer = builder.serializer;
        this.converters = builder.converters;
        this.claimDuration = builder.claimDuration;
    }

    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public void enqueue(@Nonnull Object sequenceIdentifier, @Nonnull DeadLetter<? extends M> letter)
            throws DeadLetterQueueOverflowException {
        String sequence = toIdentifier(sequenceIdentifier);
        if (isFull(sequence)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue [" + letter.message() + "] for identifier ["
                            + sequence + "] since the queue is full."
            );
        }

        if (logger.isDebugEnabled()) {
            Optional<Cause> optionalCause = letter.cause();
            if (optionalCause.isPresent()) {
                logger.debug("Adding dead letter [{}] because [{}].", letter.message(), optionalCause.get());
            } else {
                logger.debug("Adding dead letter [{}] because the sequence identifier [{}] is already present.",
                             letter.message(), sequence);
            }
        }

        DeadLetterEntryMessage converter = converters
                .stream()
                .filter(c -> c.canConvert(letter.message()))
                .findFirst()
                .map(c -> c.toEntry(letter.message(), serializer))
                .orElseThrow(() -> new IllegalArgumentException("No converter found"));
        Long maxIndexForQueueIdentifier = getNextIndexForSequence(sequence);
        DeadLetterEntry deadLetter = new DeadLetterEntry(processingGroup,
                                                         sequence,
                                                         maxIndexForQueueIdentifier,
                                                         converter,
                                                         letter.enqueuedAt(),
                                                         letter.lastTouched(),
                                                         letter.cause().orElse(null),
                                                         letter.diagnostics(),
                                                         serializer);
        entityManager().persist(deadLetter);
    }

    @Override
    public void evict(DeadLetter<? extends M> letter) {
        if (!(letter instanceof JpaDeadLetter)) {
            throw new WrongDeadLetterTypeException(
                    "Evict should be called with a JpaDeadLetter instance. Instead got: [" + letter.getClass().getName()
                            + "]");
        }
        JpaDeadLetter<M> jpaDeadLetter = (JpaDeadLetter<M>) letter;

        transactionManager.executeInTransaction(() -> {
            entityManager().createQuery(
                                   "delete from DeadLetterEntry dl where dl.deadLetterId=:deadLetterId")
                           .setParameter("deadLetterId", jpaDeadLetter.getId())
                           .executeUpdate();
        });
    }

    private EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }

    @Override
    public void requeue(@Nonnull DeadLetter<? extends M> letter,
                        @Nonnull Function<DeadLetter<? extends M>, DeadLetter<? extends M>> letterUpdater)
            throws NoSuchDeadLetterException {
        if (!(letter instanceof JpaDeadLetter)) {
            throw new WrongDeadLetterTypeException(
                    "Evict should be called with a JpaDeadLetter instance. Instead got: [" + letter.getClass().getName()
                            + "]");
        }
        EntityManager entityManager = entityManager();
        DeadLetter<? extends M> updatedLetter = letterUpdater.apply(letter).markTouched();
        String id = ((JpaDeadLetter<? extends M>) letter).getId();
        DeadLetterEntry letterEntity = entityManager.find(DeadLetterEntry.class,
                                                          id);
        if (letterEntity == null) {
            throw new NoSuchDeadLetterException("Can not find dead letter with id [" + id + "]");
        }
        letterEntity.setDiagnostics(updatedLetter.diagnostics(), serializer);
        letterEntity.setLastTouched(updatedLetter.lastTouched());
        letterEntity.setCause(updatedLetter.cause().orElse(null));
        letterEntity.clearProcessingStarted();
        entityManager.persist(letterEntity);
    }

    @Override
    public boolean contains(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        return getNumberInQueue(identifier) > 0;
    }

    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);

        return entityManagerProvider
                .getEntityManager()
                .createQuery(
                        "SELECT dl FROM DeadLetterEntry dl where dl.processingGroup=:processingGroup and dl.sequence=:identifier",
                        DeadLetterEntry.class)
                .setParameter("processingGroup", processingGroup)
                .setParameter("identifier", identifier)
                .getResultList()
                .stream()
                .map(this::toLetter)
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        List<String> sequences = entityManagerProvider
                .getEntityManager()
                .createQuery(
                        "SELECT dl.sequence FROM DeadLetterEntry dl where dl.processingGroup=:processingGroup order by dl.lastTouched asc",
                        String.class)
                .setParameter("processingGroup", processingGroup)
                .getResultList();


        return () -> {
            Iterator<String> sequenceIterator = sequences.iterator();
            return new Iterator<Iterable<DeadLetter<? extends M>>>() {
                @Override
                public boolean hasNext() {
                    return sequenceIterator.hasNext();
                }

                @Override
                public Iterable<DeadLetter<? extends M>> next() {
                    String next = sequenceIterator.next();
                    return deadLetterSequence(next);
                }
            };
        };
    }

    /**
     * Converts a {@link DeadLetterEntry} from the database into a {@link JpaDeadLetter}, using the configured
     * {@link DeadLetterJpaConverter DeadLetterJpaConverters} to restore the original message from it.
     *
     * @param entry The entry to convert.
     * @return The {@link DeadLetter} result.
     */
    @SuppressWarnings("unchecked")
    private JpaDeadLetter<M> toLetter(DeadLetterEntry entry) {
        MetaData deserializedDiagnostics = serializer.deserialize(new SimpleSerializedObject<>(
                entry.getDiagnostics(),
                byte[].class,
                MetaData.class.getName(),
                null));
        DeadLetterJpaConverter<M> converter = (DeadLetterJpaConverter<M>) converters
                .stream()
                .filter(c -> c.canConvert(entry.getMessage()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such converter"));
        return new JpaDeadLetter<>(entry,
                                   deserializedDiagnostics,
                                   (M) converter.fromEntry(entry.getMessage(), serializer));
    }

    @Override
    public boolean isFull(@Nonnull Object sequenceIdentifier) {
        String identifier = toIdentifier(sequenceIdentifier);
        return maximumNumberOfQueuesReached(identifier) || maximumQueueSizeReached(identifier);
    }

    @Override
    public long maxSequences() {
        return maxSequences;
    }

    @Override
    public long maxSequenceSize() {
        return maxSequenceSize;
    }

    @Override
    public boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                           @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        // Search for an available sequence, that is first up for grabs.
        List<DeadLetterEntry> availableFirstLetters = entityManager()
                .createQuery(
                        "SELECT dl FROM DeadLetterEntry dl "
                                + "where dl.processingGroup=:processingGroup "
                                + "and dl.index = (select min(dl2.index) from DeadLetterEntry dl2 where dl2.processingGroup=dl.processingGroup and dl2.sequence=dl.sequence) "
                                + "and (dl.processingStarted is null or dl.processingStarted < :processingStartedLimit) "
                                + "order by dl.lastTouched asc ",
                        DeadLetterEntry.class
                )
                .setParameter("processingGroup", processingGroup)
                .setParameter("processingStartedLimit",
                              getProcessingStartedLimit())
                .getResultList();

        Optional<JpaDeadLetter<M>> first = availableFirstLetters
                .stream()
                .map(this::toLetter)
                .filter(sequenceFilter)
                .findFirst();
        if (!first.isPresent()) {
            return false;
        }

        JpaDeadLetter<M> deadLetter = first.get();
        claimDeadLetter(deadLetter);

        while (deadLetter != null) {
            EnqueueDecision<M> decision = processingTask.apply(deadLetter);
            if (!decision.shouldEnqueue()) {
                JpaDeadLetter<M> oldLetter = deadLetter;
                DeadLetterEntry deadLetterEntry = findNextDeadLetter(oldLetter);
                if (deadLetterEntry != null) {
                    deadLetter = toLetter(deadLetterEntry);
                    claimDeadLetter(deadLetter);
                } else {
                    deadLetter = null;
                }
                evict(oldLetter);
            } else {
                requeue(deadLetter,
                        l -> decision.addDiagnostics(l).withCause(decision.enqueueCause().orElse(null)));
                return false;
            }
        }

        return true;
    }

    /**
     * Finds the next dead letter after the provided {@code oldLetter} in the database. This is the message in the same
     * {@code processingGroup} and {@code sequence}, but with the next index.
     *
     * @param oldLetter The base letter to search from.
     * @return The next letter to process.
     */
    private DeadLetterEntry findNextDeadLetter(JpaDeadLetter<M> oldLetter) {
        try {
            return entityManager()
                    .createQuery(
                            "SELECT dl FROM DeadLetterEntry dl "
                                    + "where dl.processingGroup=:processingGroup "
                                    + "and dl.sequence=:sequence "
                                    + "and dl.index > :previousIndex "
                                    + "order by dl.index asc ",
                            DeadLetterEntry.class
                    )
                    .setParameter("processingGroup", processingGroup)
                    .setParameter("sequence", oldLetter.getSequence())
                    .setParameter("previousIndex", oldLetter.getIndex())
                    .getSingleResult();
        } catch (NoResultException exception) {
            return null;
        }
    }

    /**
     * Claims the dead provided {@link DeadLetter} in the database by setting the {@code processingStarted} property.
     * Will check whether it was claimed successfully, or throw a {@link DeadLetterClaimException}.
     */
    private void claimDeadLetter(JpaDeadLetter<M> deadLetter) {
        Instant processingStartedLimit = getProcessingStartedLimit();
        transactionManager.executeInTransaction(() -> {
            int updatedRows = entityManager().createQuery("update DeadLetterEntry dl set dl.processingStarted=:time "
                                                                  + "where dl.deadLetterId=:deadletterId "
                                                                  + "and (dl.processingStarted is null or dl.processingStarted < :processingStartedLimit)")
                                             .setParameter("deadletterId", deadLetter.getId())
                                             .setParameter("time", clock.instant())
                                             .setParameter("processingStartedLimit", processingStartedLimit)
                                             .executeUpdate();
            if (updatedRows == 0) {
                throw new DeadLetterClaimException(String.format(
                        "Dead letter [%s] was already claimed or processed by another node.",
                        deadLetter.getId()));
            }
        });
    }

    private Instant getProcessingStartedLimit() {
        return clock.instant().minus(claimDuration);
    }

    @Override
    public void clear() {
        transactionManager.executeInTransaction(() -> {
            entityManagerProvider.getEntityManager()
                                 .createQuery(
                                         "delete from DeadLetterEntry dl where dl.processingGroup=:processingGroup")
                                 .setParameter("processingGroup", processingGroup)
                                 .executeUpdate();
        });
    }

    /**
     * Checks whether the maximum amount of unique sequences within this processingGroup of the DLQ is reached. If there
     * already is a dead letter with this sequence present, it can be added to an already existing queue and the method
     * will return false.
     *
     * @param queueIdentifier The sequence to check for.
     * @return Whether the maximum amount of queues is reached.
     */
    private boolean maximumNumberOfQueuesReached(String queueIdentifier) {
        return getNumberInQueue(queueIdentifier) == 0 && getNumberOfQueues() >= maxSequences;
    }

    /**
     * Checks whether the maximum amount of dead letters within a sequence is reached.
     *
     * @param sequence The sequence to check for.
     * @return Whether the maximum amount of dead letters is reached.
     */
    private boolean maximumQueueSizeReached(String sequence) {
        return getNumberInQueue(sequence) >= maxSequenceSize;
    }

    /**
     * Counts the amount of messages for a given sequence.
     *
     * @param sequence The sequence to check for
     * @return The amount of dead letters for this sequence
     */
    private Long getNumberInQueue(String sequence) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        return entityManager.createQuery(
                                    "SELECT count(dl) FROM DeadLetterEntry dl "
                                            + "where dl.processingGroup=:processingGroup "
                                            + "and dl.sequence=:sequence",
                                    Long.class
                            )
                            .setParameter("processingGroup", processingGroup)
                            .setParameter("sequence", sequence)
                            .getSingleResult();
    }

    /**
     * Counts the amount of unique sequences within this DLQ.
     *
     * @return The amount of sequences
     */
    private Long getNumberOfQueues() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        return entityManager.createQuery(
                                    "SELECT count(distinct dl.sequence) FROM DeadLetterEntry dl "
                                            + "where dl.processingGroup=:processingGroup",
                                    Long.class
                            )
                            .setParameter("processingGroup", processingGroup)
                            .getSingleResult();
    }

    /**
     * Fetches the next maximum index for a sequence that should be used when inserting a new item into the databse for
     * this {@code sequence}.
     *
     * @param sequence The identifier of the queue to fetch the next index for.
     * @return The next sequence index.
     */
    private Long getNextIndexForSequence(String sequence) {
        Long maxIndex = getMaxIndexForSequence(sequence);
        if (maxIndex == null) {
            return 0L;
        }
        return maxIndex + 1;
    }

    /**
     * Fetches the current maximum index for a queue identifier. Messages which are enqueued next should have an index
     * higher than the one returned. If the query returns null it indicates that the queue is empty.
     *
     * @param sequence The identifier of the queue to check the index for.
     * @return The current maximum index, or null if not present.
     */
    private Long getMaxIndexForSequence(String sequence) {
        try {
            return entityManager().createQuery(
                                          "SELECT max(dl.index) FROM DeadLetterEntry dl where dl.processingGroup=:processingGroup and dl.sequence=:sequence",
                                          Long.class
                                  )
                                  .setParameter("sequence", sequence)
                                  .setParameter("processingGroup", processingGroup)
                                  .getSingleResult();
        } catch (NoResultException e) {
            // Expected, queue is empty. Return null.
            return null;
        }
    }

    /**
     * Converts the given sequence identifier to a String.
     */
    private String toIdentifier(Object sequenceIdentifier) {
        if (sequenceIdentifier instanceof String) {
            return (String) sequenceIdentifier;
        }
        return Integer.toString(sequenceIdentifier.hashCode());
    }


    /**
     * Builder class to instantiate an {@link JpaSequencedDeadLetterQueue}.
     * <p>
     * The maximum number of unique sequences defaults to {@code 1024}, the maximum amount of dead letters inside an
     * unique sequence to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000
     * milliseconds, and the {@link ScheduledExecutorService} defaults to a
     * {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)}, using an {@link AxonThreadFactory}.
     *
     * @param <T> The type of {@link Message} maintained in this {@link JpaSequencedDeadLetterQueue}.
     */
    public static class Builder<T extends EventMessage<?>> {

        private final List<DeadLetterJpaConverter<EventMessage<?>>> converters = new LinkedList<>();
        private String processingGroup = null;
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private EntityManagerProvider entityManagerProvider;
        private TransactionManager transactionManager;
        private Serializer serializer;
        private Duration claimDuration = Duration.ofSeconds(30);

        public Builder() {
            converters.add(new EventMessageDeadLetterJpaConverter());
        }

        /**
         * Sets the maximum number of unique sequences this {@link SequencedDeadLetterQueue} may contain.
         * <p>
         * The given {@code maxSequences} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxSequences The maximum amount of unique sequences for the queue under construction.
         * @return The current Builder, for fluent interfacing.
         */
        public JpaSequencedDeadLetterQueue.Builder<T> maxSequences(int maxSequences) {
            assertThat(maxSequences,
                       value -> value >= 128,
                       "The maximum number of sequences should be larger or equal to 128");
            this.maxSequences = maxSequences;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetter letters} per unique sequences this
         * {@link SequencedDeadLetterQueue} can store.
         * <p>
         * The given {@code maxSequenceSize} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxSequenceSize The maximum amount of {@link DeadLetter letters} per unique  sequence.
         * @return The current Builder, for fluent interfacing.
         */
        public JpaSequencedDeadLetterQueue.Builder<T> maxSequenceSize(int maxSequenceSize) {
            assertThat(maxSequenceSize,
                       value -> value >= 128,
                       "The maximum number of entries in a sequence should be larger or equal to 128");
            this.maxSequenceSize = maxSequenceSize;
            return this;
        }

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} used to access the underlying
         * database for this {@link JpaSequencedDeadLetterQueue} implementation.
         *
         * @param entityManagerProvider a {@link EntityManagerProvider} which provides the {@link EntityManager} used to
         *                              access the underlying database
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> entityManagerProvider(
                EntityManagerProvider entityManagerProvider) {
            assertNonNull(entityManagerProvider, "EntityManagerProvider may not be null");
            this.entityManagerProvider = entityManagerProvider;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transaction around fetching event data. Required by
         * certain databases for reading blob data.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transaction around fetching event data
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link Serializer} to deserialize the events, metadata and diagnostics of the {@link DeadLetter}
         * when storing it to a database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> eventSerializer(Serializer serializer) {
            assertNonNull(serializer, "The serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Removes all current converters currently configured, including the default
         * {@link EventMessageDeadLetterJpaConverter}.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> clearConverters() {
            this.converters.clear();
            return this;
        }

        /**
         * Adds a {@link DeadLetterJpaConverter} to the configuration, which is used to deserialize items entries from
         * the database.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> addConverter(DeadLetterJpaConverter<EventMessage<?>> converter) {
            this.converters.add(converter);
            return this;
        }

        /**
         * Sets the processing group, which is used for storing and quering which event processor the deadlettered item
         * belonged to.
         *
         * @param processingGroup The processing group of this {@link SequencedDeadLetterQueue}.
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> processingGroup(String processingGroup) {
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Sets the claim duration, which is the time a message gets locked when processing and waiting for it to
         * complete. Other invocations of the {@link #process(Predicate, Function)} method will be unable to process a
         * sequence while the claim is active. Its default is 30 seconds.
         * <p>
         * Claims are automatically released once the item is requeued, the claim time is a backup policy in case of
         * unforeseen trouble such as down database connections.
         *
         * @param claimDuration The longest claim duration allowed.
         * @return the current Builder instance, for fluent interfacing
         */
        public JpaSequencedDeadLetterQueue.Builder<T> claimDuration(Duration claimDuration) {
            this.claimDuration = claimDuration;
            return this;
        }

        /**
         * Initializes a {@link JpaSequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link JpaSequencedDeadLetterQueue} as specified through this Builder.
         */
        public JpaSequencedDeadLetterQueue<T> build() {
            return new JpaSequencedDeadLetterQueue<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            if (processingGroup == null) {
                throw new AxonConfigurationException(
                        "Must supply processingGroup when constructing a JpaSequencedDeadLetterQueue");
            }
            if (serializer == null) {
                logger.warn(
                        "The default XStreamSerializer is used for dead letters, whereas it is strongly recommended to "
                                + "configure the security context of the XStream instance."
                );
                serializer = XStreamSerializer.builder()
                                              .xStream(new XStream(new CompactDriver()))
                                              .build();
            }
        }
    }
}
