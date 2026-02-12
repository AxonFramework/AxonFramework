/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueueOverflowException;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.NoSuchDeadLetterException;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import jakarta.annotation.Nonnull;

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
 * filtered. In order to restore the original {@link EventMessage} the configured {@link DeadLetterJpaConverter} is
 * used. The default {@link EventMessageDeadLetterJpaConverter} supports all {@code EventMessage} implementations
 * provided by the framework.
 * <p>
 * {@link org.axonframework.conversion.upcasting.Upcaster upcasters} are not supported by this implementation, so
 * breaking changes for events messages stored in the queue should be avoided.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class JpaSequencedDeadLetterQueue<M extends EventMessage> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PROCESSING_GROUP_PARAM = "processingGroup";
    private static final String SEQUENCE_ID_PARAM = "sequenceIdentifier";

    private final String processingGroup;
    private final TransactionalExecutorProvider<EntityManager> transactionalExecutorProvider;
    private final DeadLetterJpaConverter<EventMessage> converter;
    private final int maxSequences;
    private final int maxSequenceSize;
    private final int queryPageSize;
    private final EventConverter eventConverter;
    private final Converter genericConverter;
    private final Duration claimDuration;
    private final Set<Context.ResourceKey<?>> serializableResources;

    /**
     * Instantiate a JPA {@link SequencedDeadLetterQueue} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link JpaSequencedDeadLetterQueue} instance.
     */
    protected <T extends EventMessage> JpaSequencedDeadLetterQueue(Builder<T> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
        this.transactionalExecutorProvider = builder.transactionalExecutorProvider;
        this.eventConverter = builder.eventConverter;
        this.genericConverter = builder.genericConverter;
        this.converter = builder.converter;
        this.claimDuration = builder.claimDuration;
        this.queryPageSize = builder.queryPageSize;
        this.serializableResources = builder.serializableResources;
    }

    /**
     * Creates a new builder, capable of building a {@link JpaSequencedDeadLetterQueue} according to the provided
     * configuration. Note that the {@link Builder#processingGroup(String)},
     * {@link Builder#transactionalExecutorProvider(TransactionalExecutorProvider)},
     * {@link Builder#eventConverter(EventConverter)}, and {@link Builder#genericConverter(Converter)} are mandatory for
     * the queue to be constructed.
     *
     * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this
     *            queue.
     * @return The builder
     */
    public static <M extends EventMessage> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public CompletableFuture<Void> enqueue(@Nonnull Object sequenceIdentifier,
                                           @Nonnull DeadLetter<? extends M> letter,
                                           @Nullable ProcessingContext context) {
        try {
            String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);
            if (Boolean.TRUE.equals(FutureUtils.joinAndUnwrap(isFull(stringSequenceIdentifier, context)))) {
                throw new DeadLetterQueueOverflowException(
                        "No room left to enqueue [" + letter.message() + "] for identifier ["
                                + stringSequenceIdentifier + "] since the queue is full."
                );
            }

            Optional<Cause> optionalCause = letter.cause();
            if (optionalCause.isPresent()) {
                logger.info("Adding dead letter with message id [{}] because [{}].",
                            letter.message().identifier(),
                            optionalCause.get().type());
            } else {
                logger.info(
                        "Adding dead letter with message id [{}] because the sequence identifier [{}] is already present.",
                        letter.message().identifier(),
                        stringSequenceIdentifier);
            }

            Context serializationContext = snapshotContext(context != null ? context : letter.context());
            DeadLetterEventEntry entry = converter.convert(
                    letter.message(), serializationContext, eventConverter, genericConverter
            );

            FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(context).accept(em -> {
                        Long sequenceIndex = nextIndexForSequence(em, stringSequenceIdentifier);
                        DeadLetterEntry deadLetter = new DeadLetterEntry(processingGroup,
                                                                         stringSequenceIdentifier,
                                                                         sequenceIndex,
                                                                         entry,
                                                                         letter.enqueuedAt(),
                                                                         letter.lastTouched(),
                                                                         letter.cause().orElse(null),
                                                                         letter.diagnostics(),
                                                                         genericConverter);
                        logger.info(
                                "Storing DeadLetter (id: [{}]) for sequence [{}] with index [{}] in processing group [{}].",
                                deadLetter.getDeadLetterId(),
                                stringSequenceIdentifier,
                                sequenceIndex,
                                processingGroup);
                        em.persist(deadLetter);
                    })
            );
            return FutureUtils.emptyCompletedFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Context snapshotContext(@Nullable Context context) {
        if (context == null || serializableResources.isEmpty()) {
            return Context.empty();
        }
        Context result = Context.empty();
        for (Context.ResourceKey<?> key : serializableResources) {
            Object resource = context.getResource(key);
            if (resource != null) {
                //noinspection unchecked
                result = result.withResource((Context.ResourceKey<Object>) key, resource);
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    @NonNull
    public CompletableFuture<Void> evict(@Nonnull DeadLetter<? extends M> letter, @Nullable ProcessingContext context) {
        try {
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

            FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(context).accept(em -> {
                        int deletedRows = em.createQuery(
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
                    })
            );
            return FutureUtils.emptyCompletedFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Void> requeue(@Nonnull DeadLetter<? extends M> letter,
                                           @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater,
                                           @Nullable ProcessingContext context) {
        try {
            if (!(letter instanceof JpaDeadLetter)) {
                throw new WrongDeadLetterTypeException(String.format(
                        "Requeue should be called with a JpaDeadLetter instance. Instead got: [%s]",
                        letter.getClass().getName()));
            }
            DeadLetter<? extends M> updatedLetter = letterUpdater.apply(letter).markTouched();
            String id = ((JpaDeadLetter<? extends M>) letter).getId();

            FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(context).accept(em -> {
                        DeadLetterEntry letterEntity = em.find(DeadLetterEntry.class, id);
                        if (letterEntity == null) {
                            throw new NoSuchDeadLetterException(
                                    String.format("Can not find dead letter with id [%s] to requeue.", id));
                        }
                        letterEntity.setDiagnostics(updatedLetter.diagnostics(), genericConverter);
                        letterEntity.setLastTouched(updatedLetter.lastTouched());
                        letterEntity.setCause(updatedLetter.cause().orElse(null));
                        letterEntity.clearProcessingStarted();

                        logger.info("Requeueing dead letter with id [{}] with cause [{}]",
                                    letterEntity.getDeadLetterId(),
                                    updatedLetter.cause());
                        em.persist(letterEntity);
                    })
            );
            return FutureUtils.emptyCompletedFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Boolean> contains(@Nonnull Object sequenceIdentifier, @Nullable ProcessingContext context) {
        try {
            String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);
            boolean result = FutureUtils.joinAndUnwrap(sequenceSize(stringSequenceIdentifier, context)) > 0;
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Iterable<DeadLetter<? extends M>>> deadLetterSequence(
            @Nonnull Object sequenceIdentifier,
            @Nullable ProcessingContext context
    ) {
        try {
            String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);

            Iterable<DeadLetter<? extends M>> result = new PagingJpaQueryIterable<>(
                    queryPageSize,
                    entityManagerExecutor(null),
                    em -> em.createQuery(
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
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Iterable<Iterable<DeadLetter<? extends M>>>> deadLetters(
            @Nullable ProcessingContext context
    ) {
        try {
            List<String> sequenceIdentifiers = FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(null)
                            .apply(em ->
                                           em.createQuery(
                                                     "select dl.sequenceIdentifier from DeadLetterEntry dl "
                                                             + "where dl.processingGroup=:processingGroup "
                                                             + "and dl.sequenceIndex = (select min(dl2.sequenceIndex) from DeadLetterEntry dl2 where dl2.processingGroup=dl.processingGroup and dl2.sequenceIdentifier=dl.sequenceIdentifier) "
                                                             + "order by dl.lastTouched asc ",
                                                     String.class)
                                             .setParameter(
                                                     PROCESSING_GROUP_PARAM,
                                                     processingGroup)
                                             .getResultList()
                            )
            );

            Iterable<Iterable<DeadLetter<? extends M>>> result = () -> {
                Iterator<String> sequenceIterator = sequenceIdentifiers.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return sequenceIterator.hasNext();
                    }

                    @Override
                    public Iterable<DeadLetter<? extends M>> next() {
                        String next = sequenceIterator.next();
                        return FutureUtils.joinAndUnwrap(deadLetterSequence(next, context));
                    }
                };
            };
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Converts a {@link DeadLetterEntry} from the database into a {@link JpaDeadLetter}, using the configured
     * {@link DeadLetterJpaConverter DeadLetterJpaConverters} to restore the original message from it.
     * <p>
     * The converter returns a {@link MessageStream.Entry} containing both the message and its associated context. The
     * context includes restored resources that were stored when the dead letter was enqueued.
     *
     * @param entry The entry to convert.
     * @return The {@link DeadLetter} result.
     */
    @SuppressWarnings("unchecked")
    private JpaDeadLetter<M> toLetter(DeadLetterEntry entry) {
        @SuppressWarnings("unchecked")
        Map<String, String> diagnosticsMap = genericConverter.convert(entry.getDiagnostics(), Map.class);
        Metadata deserializedDiagnostics = Metadata.from(diagnosticsMap);
        MessageStream.Entry<M> messageEntry =
                ((DeadLetterJpaConverter<M>) converter).convert(entry.getMessage(), eventConverter, genericConverter);
        return new JpaDeadLetter<>(entry, deserializedDiagnostics, messageEntry.message(), messageEntry);
    }

    @Override
    @NonNull
    public CompletableFuture<Boolean> isFull(@Nonnull Object sequenceIdentifier, @Nullable ProcessingContext context) {
        try {
            String stringSequenceIdentifier = toStringSequenceIdentifier(sequenceIdentifier);
            long numberInSequence = FutureUtils.joinAndUnwrap(sequenceSize(stringSequenceIdentifier, context));
            boolean result = numberInSequence > 0
                    ? numberInSequence >= maxSequenceSize
                    : FutureUtils.joinAndUnwrap(amountOfSequences(context)) >= maxSequences;
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Boolean> process(
            @Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
            @Nonnull Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> processingTask,
            @Nullable ProcessingContext context
    ) {
        try {
            Function<DeadLetter<? extends M>, EnqueueDecision<M>> syncTask =
                    letter -> FutureUtils.joinAndUnwrap(processingTask.apply(letter));
            boolean result = processSync(sequenceFilter, syncTask, context);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean processSync(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                                @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask,
                                @Nullable ProcessingContext context) {

        JpaDeadLetter<M> claimedLetter = null;
        Iterator<JpaDeadLetter<M>> iterator = findFirstLetterOfEachAvailableSequence(10);
        while (iterator.hasNext() && claimedLetter == null) {
            JpaDeadLetter<M> next = iterator.next();
            if (sequenceFilter.test(next) && claimDeadLetter(next)) {
                claimedLetter = next;
            }
        }

        if (claimedLetter != null) {
            return processLetterAndFollowing(claimedLetter, processingTask, context);
        }
        logger.info("No claimable and/or matching dead letters found to process.");
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
     * @param processingTask  The task to use to process the dead letter, providing a decision afterwards.
     * @return Whether processing all letters in this sequence was successful.
     */
    private boolean processLetterAndFollowing(JpaDeadLetter<M> firstDeadLetter,
                                              Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask,
                                              ProcessingContext context) {
        JpaDeadLetter<M> deadLetter = firstDeadLetter;
        while (deadLetter != null) {
            logger.info("Processing dead letter with id [{}] at index [{}]", deadLetter.getId(), deadLetter.getIndex());
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
                FutureUtils.joinAndUnwrap(evict(oldLetter, context));
            } else {
                FutureUtils.joinAndUnwrap(
                        requeue(deadLetter,
                                l -> decision.withDiagnostics(l)
                                             .withCause(decision.enqueueCause().orElse(null)),
                                context
                        )
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
                entityManagerExecutor(null),
                em -> em.createQuery(
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
        return FutureUtils.joinAndUnwrap(
                entityManagerExecutor(null).apply(em -> {
                    try {
                        return em.createQuery(
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
                })
        );
    }

    /**
     * Claims the provided {@link DeadLetter} in the database by setting the {@code processingStarted} property. Will
     * check whether it was claimed successfully and return an appropriate boolean result.
     *
     * @return Whether the letter was successfully claimed or not.
     */
    private boolean claimDeadLetter(JpaDeadLetter<M> deadLetter) {
        Instant processingStartedLimit = getProcessingStartedLimit();
        //noinspection DataFlowIssue
        return FutureUtils.joinAndUnwrap(
                entityManagerExecutor(null).apply(em -> {
                    int updatedRows = em.createQuery("update DeadLetterEntry dl set dl.processingStarted=:time "
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
                })
        );
    }

    /**
     * Determines the time the {@link DeadLetterEntry#getProcessingStarted()} needs to at least have to stay claimed.
     * This is based on the configured {@link #claimDuration}.
     */
    private Instant getProcessingStartedLimit() {
        return GenericDeadLetter.clock.instant().minus(claimDuration);
    }

    @Override
    @NonNull
    public CompletableFuture<Void> clear(@Nullable ProcessingContext context) {
        try {
            FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(context)
                            .accept(em -> em.createQuery(
                                                    "delete from DeadLetterEntry dl where dl.processingGroup=:processingGroup")
                                            .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                            .executeUpdate()
                            )
            );
            return FutureUtils.emptyCompletedFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Long> sequenceSize(@Nonnull Object sequenceIdentifier,
                                                @Nullable ProcessingContext context) {
        try {
            //noinspection DataFlowIssue
            long result = FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(null)
                            .apply(em ->
                                           em.createQuery(
                                                     "select count(dl) from DeadLetterEntry dl "
                                                             + "where dl.processingGroup=:processingGroup "
                                                             + "and dl.sequenceIdentifier=:sequenceIdentifier",
                                                     Long.class
                                             )
                                             .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                             .setParameter(SEQUENCE_ID_PARAM, sequenceIdentifier)
                                             .getSingleResult()
                            )
            );
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Long> size(@Nullable ProcessingContext context) {
        try {
            //noinspection DataFlowIssue
            long result = FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(null)
                            .apply(em ->
                                           em.createQuery(
                                                     "select count(dl) from DeadLetterEntry dl "
                                                             + "where dl.processingGroup=:processingGroup",
                                                     Long.class
                                             )
                                             .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                             .getSingleResult()
                            )
            );
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @NonNull
    public CompletableFuture<Long> amountOfSequences(@Nullable ProcessingContext context) {
        try {
            //noinspection DataFlowIssue
            long result = FutureUtils.joinAndUnwrap(
                    entityManagerExecutor(null)
                            .apply(em ->
                                           em.createQuery(
                                                     "select count(distinct dl.sequenceIdentifier) from DeadLetterEntry dl "
                                                             + "where dl.processingGroup=:processingGroup",
                                                     Long.class
                                             )
                                             .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                                             .getSingleResult()
                            )
            );
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fetches the next index for a sequence that should be used when inserting a new item into the database for this
     * {@code sequence}. Must be called within an active {@link EntityManager} context.
     *
     * @param em                 The {@link EntityManager} to use for querying.
     * @param sequenceIdentifier The identifier of the sequence to fetch the next index for.
     * @return The next sequence index.
     */
    private Long nextIndexForSequence(EntityManager em, String sequenceIdentifier) {
        try {
            Long maxIndex = em.createQuery(
                                      "select max(dl.sequenceIndex) from DeadLetterEntry dl where dl.processingGroup=:processingGroup and dl.sequenceIdentifier=:sequenceIdentifier",
                                      Long.class
                              )
                              .setParameter(SEQUENCE_ID_PARAM, sequenceIdentifier)
                              .setParameter(PROCESSING_GROUP_PARAM, processingGroup)
                              .getSingleResult();
            return maxIndex == null ? 0L : maxIndex + 1;
        } catch (NoResultException e) {
            return 0L;
        }
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

    private TransactionalExecutor<EntityManager> entityManagerExecutor(@Nullable ProcessingContext processingContext) {
        return transactionalExecutorProvider.getTransactionalExecutor(processingContext);
    }


    /**
     * Builder class to instantiate an {@link JpaSequencedDeadLetterQueue}.
     * <p>
     * The maximum number of unique sequences defaults to {@code 1024}, the maximum amount of dead letters inside a
     * unique sequence to {@code 1024}, the claim duration defaults to {@code 30} seconds, the query page size defaults
     * to {@code 100}, and the converter defaults to {@link EventMessageDeadLetterJpaConverter}.
     * <p>
     * The {@code processingGroup}, {@link TransactionalExecutorProvider}, {@link EventConverter eventConverter}, and
     * {@link Converter genericConverter} have to be configured for the {@link JpaSequencedDeadLetterQueue} to be
     * constructed.
     *
     * @param <T> The type of {@link Message} maintained in this {@link JpaSequencedDeadLetterQueue}.
     */
    public static class Builder<T extends EventMessage> {

        private String processingGroup = null;
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private int queryPageSize = 100;
        private TransactionalExecutorProvider<EntityManager> transactionalExecutorProvider;
        private EventConverter eventConverter;
        private Converter genericConverter;
        private DeadLetterJpaConverter<EventMessage> converter = new EventMessageDeadLetterJpaConverter();
        private Duration claimDuration = Duration.ofSeconds(30);
        private Set<Context.ResourceKey<?>> serializableResources = Set.of();

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
         * Sets the {@link TransactionalExecutorProvider} which provides the {@link TransactionalExecutor} used to
         * execute operations against the underlying database for this {@link JpaSequencedDeadLetterQueue}
         * implementation.
         *
         * @param transactionalExecutorProvider A {@link TransactionalExecutorProvider} providing
         *                                      {@link TransactionalExecutor TransactionalExecutors} used to access the
         *                                      underlying database.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> transactionalExecutorProvider(
                TransactionalExecutorProvider<EntityManager> transactionalExecutorProvider) {
            assertNonNull(transactionalExecutorProvider, "TransactionalExecutorProvider may not be null");
            this.transactionalExecutorProvider = transactionalExecutorProvider;
            return this;
        }

        /**
         * Sets the {@link EventConverter} to convert the event payload and metadata of the {@link DeadLetter} when
         * storing it to and retrieving it from the database.
         *
         * @param eventConverter The event converter to use for payload and metadata conversion.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> eventConverter(EventConverter eventConverter) {
            assertNonNull(eventConverter, "The eventConverter may not be null");
            this.eventConverter = eventConverter;
            return this;
        }

        /**
         * Sets the {@link Converter} to convert configured context resources and diagnostics of the {@link DeadLetter}
         * when storing it to and retrieving it from the database.
         *
         * @param genericConverter The converter to use for context resources and diagnostics conversion.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> genericConverter(Converter genericConverter) {
            assertNonNull(genericConverter, "The genericConverter may not be null");
            this.genericConverter = genericConverter;
            return this;
        }

        /**
         * Sets the {@link DeadLetterJpaConverter} used to convert {@link EventMessage EventMessages} to and from
         * {@link DeadLetterEventEntry DeadLetterEventEntries}. Defaults to {@link EventMessageDeadLetterJpaConverter}.
         *
         * @param converter The converter to use.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> converter(DeadLetterJpaConverter<EventMessage> converter) {
            assertNonNull(converter, "The DeadLetterJpaConverter may not be null.");
            this.converter = converter;
            return this;
        }

        /**
         * Sets the {@link Context.ResourceKey ResourceKeys} for which resources should be snapshotted from the
         * {@link ProcessingContext} when a dead letter is created.
         *
         * @param serializableResources The resource keys to snapshot.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> serializableResources(Set<Context.ResourceKey<?>> serializableResources) {
            assertNonNull(serializableResources, "The serializableResources may not be null.");
            this.serializableResources = Set.copyOf(serializableResources);
            return this;
        }

        /**
         * Sets the claim duration, which is the time a message gets locked when processing and waiting for it to
         * complete. Other invocations of the {@link #process(Predicate, Function, ProcessingContext)} method will be
         * unable to process a sequence while the claim is active. Its default is 30 seconds.
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
            assertNonNull(transactionalExecutorProvider,
                          "Must supply a TransactionalExecutorProvider when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(eventConverter,
                          "Must supply an eventConverter when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(genericConverter,
                          "Must supply a genericConverter when constructing a JpaSequencedDeadLetterQueue");
            validateSerializableResources(serializableResources);
            if (converter instanceof EventMessageDeadLetterJpaConverter) {
                converter = new EventMessageDeadLetterJpaConverter(serializableResources);
            }
        }

        private void validateSerializableResources(Set<Context.ResourceKey<?>> keys) {
            Set<String> labels = new HashSet<>();
            for (Context.ResourceKey<?> key : keys) {
                String label = assertNonBlank(key.label(),
                                              "All serializable ResourceKeys must have a non-blank label.");
                if (!labels.add(label)) {
                    throw new AxonConfigurationException(
                            "All serializable ResourceKeys must have unique labels. Duplicate: [" + label + "]."
                    );
                }
            }
        }
    }
}
