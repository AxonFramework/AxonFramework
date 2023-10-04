/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.deadletter.legacyjpa;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.common.legacyjpa.PagingJpaQueryIterable;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterEntry;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry;
import org.axonframework.eventhandling.deadletter.jpa.NoJpaConverterFoundException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueueOverflowException;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.NoSuchDeadLetterException;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import static org.axonframework.common.BuilderUtils.*;

/**
 * JPA-backed implementation of the {@link SequencedDeadLetterQueue}, used for storing dead letters containing
 * {@link EventMessage Eventmessages} durably as a {@link DeadLetterEntry}.
 * <p>
 * Keeps the insertion order intact by saving an incremented index within each unique sequence, backed by the
 * {@link DeadLetterEntry#getSequenceIndex()} property. Each sequence is uniquely identified by the sequence identifier,
 * stored in the {@link DeadLetterEntry#getSequenceIdentifier()} field.
 * <p>
 * When processing an item, single execution across all applications is guaranteed by setting the
 * {@link DeadLetterEntry#getProcessingStarted()} property, locking other processes out of the sequence for the
 * configured {@code claimDuration} (30 seconds by default).
 * <p>
 * The stored {@link DeadLetterEntry entries} are converted to a {@link JpaDeadLetter} when they need to be processed or
 * filtered. In order to restore the original {@link EventMessage} a matching {@link DeadLetterJpaConverter} is used.
 * The default supports all {@code EventMessage} implementations provided by the framework. If you have a custom
 * variant, you have to build your own.
 * <p>
 * {@link org.axonframework.serialization.upcasting.Upcaster upcasters} are not supported by this implementation, so
 * breaking changes for events messages stored in the queue should be avoided.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 * @deprecated in favor of using {@link org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue}
 * which moved to jakarta.
 */
@Deprecated
public class JpaSequencedDeadLetterQueue<M extends EventMessage<?>> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PROCESSING_GROUP_PARAM = "processingGroup";
    private static final String SEQUENCE_ID_PARAM = "sequenceIdentifier";

    private final String processingGroup;
    private final EntityManagerProvider entityManagerProvider;
    private final List<DeadLetterJpaConverter<EventMessage<?>>> converters;
    private final TransactionManager transactionManager;
    private final int maxSequences;
    private final int maxSequenceSize;
    private final int queryPageSize;
    private final Serializer eventSerializer;
    private final Serializer genericSerializer;
    private final Duration claimDuration;

    /**
     * Instantiate a JPA {@link SequencedDeadLetterQueue} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link JpaSequencedDeadLetterQueue} instance.
     */
    protected <T extends EventMessage<?>> JpaSequencedDeadLetterQueue(Builder<T> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
        this.entityManagerProvider = builder.entityManagerProvider;
        this.transactionManager = builder.transactionManager;
        this.eventSerializer = builder.eventSerializer;
        this.genericSerializer = builder.genericSerializer;
        this.converters = builder.converters;
        this.claimDuration = builder.claimDuration;
        this.queryPageSize = builder.queryPageSize;
    }

    /**
     * Creates a new builder, capable of building a {@link JpaSequencedDeadLetterQueue} according to the provided
     * configuration. Note that the {@link Builder#processingGroup(String)}, {@link Builder#transactionManager},
     * {@link Builder#serializer(Serializer)} and {@link Builder#entityManagerProvider} are mandatory for the queue to
     * be constructed.
     *
     * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this
     *            queue.
     * @return The builder
     */
    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public void enqueue(@Nonnull Object sequenceIdentifier, @Nonnull DeadLetter<? extends M> letter)
            throws DeadLetterQueueOverflowException {
        String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);
        if (isFull(stringSequenceIdentifier)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue [" + letter.message() + "] for identifier ["
                            + stringSequenceIdentifier + "] since the queue is full."
            );
        }

        Optional<Cause> optionalCause = letter.cause();
        if (optionalCause.isPresent()) {
            logger.info("Adding dead letter with message id [{}] because [{}].",
                        letter.message().getIdentifier(),
                        optionalCause.get());
        } else {
            logger.info(
                    "Adding dead letter with message id [{}] because the sequence identifier [{}] is already present.",
                    letter.message().getIdentifier(),
                    stringSequenceIdentifier);
        }

        DeadLetterEventEntry entry = converters
                .stream()
                .filter(c -> c.canConvert(letter.message()))
                .findFirst()
                .map(c -> c.convert(letter.message(), eventSerializer, genericSerializer))
                .orElseThrow(() -> new NoJpaConverterFoundException(
                        String.format("No converter found for message of type: [%s]",
                                      letter.message().getClass().getName()))
                );

        transactionManager.executeInTransaction(() -> {
            Long sequenceIndex = getNextIndexForSequence(stringSequenceIdentifier);
            DeadLetterEntry deadLetter = new DeadLetterEntry(processingGroup,
                                                             stringSequenceIdentifier,
                                                             sequenceIndex,
                                                             entry,
                                                             letter.enqueuedAt(),
                                                             letter.lastTouched(),
                                                             letter.cause().orElse(null),
                                                             letter.diagnostics(),
                                                             eventSerializer);
            logger.info("Storing DeadLetter (id: [{}]) for sequence [{}] with index [{}] in processing group [{}].",
                        deadLetter.getDeadLetterId(),
                        stringSequenceIdentifier,
                        sequenceIndex,
                        processingGroup);
            entityManager().persist(deadLetter);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void evict(DeadLetter<? extends M> letter) {
        if (!(letter instanceof JpaDeadLetter)) {
            throw new WrongDeadLetterTypeException(
                    String.format("Evict should be called with a JpaDeadLetter instance. Instead got: [%s]",
                                  letter.getClass().getName()));
        }
        JpaDeadLetter<M> jpaDeadLetter = (JpaDeadLetter<M>) letter;
        logger.info("Evicting JpaDeadLetter with id {} for processing group {} and sequence {}",
                    jpaDeadLetter.getId(),
                    processingGroup,
                    jpaDeadLetter.getSequenceIdentifier());

        transactionManager.executeInTransaction(
                () -> {
                    int deletedRows = entityManager().createQuery(
                                                             "delete from DeadLetterEntry dl where dl.deadLetterId=:deadLetterId")
                                                     .setParameter("deadLetterId", jpaDeadLetter.getId())
                                                     .executeUpdate();
                    if (deletedRows == 0) {
                        logger.info(
                                "JpaDeadLetter with id {} for processing group {} and sequence {} was already evicted",
                                jpaDeadLetter.getId(),
                                processingGroup,
                                jpaDeadLetter.getSequenceIdentifier());
                    }
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void requeue(@Nonnull DeadLetter<? extends M> letter,
                        @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater)
            throws NoSuchDeadLetterException {
        if (!(letter instanceof JpaDeadLetter)) {
            throw new WrongDeadLetterTypeException(String.format(
                    "Requeue should be called with a JpaDeadLetter instance. Instead got: [%s]",
                    letter.getClass().getName()));
        }
        EntityManager entityManager = entityManager();
        DeadLetter<? extends M> updatedLetter = letterUpdater.apply(letter).markTouched();
        String id = ((JpaDeadLetter<? extends M>) letter).getId();
        DeadLetterEntry letterEntity = entityManager.find(DeadLetterEntry.class, id);
        if (letterEntity == null) {
            throw new NoSuchDeadLetterException(String.format("Can not find dead letter with id [%s] to requeue.", id));
        }
        letterEntity.setDiagnostics(updatedLetter.diagnostics(), eventSerializer);
        letterEntity.setLastTouched(updatedLetter.lastTouched());
        letterEntity.setCause(updatedLetter.cause().orElse(null));
        letterEntity.clearProcessingStarted();

        logger.info("Requeueing dead letter with id [{}] with cause [{}]",
                    letterEntity.getDeadLetterId(),
                    updatedLetter.cause());
        entityManager.persist(letterEntity);
    }

    @Override
    public boolean contains(@Nonnull Object sequenceIdentifier) {
        String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);
        return sequenceSize(stringSequenceIdentifier) > 0;
    }

    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);

        return new PagingJpaQueryIterable<>(
                queryPageSize,
                transactionManager,
                () -> entityManagerProvider
                        .getEntityManager()
                        .createQuery(
                                "select dl from DeadLetterEntry dl "
                                        + "where dl.processingGroup=:processingGroup "
                                        + "and dl.sequenceIdentifier=:identifier "
                                        + "order by dl.sequenceIndex",
                                DeadLetterEntry.class
                        )
                        .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                        .setParameter("identifier", stringSequenceIdentifier),
                this::toLetter
        );
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        List<String> sequenceIdentifiers = entityManagerProvider
                .getEntityManager()
                .createQuery(
                        "select dl.sequenceIdentifier from DeadLetterEntry dl "
                                + "where dl.processingGroup=:processingGroup "
                                + "and dl.sequenceIndex = (select min(dl2.sequenceIndex) from DeadLetterEntry dl2 where dl2.processingGroup=dl.processingGroup and dl2.sequenceIdentifier=dl.sequenceIdentifier) "
                                + "order by dl.lastTouched asc ",
                        String.class)
                .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                .getResultList();


        return () -> {
            Iterator<String> sequenceIterator = sequenceIdentifiers.iterator();
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
        DeadLetterJpaConverter<M> converter = (DeadLetterJpaConverter<M>) converters
                .stream()
                .filter(c -> c.canConvert(entry.getMessage()))
                .findFirst()
                .orElseThrow(() -> new NoJpaConverterFoundException(String.format(
                        "No converter found to convert message of class [%s].",
                        entry.getMessage().getMessageType())));
        MetaData deserializedDiagnostics = eventSerializer.deserialize(entry.getDiagnostics());
        return new JpaDeadLetter<>(entry,
                                   deserializedDiagnostics,
                                   converter.convert(entry.getMessage(), eventSerializer, genericSerializer));
    }

    @Override
    public boolean isFull(@Nonnull Object sequenceIdentifier) {
        String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);
        long numberInSequence = sequenceSize(stringSequenceIdentifier);
        return numberInSequence > 0 ? numberInSequence >= maxSequenceSize : amountOfSequences() >= maxSequences;
    }

    @Override
    public boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                           @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {

        JpaDeadLetter<M> claimedLetter = null;
        Iterator<JpaDeadLetter<M>> iterator = findFirstLetterOfEachAvailableSequence(10);
        while (iterator.hasNext() && claimedLetter == null) {
            JpaDeadLetter<M> next = iterator.next();
            if (sequenceFilter.test(next) && claimDeadLetter(next)) {
                claimedLetter = next;
            }
        }

        if (claimedLetter != null) {
            return processLetterAndFollowing(claimedLetter, processingTask);
        }
        logger.info("No claimable and/or matching dead letters found to process.");
        return false;
    }

    @Override
    public boolean process(@Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        Iterator<JpaDeadLetter<M>> iterator = findFirstLetterOfEachAvailableSequence(1);
        if (iterator.hasNext()) {
            JpaDeadLetter<M> deadLetter = iterator.next();
            claimDeadLetter(deadLetter);
            return processLetterAndFollowing(deadLetter, processingTask);
        }
        return false;
    }

    /**
     * Processes the given {@code firstDeadLetter} using the provided {@code processingTask}. When successful (the
     * message is evicted) it will automatically process all messages in the same
     * {@link JpaDeadLetter#getSequenceIdentifier()}, evicting messages that succeed and stopping when the first one
     * fails (and is requeued).
     * <p>
     * Will claim the next letter in the same sequence before removing the old one to prevent concurrency issues. Note
     * that we do not use paging on the results here, since messages can be added to the end of the sequence before
     * processing ends, and deletes would throw the ordering off.
     *
     * @param firstDeadLetter The dead letter to start processing.
     * @param processingTask  The task to use to process the dead letter, providing a dicision afterwards.
     * @return Whether processing all letters in this sequence was successful.
     */
    private boolean processLetterAndFollowing(JpaDeadLetter<M> firstDeadLetter,
                                              Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        JpaDeadLetter<M> deadLetter = firstDeadLetter;
        while (deadLetter != null) {
            logger.info("Processing dead letter with id [{}]", deadLetter.getId());
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
                        l -> decision.withDiagnostics(l)
                                     .withCause(decision.enqueueCause().orElse(null))
                );
                return false;
            }
        }

        return true;
    }

    /**
     * Fetches the first letter for each sequence in the provided {@code processingGroup}. This is the message with the
     * lowest index.
     * <p>
     * This fetches only letters which are available, having a {@code processingStarted} of null or longer ago than the
     * configured {@code claimDuration}. The messages are lazily reconstructed by the {@link PagingJpaQueryIterable}.
     *
     * @param pageSize The size of the paging on the query. Lower is faster, but with many results a larger page is
     *                 better.
     * @return A list of first letters of each sequence.
     */
    private Iterator<JpaDeadLetter<M>> findFirstLetterOfEachAvailableSequence(int pageSize) {
        return new PagingJpaQueryIterable<>(
                pageSize,
                transactionManager,
                () -> entityManager()
                        .createQuery(
                                "select dl from DeadLetterEntry dl "
                                        + "where dl.processingGroup=:processingGroup "
                                        + "and dl.sequenceIndex = (select min(dl2.sequenceIndex) from DeadLetterEntry dl2 where dl2.processingGroup=dl.processingGroup and dl2.sequenceIdentifier=dl.sequenceIdentifier) "
                                        + "and (dl.processingStarted is null or dl.processingStarted < :processingStartedLimit) "
                                        + "order by dl.lastTouched asc ",
                                DeadLetterEntry.class
                        )
                        .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                        .setParameter("processingStartedLimit", getProcessingStartedLimit()),
                this::toLetter)
                .iterator();
    }

    /**
     * Finds the next dead letter after the provided {@code oldLetter} in the database. This is the message in the same
     * {@code processingGroup} and {@code sequence}, but with the next index.
     *
     * @param oldLetter The base letter to search from.
     * @return The next letter to process.
     */
    private DeadLetterEntry findNextDeadLetter(JpaDeadLetter<M> oldLetter) {
        return transactionManager.fetchInTransaction(() -> {
            try {
                return entityManager()
                        .createQuery(
                                "select dl from DeadLetterEntry dl "
                                        + "where dl.processingGroup=:processingGroup "
                                        + "and dl.sequenceIdentifier=:sequenceIdentifier "
                                        + "and dl.sequenceIndex > :previousIndex "
                                        + "order by dl.sequenceIndex asc ",
                                DeadLetterEntry.class
                        )
                        .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                        .setParameter(SEQUENCE_ID_PARAM, oldLetter.getSequenceIdentifier())
                        .setParameter("previousIndex", oldLetter.getIndex())
                        .setMaxResults(1)
                        .getSingleResult();
            } catch (NoResultException exception) {
                return null;
            }
        });
    }

    /**
     * Claims the provided {@link DeadLetter} in the database by setting the {@code processingStarted} property. Will
     * check whether it was claimed successfully and return an appropriate boolean result.
     *
     * @return Whether the letter was successfully claimed or not.
     */
    private boolean claimDeadLetter(JpaDeadLetter<M> deadLetter) {
        Instant processingStartedLimit = getProcessingStartedLimit();
        return transactionManager.fetchInTransaction(() -> {
            int updatedRows = entityManager().createQuery("update DeadLetterEntry dl set dl.processingStarted=:time "
                                                                  + "where dl.deadLetterId=:deadletterId "
                                                                  + "and (dl.processingStarted is null or dl.processingStarted < :processingStartedLimit)")
                                             .setParameter("deadletterId", deadLetter.getId())
                                             .setParameter("time", GenericDeadLetter.clock.instant())
                                             .setParameter("processingStartedLimit", processingStartedLimit)
                                             .executeUpdate();
            if (updatedRows > 0) {
                logger.info("Claimed dead letter with id [{}] to process.", deadLetter.getId());
                return true;
            }
            logger.info("Failed to claim dead letter with id [{}].", deadLetter.getId());
            return false;
        });
    }

    /**
     * Determines the time the {@link DeadLetterEntry#getProcessingStarted()} needs to at least have to stay claimed.
     * This is based on the configured {@link #claimDuration}.
     */
    private Instant getProcessingStartedLimit() {
        return GenericDeadLetter.clock.instant().minus(claimDuration);
    }

    @Override
    public void clear() {
        transactionManager.executeInTransaction(
                () -> entityManagerProvider.getEntityManager()
                                           .createQuery(
                                                   "delete from DeadLetterEntry dl where dl.processingGroup=:processingGroup")
                                           .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                           .executeUpdate());
    }

    @Override
    public long sequenceSize(@Nonnull Object sequenceIdentifier) {
        return transactionManager.fetchInTransaction(
                () -> entityManagerProvider.getEntityManager().createQuery(
                                                   "select count(dl) from DeadLetterEntry dl "
                                                           + "where dl.processingGroup=:processingGroup "
                                                           + "and dl.sequenceIdentifier=:sequenceIdentifier",
                                                   Long.class
                                           )
                                           .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                           .setParameter(SEQUENCE_ID_PARAM, sequenceIdentifier)
                                           .getSingleResult());
    }

    @Override
    public long size() {
        return transactionManager.fetchInTransaction(
                () -> entityManagerProvider.getEntityManager().createQuery(
                                                   "select count(dl) from DeadLetterEntry dl "
                                                           + "where dl.processingGroup=:processingGroup",
                                                   Long.class
                                           )
                                           .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                           .getSingleResult());
    }

    @Override
    public long amountOfSequences() {
        return transactionManager.fetchInTransaction(
                () -> entityManagerProvider.getEntityManager().createQuery(
                                                   "select count(distinct dl.sequenceIdentifier) from DeadLetterEntry dl "
                                                           + "where dl.processingGroup=:processingGroup",
                                                   Long.class
                                           )
                                           .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                           .getSingleResult());
    }

    /**
     * Fetches the next maximum index for a sequence that should be used when inserting a new item into the database for
     * this {@code sequence}.
     *
     * @param sequenceIdentifier The identifier of the sequence to fetch the next index for.
     * @return The next sequence index.
     */
    private Long getNextIndexForSequence(String sequenceIdentifier) {
        Long maxIndex = getMaxIndexForSequence(sequenceIdentifier);
        if (maxIndex == null) {
            return 0L;
        }
        return maxIndex + 1;
    }

    /**
     * Fetches the current maximum index for a queue identifier. Messages which are enqueued next should have an index
     * higher than the one returned. If the query returns null it indicates that the queue is empty.
     *
     * @param sequenceIdentifier The identifier of the sequence to check the index for.
     * @return The current maximum index, or null if not present.
     */
    private Long getMaxIndexForSequence(String sequenceIdentifier) {
        return transactionManager.fetchInTransaction(() -> {
            try {
                return entityManager().createQuery(
                                              "select max(dl.sequenceIndex) from DeadLetterEntry dl where dl.processingGroup=:processingGroup and dl.sequenceIdentifier=:sequenceIdentifier",
                                              Long.class
                                      )
                                      .setParameter(SEQUENCE_ID_PARAM, sequenceIdentifier)
                                      .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                      .getSingleResult();
            } catch (NoResultException e) {
                // Expected, queue is empty. Return null.
                return null;
            }
        });
    }

    /**
     * Converts the given sequence identifier to a String.
     */
    private String toStringSequenceIdentifier(Object sequenceIdentifier) {
        if (sequenceIdentifier instanceof String) {
            return (String) sequenceIdentifier;
        }
        return Integer.toString(sequenceIdentifier.hashCode());
    }

    private EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }


    /**
     * Builder class to instantiate an {@link JpaSequencedDeadLetterQueue}.
     * <p>
     * The maximum number of unique sequences defaults to {@code 1024}, the maximum amount of dead letters inside a
     * unique sequence to {@code 1024}, the claim duration defaults to {@code 30} seconds, the query page size defaults
     * to {@code 100}, and the converters default to containing a single {@link EventMessageDeadLetterJpaConverter}.
     * <p>
     * If you have custom {@link EventMessage} to use with this queue, replace the current (or add a second) converter.
     * <p>
     * The {@code processingGroup}, {@link EntityManagerProvider}, {@link TransactionManager} and {@link Serializer}
     * have to be configured for the {@link JpaSequencedDeadLetterQueue} to be constructed.
     *
     * @param <T> The type of {@link Message} maintained in this {@link JpaSequencedDeadLetterQueue}.
     */
    public static class Builder<T extends EventMessage<?>> {

        private final List<DeadLetterJpaConverter<EventMessage<?>>> converters = new LinkedList<>();
        private String processingGroup = null;
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private int queryPageSize = 100;
        private EntityManagerProvider entityManagerProvider;
        private TransactionManager transactionManager;
        private Serializer eventSerializer;
        private Serializer genericSerializer;
        private Duration claimDuration = Duration.ofSeconds(30);

        public Builder() {
            converters.add(new EventMessageDeadLetterJpaConverter());
        }

        /**
         * Sets the processing group, which is used for storing and querying which event processor the deadlettered item
         * belonged to.
         *
         * @param processingGroup The processing group of this {@link SequencedDeadLetterQueue}.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> processingGroup(String processingGroup) {
            assertNonEmpty(processingGroup, "Can not set processingGroup to an empty String.");
            this.processingGroup = processingGroup;
            return this;
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
        public Builder<T> maxSequences(int maxSequences) {
            assertStrictPositive(maxSequences,
                                 "The maximum number of sequences should be larger or equal to 0");
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
        public Builder<T> maxSequenceSize(int maxSequenceSize) {
            assertStrictPositive(maxSequenceSize,
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
        public Builder<T> entityManagerProvider(
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
        public Builder<T> transactionManager(TransactionManager transactionManager) {
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
        public Builder<T> serializer(Serializer serializer) {
            assertNonNull(serializer, "The serializer may not be null");
            this.eventSerializer = serializer;
            this.genericSerializer = serializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the event payload, event metadata, and diagnostics of the
         * {@link DeadLetter} when storing it to the database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> eventSerializer(Serializer serializer) {
            assertNonNull(serializer, "The eventSerializer may not be null");
            this.eventSerializer = serializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the tracking token of the event in the {@link DeadLetter} when
         * storing it to the database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> genericSerializer(Serializer serializer) {
            assertNonNull(serializer, "The genericSerializer may not be null");
            this.genericSerializer = serializer;
            return this;
        }

        /**
         * Removes all current converters currently configured, including the default
         * {@link EventMessageDeadLetterJpaConverter}.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> clearConverters() {
            this.converters.clear();
            return this;
        }

        /**
         * Adds a {@link DeadLetterJpaConverter} to the configuration, which is used to deserialize dead-letter entries
         * from the database.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> addConverter(DeadLetterJpaConverter<EventMessage<?>> converter) {
            assertNonNull(converter, "Can not add a null DeadLetterJpaConverter.");
            this.converters.add(converter);
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
        public Builder<T> claimDuration(Duration claimDuration) {
            assertNonNull(claimDuration, "Claim duration can not be set to null.");
            this.claimDuration = claimDuration;
            return this;
        }

        /**
         * Modifies the page size used when retrieving a sequence of dead letters. Defaults to {@code 100} items in a
         * page.
         *
         * @param queryPageSize The page size
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> queryPageSize(int queryPageSize) {
            assertStrictPositive(queryPageSize, "The query page size must be at least 1.");
            this.queryPageSize = queryPageSize;
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
            assertNonEmpty(processingGroup,
                           "Must supply processingGroup when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(transactionManager,
                          "Must supply a TransactionManager when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(entityManagerProvider,
                          "Must supply a EntityManagerProvider when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(eventSerializer,
                          "Must supply an eventSerializer when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(genericSerializer,
                          "Must supply an genericSerializer when constructing a JpaSequencedDeadLetterQueue");
        }
    }
}
