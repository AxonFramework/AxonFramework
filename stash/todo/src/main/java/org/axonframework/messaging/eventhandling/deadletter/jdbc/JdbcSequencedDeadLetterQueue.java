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

package org.axonframework.messaging.eventhandling.deadletter.jdbc;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionalExecutorProvider;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueueOverflowException;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.NoSuchDeadLetterException;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static org.axonframework.common.BuilderUtils.*;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.common.jdbc.JdbcUtils.*;

/**
 * A JDBC-based implementation of the {@link SequencedDeadLetterQueue}, used for storing dead letters containing
 * {@link EventMessage event messages} durably. Use the {@link #createSchema(DeadLetterTableFactory)} operation to build
 * the table and indices required by this {@code SequencedDeadLetterQueue}, providing the desired
 * {@link DeadLetterTableFactory}. The {@link java.sql.PreparedStatement statements} used by this queues methods can be
 * optimized by providing a custom {@link DeadLetterStatementFactory}.
 * <p>
 * Keeps the insertion order intact by saving an incremented index within each unique sequence, backed by the
 * {@link DeadLetterSchema#sequenceIndexColumn() index} property. Each sequence is uniquely identified by the sequence
 * identifier, stored in the {@link DeadLetterSchema#sequenceIdentifierColumn()} sequence identifier} field.
 * <p>
 * When processing an item, single execution across all applications is guaranteed by setting the
 * {@link DeadLetterSchema#processingStartedColumn() processing started} property, locking other processes out of the
 * sequence for the configured {@code claimDuration} (30 seconds by default).
 * <p>
 * The stored entries are converted to a {@link JdbcDeadLetter} when they need to be processed or filtered. In order to
 * restore the original {@link EventMessage} the {@link DeadLetterJdbcConverter} is used. The default supports all
 * {@code EventMessage} implementations provided by the framework. If you have a custom variant, you have to build your
 * own.
 * <p>
 * {@link org.axonframework.conversion.upcasting.Upcaster upcasters} are not supported by this implementation, so
 * breaking changes for events messages stored in the queue should be avoided.
 *
 * @param <E> An implementation of {@link EventMessage} contained in the {@link DeadLetter dead-letters} within this
 *            queue.
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class JdbcSequencedDeadLetterQueue<E extends EventMessage> implements SequencedDeadLetterQueue<E> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Instructs {@link org.axonframework.common.jdbc.JdbcUtils#executeQuery} not to close the connection, as it is
     * owned and committed by the surrounding {@link org.axonframework.common.tx.TransactionalExecutor}.
     */
    private static final boolean AUTO_CLOSE_CONNECTION = false;

    private final String processingGroup;
    private final TransactionalExecutorProvider<Connection> transactionalExecutorProvider;
    private final DeadLetterSchema schema;
    private final DeadLetterStatementFactory<E> statementFactory;
    private final DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter;
    private final int maxSequences;
    private final int maxSequenceSize;
    private final int pageSize;
    private final Duration claimDuration;

    /**
     * Instantiate a JDBC-based {@link SequencedDeadLetterQueue} through the given {@link Builder builder}.
     * <p>
     * Will validate whether the {@link Builder#processingGroup(String) processing group},
     * {@link Builder#transactionalExecutorProvider(TransactionalExecutorProvider) TransactionalExecutorProvider},
     * and the converters required to construct statement factories/converters (the
     * {@link Builder#eventConverter(EventConverter) EventConverter} and
     * {@link Builder#genericConverter(Converter) generic Converter}) are set. If any of these are not provided an
     * {@link AxonConfigurationException} is thrown.
     *
     * @param builder The {@link Builder} used to instantiate a {@link JdbcSequencedDeadLetterQueue} instance.
     */
    protected JdbcSequencedDeadLetterQueue(Builder<E> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.transactionalExecutorProvider = builder.transactionalExecutorProvider;
        this.schema = builder.schema;
        this.statementFactory = builder.statementFactory();
        this.converter = builder.converter();
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
        this.pageSize = builder.pageSize;
        this.claimDuration = builder.claimDuration;
    }

    /**
     * Instantiate a builder to construct a {@link JdbcSequencedDeadLetterQueue}.
     * <p>
     * The following defaults are set by the builder:
     * <ul>
     *     <li>The {@link Builder#schema(DeadLetterSchema) table's schema} defaults to a {@link DeadLetterSchema#defaultSchema()}.</li>
     *     <li>The {@link Builder#maxSequences(int) maximum amount of sequences} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#maxSequenceSize(int) maximum sequence size} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#pageSize(int) page size} defaults to {@code 100}.</li>
     *     <li>The {@link Builder#claimDuration(Duration) claim duration} defaults to 30 seconds.</li>
     * </ul>
     * <p>
     * The {@link Builder#processingGroup(String) processing group},
     * {@link Builder#transactionalExecutorProvider(TransactionalExecutorProvider) TransactionalExecutorProvider},
     * {@link Builder#eventConverter(EventConverter) EventConverter}, and
     * {@link Builder#genericConverter(Converter) generic Converter} are hard requirements and should be provided.
     *
     * @param <E> The type of {@link EventMessage} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A Builder that can construct an {@link JdbcSequencedDeadLetterQueue}.
     */
    public static <E extends EventMessage> Builder<E> builder() {
        return new Builder<>();
    }

    /**
     * Performs the DDL queries to create the schema necessary for this {@link SequencedDeadLetterQueue}
     * implementation.
     *
     * @param tableFactory The factory constructing the {@link java.sql.PreparedStatement} to construct a
     *                     {@link DeadLetter} entry table based on the
     *                     {@link Builder#schema(DeadLetterSchema) configured} {@link DeadLetterSchema}.
     * @param context      The {@link ProcessingContext} in which the schema will be created. May be {@code null}; when
     *                     provided, the connection executor is obtained from the context.
     * @return A {@link CompletableFuture} that completes when the schema has been created. Completes exceptionally with
     * a {@link JdbcException} if the table or indices could not be created.
     */
    public CompletableFuture<Void> createSchema(DeadLetterTableFactory tableFactory,
                                                @Nullable ProcessingContext context) {
        return connectionExecutor(context).accept(connection -> {
            Statement tableStatement = tableFactory.createTableStatement(connection, schema);
            try {
                tableStatement.executeBatch();
            } catch (Exception e) {
                throw new JdbcException("Failed to create the dead-letter entry table or indices", e);
            } finally {
                closeQuietly(tableStatement);
            }
        });
    }

    @Override
    @NonNull
    public CompletableFuture<Void> enqueue(@NonNull Object sequenceIdentifier,
                                           @NonNull DeadLetter<? extends E> letter,
                                           @Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> {
            String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
            return isFull(sequenceId, context).thenCompose(isFull -> {
                if (Boolean.TRUE.equals(isFull)) {
                    return CompletableFuture.failedFuture(new DeadLetterQueueOverflowException(
                            "No room left to enqueue [" + letter.message() + "] for identifier ["
                                    + sequenceId + "] since the queue is full."
                    ));
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
                            sequenceId);
                }

                return connectionExecutor(context).accept(connection -> {
                    long sequenceIndex = nextIndexForSequence(connection, sequenceId);
                    executeUpdate(connection,
                                  c -> statementFactory.enqueueStatement(
                                          c, processingGroup, sequenceId, letter, sequenceIndex, context
                                  ),
                                  e -> new JdbcException("Failed to enqueue dead letter with message id ["
                                                                 + letter.message().identifier() + "]", e));
                });
            });
        });
    }

    private long nextIndexForSequence(Connection connection, String sequenceId) {
        long maxIndex = executeQuery(connection,
                                     c -> statementFactory.maxIndexStatement(c, processingGroup, sequenceId),
                                     resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                                     e -> new JdbcException("Failed to uncover the maximum index for sequence ["
                                                                    + sequenceId + "]", e),
                                     AUTO_CLOSE_CONNECTION);
        long nextIndex = maxIndex + 1;
        logger.debug("Next index for [{}] is [{}]", sequenceId, nextIndex);
        return nextIndex;
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> evict(@NonNull DeadLetter<? extends E> letter,
                                         @Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> {
            if (!(letter instanceof JdbcDeadLetter)) {
                throw new WrongDeadLetterTypeException(
                        String.format("Invoke evict with a JdbcDeadLetter instance. Instead got: [%s]",
                                      letter.getClass().getName())
                );
            }
            JdbcDeadLetter<E> jdbcLetter = (JdbcDeadLetter<E>) letter;
            String identifier = jdbcLetter.getIdentifier();
            String sequenceIdentifier = jdbcLetter.getSequenceIdentifier();
            logger.info("Evicting dead letter with id [{}] for processing group [{}] and sequence [{}]",
                        identifier, processingGroup, sequenceIdentifier);

            return connectionExecutor(context).accept(connection -> {
                int deletedRows = executeUpdate(connection,
                                                c -> statementFactory.evictStatement(c, identifier),
                                                e -> new JdbcException(
                                                        "Failed to evict letter with message id ["
                                                                + letter.message().identifier() + "]", e
                                                ));
                if (deletedRows == 0) {
                    logger.info("Dead letter with identifier [{}] for processing group [{}] "
                                        + "and sequence [{}] was already evicted",
                                identifier, processingGroup, sequenceIdentifier);
                }
            });
        });
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> requeue(@NonNull DeadLetter<? extends E> letter,
                                           @NonNull UnaryOperator<DeadLetter<? extends E>> letterUpdater,
                                           @Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> {
            if (!(letter instanceof JdbcDeadLetter)) {
                throw new WrongDeadLetterTypeException(
                        String.format("Invoke requeue with a JdbcDeadLetter instance. Instead got: [%s]",
                                      letter.getClass().getName())
                );
            }
            JdbcDeadLetter<E> jdbcLetter = (JdbcDeadLetter<E>) letter;
            String identifier = jdbcLetter.getIdentifier();
            logger.info("Requeueing dead letter with id [{}] for processing group [{}] and sequence [{}]",
                        identifier, processingGroup, jdbcLetter.getSequenceIdentifier());
            DeadLetter<? extends E> updatedLetter = letterUpdater.apply(jdbcLetter).markTouched();

            return connectionExecutor(context).accept(connection -> {
                int updatedRows = executeUpdate(
                        connection,
                        c -> statementFactory.requeueStatement(c,
                                                               identifier,
                                                               updatedLetter.cause().orElse(null),
                                                               updatedLetter.lastTouched(),
                                                               updatedLetter.diagnostics()),
                        e -> new JdbcException("Failed to requeue letter with message id ["
                                                       + letter.message().identifier() + "]", e)
                );
                if (updatedRows == 0) {
                    throw new NoSuchDeadLetterException("Cannot requeue [" + letter.message().identifier()
                                                                + "] since there is no matching entry in this queue.");
                } else if (logger.isTraceEnabled()) {
                    logger.trace("Requeued letter [{}] for sequence [{}].",
                                 identifier, jdbcLetter.getSequenceIdentifier());
                }
            });
        });
    }

    @Override
    @NonNull
    public CompletableFuture<Boolean> contains(@NonNull Object sequenceIdentifier,
                                               @Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> {
            String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
            logger.debug("Validating existence of sequence identifier [{}].", sequenceId);
            return connectionExecutor(context)
                    .apply(connection -> executeQuery(
                            connection,
                            c -> statementFactory.containsStatement(c, processingGroup, sequenceId),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L) > 0L,
                            e -> new JdbcException("Failed to validate whether there are letters "
                                                           + "present for sequence [" + sequenceId + "]", e),
                            AUTO_CLOSE_CONNECTION
                    ));
        });
    }

    @Override
    @NonNull
    public CompletableFuture<Iterable<DeadLetter<? extends E>>> deadLetterSequence(
            @NonNull Object sequenceIdentifier,
            @Nullable ProcessingContext context
    ) {
        return FutureUtils.runFailing(() -> {
            String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
            return CompletableFuture.completedFuture(deadLetterSequenceIterable(sequenceId, context));
        });
    }

    private Iterable<DeadLetter<? extends E>> deadLetterSequenceIterable(String sequenceId,
                                                                         @Nullable ProcessingContext context) {
        return new PagingJdbcIterable<>(
                connectionExecutor(context),
                (connection, firstResult, maxSize) -> statementFactory.letterSequenceStatement(
                        connection, processingGroup, sequenceId, firstResult, maxSequenceSize
                ),
                pageSize,
                converter::convertToLetter,
                e -> new JdbcException("Failed to retrieve dead letters for sequence [" + sequenceId + "]", e)
        );
    }

    @Override
    @NonNull
    public CompletableFuture<Iterable<Iterable<DeadLetter<? extends E>>>> deadLetters(
            @Nullable ProcessingContext context
    ) {
        return FutureUtils.runFailing(() -> connectionExecutor(context)
                .apply(connection -> executeQuery(
                        connection,
                        c -> statementFactory.sequenceIdentifiersStatement(c, processingGroup),
                        listResults(resultSet -> resultSet.getString(1)),
                        e -> new JdbcException("Failed to retrieve all sequence identifiers", e),
                        AUTO_CLOSE_CONNECTION
                ))
                .thenApply(sequenceIdentifiers -> (Iterable<Iterable<DeadLetter<? extends E>>>) () -> {
                    Iterator<String> sequenceIterator = sequenceIdentifiers.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return sequenceIterator.hasNext();
                        }

                        @Override
                        public Iterable<DeadLetter<? extends E>> next() {
                            return deadLetterSequenceIterable(sequenceIterator.next(), context);
                        }
                    };
                }));
    }

    @Override
    @NonNull
    public CompletableFuture<Boolean> isFull(@NonNull Object sequenceIdentifier,
                                             @Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> {
            String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
            return sequenceSize(sequenceId, context)
                    .thenCompose(numberInSequence -> numberInSequence > 0
                            ? CompletableFuture.completedFuture(numberInSequence >= maxSequenceSize)
                            : amountOfSequences(context).thenApply(amount -> amount >= maxSequences));
        });
    }

    @Override
    @NonNull
    public CompletableFuture<Long> size(@Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> connectionExecutor(context)
                .apply(connection -> executeQuery(connection,
                                                  c -> statementFactory.sizeStatement(c, processingGroup),
                                                  resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                                                  e -> new JdbcException("Failed to check the total number of "
                                                                                 + "dead letters", e),
                                                  AUTO_CLOSE_CONNECTION)));
    }

    @Override
    @NonNull
    public CompletableFuture<Long> sequenceSize(@NonNull Object sequenceIdentifier,
                                                @Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> {
            String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
            return connectionExecutor(context)
                    .apply(connection -> executeQuery(
                            connection,
                            c -> statementFactory.sequenceSizeStatement(c, processingGroup, sequenceId),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                            e -> new JdbcException("Failed to check the number of dead letters in sequence ["
                                                           + sequenceId + "]", e),
                            AUTO_CLOSE_CONNECTION));
        });
    }

    @Override
    @NonNull
    public CompletableFuture<Long> amountOfSequences(@Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> connectionExecutor(context)
                .apply(connection -> executeQuery(
                        connection,
                        c -> statementFactory.amountOfSequencesStatement(c, processingGroup),
                        resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                        e -> new JdbcException(
                                "Failed to check the number of dead letter sequences in this queue", e),
                        AUTO_CLOSE_CONNECTION)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Optimized override that retrieves available sequences one at a time (page size of 1) instead of in batches. Since
     * no {@code sequenceFilter} is applied, every available sequence is a valid candidate, so there is no need to
     * prefetch multiple sequences. This avoids unnecessary deserialization of dead letter entries that will never be
     * evaluated.
     *
     * @see #process(Predicate, Function, ProcessingContext)
     */
    @Override
    @NonNull
    public CompletableFuture<Boolean> process(
            @NonNull Function<DeadLetter<? extends E>, CompletableFuture<EnqueueDecision<E>>> processingTask,
            @Nullable ProcessingContext context
    ) {
        return FutureUtils.runFailing(() -> {
            logger.debug("Received a request to process any dead letters.");
            Iterator<? extends JdbcDeadLetter<E>> iterator = findClaimableSequences(1, context);
            return claimFirstAvailableLetter(iterator, context).thenCompose(claimedLetter -> {
                if (claimedLetter == null) {
                    logger.debug("Received a request to process dead letters but there are no claimable sequences.");
                    return CompletableFuture.completedFuture(false);
                }
                return processLetterAndFollowing(claimedLetter, processingTask, context);
            });
        });
    }

    @Override
    @NonNull
    public CompletableFuture<Boolean> process(
            @NonNull Predicate<DeadLetter<? extends E>> sequenceFilter,
            @NonNull Function<DeadLetter<? extends E>, CompletableFuture<EnqueueDecision<E>>> processingTask,
            @Nullable ProcessingContext context
    ) {
        return FutureUtils.runFailing(() -> {
            logger.debug("Received a request to process matching dead letters.");
            Iterator<? extends JdbcDeadLetter<E>> iterator = findClaimableSequences(10, context);
            return claimFirstMatchingLetter(iterator, sequenceFilter, context).thenCompose(claimedLetter -> {
                if (claimedLetter == null) {
                    logger.debug("Received a request to process dead letters "
                                         + "but there are no matching or claimable sequences.");
                    return CompletableFuture.completedFuture(false);
                }
                return processLetterAndFollowing(claimedLetter, processingTask, context);
            });
        });
    }

    private CompletableFuture<JdbcDeadLetter<E>> claimFirstAvailableLetter(
            Iterator<? extends JdbcDeadLetter<E>> iterator,
            @Nullable ProcessingContext context
    ) {
        while (iterator.hasNext()) {
            JdbcDeadLetter<E> next = iterator.next();
            return claimDeadLetter(next, context)
                    .thenCompose(claimed -> claimed
                            ? CompletableFuture.completedFuture(next)
                            : claimFirstAvailableLetter(iterator, context));
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<JdbcDeadLetter<E>> claimFirstMatchingLetter(
            Iterator<? extends JdbcDeadLetter<E>> iterator,
            Predicate<DeadLetter<? extends E>> sequenceFilter,
            @Nullable ProcessingContext context
    ) {
        while (iterator.hasNext()) {
            JdbcDeadLetter<E> next = iterator.next();
            if (!sequenceFilter.test(next)) {
                continue;
            }
            return claimDeadLetter(next, context)
                    .thenCompose(claimed -> claimed
                            ? CompletableFuture.completedFuture(next)
                            : claimFirstMatchingLetter(iterator, sequenceFilter, context));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Processes the given {@code firstDeadLetter} using the provided {@code processingTask}. When successful (the
     * message is evicted) it will automatically process all messages in the same
     * {@link JdbcDeadLetter#getSequenceIdentifier()}, evicting messages that succeed and stopping when the first one
     * fails (and is requeued).
     * <p>
     * Will claim the next letter in the same sequence before removing the old one to prevent concurrency issues. Note
     * that we do not use paging on the results here, since messages can be added to the end of the sequence before
     * processing ends, and deletes would throw the ordering off.
     *
     * @param firstDeadLetter The dead letter to start processing.
     * @param processingTask  The task to use to process the dead letter, providing a decision afterwards.
     * @param context         The processing context for this operation.
     * @return Whether processing all letters in this sequence was successful.
     */
    private CompletableFuture<Boolean> processLetterAndFollowing(
            JdbcDeadLetter<E> firstDeadLetter,
            Function<DeadLetter<? extends E>, CompletableFuture<EnqueueDecision<E>>> processingTask,
            @Nullable ProcessingContext context
    ) {
        JdbcDeadLetter<E> deadLetter = firstDeadLetter;
        logger.info("Processing dead letter with identifier [{}] at index [{}]",
                    deadLetter.getIdentifier(), deadLetter.getSequenceIndex());

        CompletableFuture<EnqueueDecision<E>> decisionFuture;
        try {
            decisionFuture = processingTask.apply(deadLetter);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        return decisionFuture.thenCompose(decision -> {
            if (!decision.shouldEnqueue()) {
                return findNext(deadLetter.getSequenceIdentifier(), deadLetter.getSequenceIndex(), context)
                        .thenCompose(next -> {
                            if (next == null) {
                                return evict(deadLetter, context).thenApply(ignored -> true);
                            }
                            return claimDeadLetter(next, context)
                                    .thenCompose(ignored -> evict(deadLetter, context))
                                    .thenCompose(ignored -> processLetterAndFollowing(next, processingTask, context));
                        });
            }

            return requeue(deadLetter,
                           l -> decision.withDiagnostics(l)
                                        .withCause(decision.enqueueCause().orElse(null)),
                           context)
                    .thenApply(ignored -> false);
        });
    }

    /**
     * Fetches the first letter for each sequence in the provided {@code processingGroup} that is not claimed. Note that
     * the first letters have the lowest {@link DeadLetterSchema#sequenceIndexColumn() index} within their sequence.
     * <p>
     * A sequence is regarded claimable when {@link DeadLetterSchema#processingStartedColumn() processing started} is
     * {@code null} or processing started is longer ago than the configured {@code claimDuration}.
     *
     * @param pageSize The size of the paging on the query. Lower is faster, but with many results a larger page is
     *                 better.
     * @param context  The processing context for this operation.
     * @return An iterator over the first letters of each claimable sequence.
     */
    private Iterator<? extends JdbcDeadLetter<E>> findClaimableSequences(
            int pageSize,
            @Nullable ProcessingContext context
    ) {
        return new PagingJdbcIterable<>(
                connectionExecutor(context),
                (connection, firstResult, maxSize) -> statementFactory.claimableSequencesStatement(
                        connection, processingGroup, processingStartedLimit(), firstResult, maxSize
                ),
                pageSize,
                converter::convertToLetter,
                e -> new JdbcException("Failed to find any claimable sequences for processing", e)
        ).iterator();
    }

    /**
     * Claims the provided {@code letter} in the database by setting the
     * {@link DeadLetterSchema#processingStartedColumn() processing started} property. Will check whether it was claimed
     * successfully and return an appropriate boolean result.
     *
     * @return A future completing with whether the letter was successfully claimed or not.
     */
    private CompletableFuture<Boolean> claimDeadLetter(JdbcDeadLetter<E> letter,
                                                       @Nullable ProcessingContext context) {
        Instant processingStartedLimit = processingStartedLimit();
        return connectionExecutor(context).apply(connection -> {
            int updatedRows = executeUpdate(
                    connection,
                    c -> statementFactory.claimStatement(
                            c, letter.getIdentifier(), GenericDeadLetter.clock.instant(), processingStartedLimit
                    ),
                    e -> new JdbcException(
                            "Failed to claim JDBC dead letter [" + letter.getIdentifier() + "] for processing", e
                    )
            );
            if (updatedRows > 0) {
                logger.debug("Claimed dead letter with identifier [{}] to process.", letter.getIdentifier());
                return true;
            }
            logger.debug("Failed to claim dead letter with identifier [{}].", letter.getIdentifier());
            return false;
        });
    }

    /**
     * Determines the time the {@link DeadLetterSchema#processingStartedColumn() processing started} value a dead letter
     * entry at least needs to have to stay claimed. The returned limit is based on the difference between the
     * {@link GenericDeadLetter#clock} and the configurable {@link Builder#claimDuration claim duration}.
     *
     * @return The difference between the {@link GenericDeadLetter#clock} and the
     * {@link Builder#claimDuration claim duration}.
     */
    private Instant processingStartedLimit() {
        return GenericDeadLetter.clock.instant().minus(claimDuration);
    }

    /**
     * Finds the next dead letter matching the given {@code sequenceIdentifier} following the provided
     * {@code sequenceIndex}.
     *
     * @param sequenceIdentifier The identifier of the sequence to find the next letter for.
     * @param sequenceIndex      The index within the sequence to find the following letter for.
     * @param context            The processing context for this operation.
     * @return A future completing with the next letter to process, or null if there are no more.
     */
    private CompletableFuture<JdbcDeadLetter<E>> findNext(
            String sequenceIdentifier,
            long sequenceIndex,
            @Nullable ProcessingContext context
    ) {
        return connectionExecutor(context).apply(connection -> executeQuery(
                connection,
                c -> statementFactory.nextLetterInSequenceStatement(
                        c, processingGroup, sequenceIdentifier, sequenceIndex
                ),
                resultSet -> resultSet.next() ? converter.convertToLetter(resultSet) : null,
                e -> new JdbcException(
                        "Failed to find the next dead letter in sequence [" + sequenceIdentifier
                                + "] for processing", e
                ),
                AUTO_CLOSE_CONNECTION
        ));
    }

    @Override
    @NonNull
    public CompletableFuture<Void> clear(@Nullable ProcessingContext context) {
        return FutureUtils.runFailing(() -> connectionExecutor(context)
                .accept(connection -> executeUpdate(
                        connection,
                        c -> statementFactory.clearStatement(c, processingGroup),
                        e -> new JdbcException("Failed to clear out all dead letters for "
                                                       + "processing group [" + processingGroup + "]", e))));
    }

    /**
     * Converts the given {@code sequenceIdentifier} to a {@link String}.
     *
     * @return The given {@code sequenceIdentifier} as a {@link String}.
     */
    private String toStringSequenceIdentifier(Object sequenceIdentifier) {
        return sequenceIdentifier instanceof String
                ? (String) sequenceIdentifier
                : Integer.toString(sequenceIdentifier.hashCode());
    }

    private TransactionalExecutor<Connection> connectionExecutor(@Nullable ProcessingContext context) {
        return transactionalExecutorProvider.getTransactionalExecutor(context);
    }

    /**
     * Builder class to instantiate a {@link JdbcSequencedDeadLetterQueue}.
     * <p>
     * The following defaults are set by the builder:
     * <ul>
     *     <li>The {@link Builder#schema(DeadLetterSchema) table's schema} defaults to a {@link DeadLetterSchema#defaultSchema()}.</li>
     *     <li>The {@link Builder#maxSequences(int) maximum amount of sequences} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#maxSequenceSize(int) maximum sequence size} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#pageSize(int) page size} defaults to {@code 100}.</li>
     *     <li>The {@link Builder#claimDuration(Duration) claim duration} defaults to 30 seconds.</li>
     * </ul>
     * <p>
     * The {@link Builder#processingGroup(String) processing group},
     * {@link Builder#transactionalExecutorProvider(TransactionalExecutorProvider) TransactionalExecutorProvider},
     * {@link Builder#eventConverter(EventConverter) EventConverter}, and
     * {@link Builder#genericConverter(Converter) generic Converter} are hard requirements and should be provided.
     *
     * @param <E> The type of {@link EventMessage} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<E extends EventMessage> {

        private String processingGroup;
        private TransactionalExecutorProvider<Connection> transactionalExecutorProvider;
        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private DeadLetterStatementFactory<E> statementFactory;
        private DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter;
        private EventConverter eventConverter;
        private Converter genericConverter;
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private int pageSize = 100;
        private Duration claimDuration = Duration.ofSeconds(30);

        /**
         * Sets the processing group, which is used for storing and querying which processing group a dead-lettered
         * {@link EventMessage} belonged to.
         *
         * @param processingGroup The processing group of this {@link SequencedDeadLetterQueue}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> processingGroup(@NonNull String processingGroup) {
            assertNonEmpty(processingGroup, "Can not set processingGroup to an empty String.");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Sets the {@link TransactionalExecutorProvider} which provides the {@link TransactionalExecutor} used to
         * execute operations against the underlying database for this {@link JdbcSequencedDeadLetterQueue}
         * implementation.
         *
         * @param transactionalExecutorProvider A {@link TransactionalExecutorProvider} providing
         *                                      {@link TransactionalExecutor TransactionalExecutors} used to access the
         *                                      underlying database.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> transactionalExecutorProvider(
                @NonNull TransactionalExecutorProvider<Connection> transactionalExecutorProvider) {
            assertNonNull(transactionalExecutorProvider, "TransactionalExecutorProvider may not be null");
            this.transactionalExecutorProvider = transactionalExecutorProvider;
            return this;
        }

        /**
         * Sets the {@link DeadLetterSchema} used to constructs the table and indices required by this
         * {@link SequencedDeadLetterQueue}.
         * <p>
         * The {@code schema} will be used to construct the {@link DeadLetterStatementFactory} when it is not explicitly
         * configured.
         * <p>
         * Defaults to the default {@link DeadLetterSchema#defaultSchema() schema} configuration.
         *
         * @param schema The {@link DeadLetterSchema} used to constructs the table and indices required by this
         *               dead-letter queue.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> schema(@NonNull DeadLetterSchema schema) {
            assertNonNull(schema, "DeadLetterSchema may not be null");
            this.schema = schema;
            return this;
        }

        /**
         * Sets the {@link DeadLetterStatementFactory} used to construct all
         * {@link java.sql.PreparedStatement PreparedStatements} executed by this {@link SequencedDeadLetterQueue}.
         * <p>
         * When the {@code statementFactory} is not explicitly configured, this builder defaults to the
         * {@link DefaultDeadLetterStatementFactory}. To construct the {@code DefaultDeadLetterStatementFactory}, the
         * configured {@link #eventConverter(EventConverter) EventConverter} and
         * {@link #genericConverter(Converter) generic Converter} are used.
         *
         * @param statementFactory The {@link DeadLetterStatementFactory} used to construct all
         *                         {@link java.sql.PreparedStatement PreparedStatements} executed by this
         *                         {@link SequencedDeadLetterQueue}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> statementFactory(@NonNull DeadLetterStatementFactory<E> statementFactory) {
            assertNonNull(statementFactory, "The DeadLetterStatementFactory may not be null");
            this.statementFactory = statementFactory;
            return this;
        }

        /**
         * Sets the {@link DeadLetterJdbcConverter} used to convert a {@link java.sql.ResultSet} into a
         * {@link JdbcDeadLetter} implementation. The {@code converter} is, for example, used to service the
         * {@link #deadLetters(ProcessingContext)} and {@link #deadLetterSequence(Object, ProcessingContext)}
         * operations.
         * <p>
         * When the {@code converter} is not explicitly configured, this builder defaults to the
         * {@link DefaultDeadLetterJdbcConverter}. To construct the {@code DefaultDeadLetterJdbcConverter}, the
         * configured {@link #eventConverter(EventConverter) EventConverter} and
         * {@link #genericConverter(Converter) generic Converter} are used.
         *
         * @param converter The {@link DeadLetterJdbcConverter} used to convert a {@link java.sql.ResultSet} into a
         *                  {@link JdbcDeadLetter} implementation.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> converter(@NonNull DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter) {
            assertNonNull(converter, "The DeadLetterJdbcConverter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Sets the {@link EventConverter} to convert the event payload and metadata of the {@link DeadLetter} when
         * storing it to and retrieving it from the database.
         * <p>
         * The {@code eventConverter} will be used to construct the {@link DeadLetterStatementFactory} and/or
         * {@link DeadLetterJdbcConverter} when either of them are not explicitly configured.
         *
         * @param eventConverter The event converter to use for payload and metadata conversion.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> eventConverter(@NonNull EventConverter eventConverter) {
            assertNonNull(eventConverter, "The EventConverter may not be null");
            this.eventConverter = eventConverter;
            return this;
        }

        /**
         * Sets the {@link Converter} to convert the tracking token and diagnostics of the {@link DeadLetter} when
         * storing it to and retrieving it from the database.
         * <p>
         * The {@code genericConverter} will be used to construct the {@link DeadLetterStatementFactory} and/or
         * {@link DeadLetterJdbcConverter} when either of them are not explicitly configured.
         *
         * @param genericConverter The converter to use for tracking token and diagnostics conversion.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> genericConverter(@NonNull Converter genericConverter) {
            assertNonNull(genericConverter, "The generic Converter may not be null");
            this.genericConverter = genericConverter;
            return this;
        }

        /**
         * Sets the maximum number of unique sequences this {@link SequencedDeadLetterQueue} may contain.
         * <p>
         * The given {@code maxSequences} is required to be a positive number. The maximum number of unique sequences
         * defaults to {@code 1024}.
         *
         * @param maxSequences The maximum amount of unique sequences for the queue under construction.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> maxSequences(int maxSequences) {
            assertStrictPositive(maxSequences, "The maximum number of sequences should be larger than 0");
            this.maxSequences = maxSequences;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetter letters} per unique sequences this
         * {@link SequencedDeadLetterQueue} can store.
         * <p>
         * The given {@code maxSequenceSize} is required to be a positive number. The maximum amount of letters per
         * unique sequence defaults to {@code 1024}.
         *
         * @param maxSequenceSize The maximum amount of {@link DeadLetter letters} per unique  sequence.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> maxSequenceSize(int maxSequenceSize) {
            assertStrictPositive(maxSequenceSize,
                                 "The maximum number of entries in a sequence should be larger than 0");
            this.maxSequenceSize = maxSequenceSize;
            return this;
        }

        /**
         * Sets the claim duration, which is the time a dead-letter gets locked when processing and waiting for it to
         * complete. Other invocations of the {@link #process(Predicate, Function, ProcessingContext)} method will be
         * unable to process a sequence while the claim is active. The claim duration defaults to 30 seconds.
         * <p>
         * Claims are automatically released once the item is requeued. Thus, the claim time is a backup policy in case
         * of unforeseen trouble such as database connection issues.
         *
         * @param claimDuration The longest claim duration allowed.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> claimDuration(@NonNull Duration claimDuration) {
            assertNonNull(claimDuration, "Claim duration can not be set to null.");
            this.claimDuration = claimDuration;
            return this;
        }

        /**
         * Modifies the page size used when retrieving a sequence of dead letters.
         * <p>
         * Used during the {@link #deadLetterSequence(Object, ProcessingContext)} and
         * {@link #deadLetters(ProcessingContext)} operations. Defaults to {@code 100}.
         *
         * @param pageSize The page size used when retrieving a sequence of dead letters.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> pageSize(int pageSize) {
            assertStrictPositive(pageSize, "The page size should be larger than 0.");
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Initializes a {@link JdbcSequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link JdbcSequencedDeadLetterQueue} as specified through this Builder.
         */
        public JdbcSequencedDeadLetterQueue<E> build() {
            return new JdbcSequencedDeadLetterQueue<>(this);
        }

        /**
         * Retrieve the {@link DeadLetterStatementFactory} for this {@link SequencedDeadLetterQueue}.
         * <p>
         * When not set explicitly through {@link #statementFactory(DeadLetterStatementFactory)} a
         * {@link DefaultDeadLetterStatementFactory} is constructed based on the
         * {@link #schema(DeadLetterSchema) schema}, {@link #eventConverter(EventConverter) EventConverter}, and
         * {@link #genericConverter(Converter) generic Converter}.
         *
         * @return The {@link DeadLetterStatementFactory} as defined within this builder.
         */
        private DeadLetterStatementFactory<E> statementFactory() {
            return getOrDefault(statementFactory, DefaultDeadLetterStatementFactory.<E>builder()
                                                                                   .schema(schema)
                                                                                   .eventConverter(eventConverter)
                                                                                   .genericConverter(genericConverter)
                                                                                   .build());
        }

        /**
         * Retrieve the {@link DeadLetterJdbcConverter} for this {@link SequencedDeadLetterQueue}.
         * <p>
         * When not set explicitly through {@link #converter(DeadLetterJdbcConverter)} a
         * {@link DefaultDeadLetterJdbcConverter} is constructed based on the
         * {@link #eventConverter(EventConverter) EventConverter} and
         * {@link #genericConverter(Converter) generic Converter}.
         *
         * @return The {@link DeadLetterJdbcConverter} as defined within this builder.
         */
        private DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter() {
            return getOrDefault(converter, () -> DefaultDeadLetterJdbcConverter.<E>builder()
                                                                               .schema(schema)
                                                                               .eventConverter(eventConverter)
                                                                               .genericConverter(genericConverter)
                                                                               .build());
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonEmpty(processingGroup, "The processing group is a hard requirement and should be non-empty");
            assertNonNull(transactionalExecutorProvider,
                          "The TransactionalExecutorProvider is a hard requirement and should be provided");
            if (statementFactory == null) {
                assertNonNull(
                        eventConverter,
                        "The EventConverter is a hard requirement when the DeadLetterStatementFactory is not provided"
                );
                assertNonNull(
                        genericConverter,
                        "The generic Converter is a hard requirement when the DeadLetterStatementFactory is not provided"
                );
            }
            if (converter == null) {
                assertNonNull(
                        eventConverter,
                        "The EventConverter is a hard requirement when the DeadLetterJdbcConverter is not provided"
                );
                assertNonNull(
                        genericConverter,
                        "The generic Converter is a hard requirement when the DeadLetterJdbcConverter is not provided"
                );
            }
        }
    }
}
