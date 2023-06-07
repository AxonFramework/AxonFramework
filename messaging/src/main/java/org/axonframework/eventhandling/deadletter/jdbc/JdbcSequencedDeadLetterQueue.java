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

package org.axonframework.eventhandling.deadletter.jdbc;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.common.jdbc.PagingJdbcIterable;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

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
 * {@link org.axonframework.serialization.upcasting.Upcaster upcasters} are not supported by this implementation, so
 * breaking changes for events messages stored in the queue should be avoided.
 *
 * @param <E> An implementation of {@link EventMessage} contained in the {@link DeadLetter dead-letters} within this
 *            queue.
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class JdbcSequencedDeadLetterQueue<E extends EventMessage<?>> implements SequencedDeadLetterQueue<E> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final boolean CLOSE_QUIETLY = true;

    private final String processingGroup;
    private final ConnectionProvider connectionProvider;
    private final TransactionManager transactionManager;
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
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider},
     * {@link Builder#transactionManager(TransactionManager) TransactionManager},
     * {@link Builder#statementFactory(DeadLetterStatementFactory) DeadLetterStatementFactory} and
     * {@link Builder#converter(DeadLetterJdbcConverter) DeadLetterJdbcConverter} are set. If for either this is not the
     * case an {@link AxonConfigurationException} is thrown.
     *
     * @param builder The {@link Builder} used to instantiate a {@link JdbcSequencedDeadLetterQueue} instance.
     */
    protected JdbcSequencedDeadLetterQueue(Builder<E> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.connectionProvider = builder.connectionProvider;
        this.transactionManager = builder.transactionManager;
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
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider}, and
     * {@link Builder#transactionManager(TransactionManager) TransactionManager} are hard requirements and should be
     * provided.
     * <p>
     * The {@link Builder#statementFactory(DeadLetterStatementFactory)} and
     * {@link Builder#converter(DeadLetterJdbcConverter)} are also hard requirements, but users can choose to either set
     * both explicitly or rely on the {@link DefaultDeadLetterStatementFactory} and
     * {@link DefaultDeadLetterJdbcConverter} constructed through the
     * {@link Builder#genericSerializer(Serializer) generic Serializer} and
     * {@link Builder#eventSerializer(Serializer) event Serializer}.
     *
     * @param <E> The type of {@link EventMessage} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A Builder that can construct an {@link JdbcSequencedDeadLetterQueue}.
     */
    public static <E extends EventMessage<?>> Builder<E> builder() {
        return new Builder<>();
    }

    /**
     * Performs the DDL queries to create the schema necessary for this {@link SequencedDeadLetterQueue}
     * implementation.
     *
     * @param tableFactory The factory constructing the {@link java.sql.PreparedStatement} to construct a
     *                     {@link DeadLetter} entry table based on the
     *                     {@link Builder#schema(DeadLetterSchema) configured} {@link DeadLetterSchema}.
     */
    public void createSchema(DeadLetterTableFactory tableFactory) {
        Connection connection = getConnection();
        try {
            executeUpdates(
                    connection,
                    e -> {
                        throw new JdbcException("Failed to create the dead-letter entry table or indices", e);
                    },
                    c -> tableFactory.createTable(c, schema),
                    c -> tableFactory.createProcessingGroupIndex(c, schema),
                    c -> tableFactory.createSequenceIdentifierIndex(c, schema)
            );
        } finally {
            closeQuietly(connection);
        }
    }

    private Connection getConnection() {
        try {
            return connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new JdbcException("Failed to obtain a database connection", e);
        }
    }

    @Override
    public void enqueue(@Nonnull Object sequenceIdentifier,
                        @Nonnull DeadLetter<? extends E> letter) throws DeadLetterQueueOverflowException {
        String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
        if (isFull(sequenceId)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue [" + letter.message() + "] for identifier ["
                            + sequenceId + "] since the queue is full."
            );
        }

        if (logger.isDebugEnabled()) {
            Optional<Cause> optionalCause = letter.cause();
            if (optionalCause.isPresent()) {
                logger.info("Adding dead letter with message id [{}] because [{}].",
                            letter.message().getIdentifier(), optionalCause.get());
            } else {
                logger.info(
                        "Adding dead letter with message id [{}] because the sequence identifier [{}] is already present.",
                        letter.message().getIdentifier(),
                        sequenceId);
            }
        }

        Connection connection = getConnection();
        try {
            executeUpdate(connection,
                          c -> statementFactory.enqueueStatement(
                                  c, processingGroup, sequenceId, letter, nextIndexForSequence(sequenceId)
                          ),
                          e -> new JdbcException("Failed to enqueue dead letter with with message id ["
                                                         + letter.message().getIdentifier() + "]", e));
        } finally {
            closeQuietly(connection);
        }
    }

    private long nextIndexForSequence(String sequenceId) {
        // todo implement solution to start at zero
        long nextIndex = maxIndexForSequence(sequenceId) + 1;
        logger.debug("Next index for [{}] is [{}]", sequenceId, nextIndex);
        return nextIndex;
    }

    private long maxIndexForSequence(String sequenceId) {
        return transactionManager.fetchInTransaction(() -> executeQuery(
                getConnection(),
                connection -> statementFactory.maxIndexStatement(connection, processingGroup, sequenceId),
                resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                e -> new JdbcException("Failed to uncover the maximum index for sequence [" + sequenceId + "]", e)
        ));
    }

    @Override
    public void evict(DeadLetter<? extends E> letter) {
        if (!(letter instanceof JdbcDeadLetter)) {
            throw new WrongDeadLetterTypeException(
                    String.format("Invoke evict with a JdbcDeadLetter instance. Instead got: [%s]",
                                  letter.getClass().getName())
            );
        }
        //noinspection unchecked
        JdbcDeadLetter<E> jdbcLetter = (JdbcDeadLetter<E>) letter;
        String identifier = jdbcLetter.getIdentifier();
        String sequenceIdentifier = jdbcLetter.getSequenceIdentifier();
        logger.info("Evicting dead letter with id [{}] for processing group [{}] and sequence [{}]",
                    identifier, processingGroup, sequenceIdentifier);

        transactionManager.executeInTransaction(() -> {
            Connection connection = getConnection();
            try {
                int deletedRows = executeUpdate(connection,
                                                c -> statementFactory.evictStatement(c, identifier),
                                                e -> new JdbcException(
                                                        "Failed to evict letter with message id ["
                                                                + letter.message().getIdentifier() + "]", e
                                                ));
                if (deletedRows == 0) {
                    logger.info("Dead letter with identifier [{}] for processing group [{}] "
                                        + "and sequence [{}] was already evicted",
                                identifier, processingGroup, sequenceIdentifier);
                }
            } finally {
                closeQuietly(connection);
            }
        });
    }

    @Override
    public void requeue(
            @Nonnull DeadLetter<? extends E> letter,
            @Nonnull UnaryOperator<DeadLetter<? extends E>> letterUpdater
    ) throws NoSuchDeadLetterException {
        if (!(letter instanceof JdbcDeadLetter)) {
            throw new WrongDeadLetterTypeException(
                    String.format("Invoke requeue with a JdbcDeadLetter instance. Instead got: [%s]",
                                  letter.getClass().getName())
            );
        }
        //noinspection unchecked
        JdbcDeadLetter<E> jdbcLetter = (JdbcDeadLetter<E>) letter;
        String identifier = jdbcLetter.getIdentifier();
        logger.info("Requeueing dead letter with id [{}] for processing group [{}] and sequence [{}]",
                    identifier, processingGroup, jdbcLetter.getSequenceIdentifier());
        DeadLetter<? extends E> updatedLetter = letterUpdater.apply(jdbcLetter).markTouched();

        transactionManager.executeInTransaction(() -> {
            Connection connection = getConnection();
            try {
                int updatedRows = executeUpdate(
                        connection,
                        c -> statementFactory.requeueStatement(c,
                                                               identifier,
                                                               updatedLetter.cause().orElse(null),
                                                               updatedLetter.lastTouched(),
                                                               updatedLetter.diagnostics()),
                        e -> new JdbcException("Failed to requeue letter with message id ["
                                                       + letter.message().getIdentifier() + "]", e)
                );
                if (updatedRows == 0) {
                    throw new NoSuchDeadLetterException("Cannot requeue [" + letter.message().getIdentifier()
                                                                + "] since there is not matching entry in this queue.");
                } else if (logger.isTraceEnabled()) {
                    logger.trace("Requeued letter [{}] for sequence [{}].",
                                 identifier, jdbcLetter.getSequenceIdentifier());
                }
            } finally {
                closeQuietly(connection);
            }
        });
    }

    @Override
    public boolean contains(@Nonnull Object sequenceIdentifier) {
        String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
        if (logger.isDebugEnabled()) {
            logger.debug("Validating existence of sequence identifier [{}].", sequenceId);
        }

        return executeQuery(getConnection(),
                            connection -> statementFactory.containsStatement(connection, processingGroup, sequenceId),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L) > 0L,
                            e -> new JdbcException("Failed to validate whether there are letters "
                                                           + "present for sequence [" + sequenceId + "]", e),
                            CLOSE_QUIETLY);
    }

    @Override
    public Iterable<DeadLetter<? extends E>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
        if (!contains(sequenceId)) {
            return Collections.emptyList();
        }

        return new PagingJdbcIterable<>(
                pageSize,
                this::getConnection,
                transactionManager,
                (connection, firstResult, maxSize) -> statementFactory.letterSequenceStatement(
                        connection, processingGroup, sequenceId, firstResult, maxSequenceSize
                ),
                converter::convertToLetter,
                e -> new JdbcException("Failed to retrieve dead letters for sequence [" + sequenceId + "]", e),
                CLOSE_QUIETLY
        );
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends E>>> deadLetters() {
        List<String> sequenceIdentifiers = executeQuery(
                getConnection(),
                connection -> statementFactory.sequenceIdentifiersStatement(connection, processingGroup),
                listResults(resultSet -> resultSet.getString(1)),
                e -> new JdbcException("Failed to retrieve all sequence identifiers", e),
                CLOSE_QUIETLY
        );

        //noinspection DuplicatedCode
        return () -> {
            Iterator<String> sequenceIterator = sequenceIdentifiers.iterator();
            return new Iterator<Iterable<DeadLetter<? extends E>>>() {
                @Override
                public boolean hasNext() {
                    return sequenceIterator.hasNext();
                }

                @Override
                public Iterable<DeadLetter<? extends E>> next() {
                    String next = sequenceIterator.next();
                    return deadLetterSequence(next);
                }
            };
        };
    }

    @Override
    public boolean isFull(@Nonnull Object sequenceIdentifier) {
        String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
        long numberInSequence = sequenceSize(sequenceId);
        return numberInSequence > 0 ? numberInSequence >= maxSequenceSize : amountOfSequences() >= maxSequences;
    }

    @Override
    public long size() {
        return executeQuery(getConnection(),
                            connection -> statementFactory.sizeStatement(connection, processingGroup),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                            e -> new JdbcException("Failed to check the total number of dead letters", e),
                            CLOSE_QUIETLY);
    }

    @Override
    public long sequenceSize(@Nonnull Object sequenceIdentifier) {
        String sequenceId = toStringSequenceIdentifier(sequenceIdentifier);
        return executeQuery(getConnection(),
                            connection -> statementFactory.sequenceSizeStatement(
                                    connection, processingGroup, sequenceId
                            ),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                            e -> new JdbcException(
                                    "Failed to check the number of dead letters in sequence [" + sequenceId + "]", e
                            ),
                            CLOSE_QUIETLY
        );
    }

    @Override
    public long amountOfSequences() {
        return executeQuery(getConnection(),
                            connection -> statementFactory.amountOfSequencesStatement(connection, processingGroup),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                            e -> new JdbcException(
                                    "Failed to check the number of dead letter sequences in this queue", e
                            ),
                            CLOSE_QUIETLY);
    }

    @Override
    public boolean process(@Nonnull Predicate<DeadLetter<? extends E>> sequenceFilter,
                           @Nonnull Function<DeadLetter<? extends E>, EnqueueDecision<E>> processingTask) {
        logger.debug("Received a request to process matching dead letters.");

        Iterator<? extends JdbcDeadLetter<E>> iterator = findClaimableSequences(10);
        JdbcDeadLetter<E> claimedLetter = null;
        while (iterator.hasNext() && claimedLetter == null) {
            JdbcDeadLetter<E> next = iterator.next();
            if (sequenceFilter.test(next) && claimDeadLetter(next)) {
                claimedLetter = next;
            }
        }

        if (claimedLetter != null) {
            return processInitialAndSubsequent(claimedLetter, processingTask);
        }
        logger.debug("Received a request to process dead letters but there are no matching or claimable sequences.");
        return false;
    }

    @Override
    public boolean process(@Nonnull Function<DeadLetter<? extends E>, EnqueueDecision<E>> processingTask) {
        logger.debug("Received a request to process any dead letters.");
        Iterator<? extends JdbcDeadLetter<E>> iterator = findClaimableSequences(1);
        if (iterator.hasNext()) {
            JdbcDeadLetter<E> deadLetter = iterator.next();
            claimDeadLetter(deadLetter);
            return processInitialAndSubsequent(deadLetter, processingTask);
        }
        logger.debug("Received a request to process dead letters but there are no claimable sequences.");
        return false;
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
     * @return A list of first letters of each sequence.
     */
    private Iterator<? extends JdbcDeadLetter<E>> findClaimableSequences(int pageSize) {
        return new PagingJdbcIterable<>(
                pageSize,
                this::getConnection,
                transactionManager,
                (connection, firstResult, maxSize) -> statementFactory.claimableSequencesStatement(
                        connection, processingGroup, processingStartedLimit(), firstResult, maxSize
                ),
                converter::convertToLetter,
                e -> new JdbcException("Failed to find any claimable sequences for processing", e),
                CLOSE_QUIETLY
        ).iterator();
    }

    /**
     * Claims the provided {@code letter} in the database by setting the
     * {@link DeadLetterSchema#processingStartedColumn() processing started} property. Will check whether it was claimed
     * successfully and return an appropriate boolean result.
     *
     * @return Whether the letter was successfully claimed or not.
     */
    private boolean claimDeadLetter(JdbcDeadLetter<E> letter) {
        Instant processingStartedLimit = processingStartedLimit();
        return transactionManager.fetchInTransaction(() -> {
            Connection connection = getConnection();
            try {
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
            } finally {
                closeQuietly(connection);
            }
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
     * Processes the given {@code initialLetter} using the provided {@code processingTask}.
     * <p>
     * When processing is successful this operation will automatically process all messages in the same
     * {@link DeadLetterSchema#sequenceIdentifierColumn()}, {@link #evict(DeadLetter) evicting} letters that succeed and
     * stopping when the first one fails (and is {@link #requeue(DeadLetter, UnaryOperator) requeued}).
     * <p>
     * Will claim the next letter in the same sequence before removing the previous letter to prevent concurrency
     * issues. Note that we should not use paging on the results here, since letters can be added to the end of the
     * sequence before processing ends, and deletes would throw the ordering off.
     *
     * @param initialLetter  The dead letter to start processing.
     * @param processingTask The task to use to process the dead letter, providing a decision afterward.
     * @return Whether processing all letters in this sequence was successful.
     */
    private boolean processInitialAndSubsequent(JdbcDeadLetter<E> initialLetter,
                                                Function<DeadLetter<? extends E>, EnqueueDecision<E>> processingTask) {
        JdbcDeadLetter<E> current = initialLetter;
        while (current != null) {
            logger.info("Processing dead letter with identifier [{}] at index [{}]",
                        current.getIdentifier(), current.getIndex());
            EnqueueDecision<E> decision = processingTask.apply(current);
            if (!decision.shouldEnqueue()) {
                JdbcDeadLetter<E> previous = current;
                JdbcDeadLetter<E> next = findNext(previous.getSequenceIdentifier(), previous.getIndex());
                if (next != null) {
                    current = next;
                    claimDeadLetter(current);
                } else {
                    current = null;
                }
                evict(previous);
            } else {
                requeue(current,
                        letter -> decision.withDiagnostics(letter)
                                          .withCause(decision.enqueueCause().orElse(null)));
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the next dead letter matching the given {@code sequenceIdentifier} following the provided
     * {@code sequenceIndex}.
     *
     * @param sequenceIdentifier The identifier of the sequence to find the next letter for.
     * @param sequenceIndex      The index within the sequence to find the following letter for
     * @return The next letter to process.
     */
    private JdbcDeadLetter<E> findNext(String sequenceIdentifier, long sequenceIndex) {
        return transactionManager.fetchInTransaction(() -> executeQuery(
                getConnection(),
                connection -> statementFactory.nextLetterInSequenceStatement(
                        connection, processingGroup, sequenceIdentifier, sequenceIndex
                ),
                resultSet -> resultSet.next() ? converter.convertToLetter(resultSet) : null,
                e -> new JdbcException(
                        "Failed to find the next dead letter in sequence [" + sequenceIdentifier + "] for processing", e
                ),
                CLOSE_QUIETLY
        ));
    }

    @Override
    public void clear() {
        Connection connection = getConnection();
        try {
            executeUpdate(connection,
                          c -> statementFactory.clearStatement(c, processingGroup),
                          e -> new JdbcException("Failed to clear out all dead letters for "
                                                         + "processing group [" + processingGroup + "]", e));
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Converts the given {@code sequenceIdentifier} to a {@link String}.
     *
     * @return The given {@code sequenceIdentifier} to a {@link String}.
     */
    private String toStringSequenceIdentifier(Object sequenceIdentifier) {
        return sequenceIdentifier instanceof String
                ? (String) sequenceIdentifier
                : Integer.toString(sequenceIdentifier.hashCode());
    }

    /**
     * Builder class to instantiate an {@link JdbcSequencedDeadLetterQueue}.
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
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider}, and
     * {@link Builder#transactionManager(TransactionManager) TransactionManager} are hard requirements and should be
     * provided.
     * <p>
     * The {@link Builder#statementFactory(DeadLetterStatementFactory)} and
     * {@link Builder#converter(DeadLetterJdbcConverter)} are also hard requirements, but users can choose to either set
     * both explicitly or rely on the {@link DefaultDeadLetterStatementFactory} and
     * {@link DefaultDeadLetterJdbcConverter} constructed through the
     * {@link Builder#genericSerializer(Serializer) generic Serializer} and
     * {@link Builder#eventSerializer(Serializer) event Serializer}.
     *
     * @param <E> The type of {@link EventMessage} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<E extends EventMessage<?>> {

        private String processingGroup;
        private ConnectionProvider connectionProvider;
        private TransactionManager transactionManager;
        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private DeadLetterStatementFactory<E> statementFactory;
        private DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter;
        private Serializer genericSerializer;
        private Serializer eventSerializer;
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
        public Builder<E> processingGroup(@Nonnull String processingGroup) {
            assertNonEmpty(processingGroup, "Can not set processingGroup to an empty String.");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Sets the {@link ConnectionProvider} which provides access to a JDBC connection.
         *
         * @param connectionProvider a {@link ConnectionProvider} which provides access to a JDBC connection
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> connectionProvider(@Nonnull ConnectionProvider connectionProvider) {
            assertNonNull(connectionProvider, "ConnectionProvider may not be null");
            this.connectionProvider = connectionProvider;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transaction around fetching dead-letter data.
         *
         * @param transactionManager A {@link TransactionManager} used to manage transaction around fetching dead-letter
         *                           data.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> transactionManager(@Nonnull TransactionManager transactionManager) {
            assertNonNull(transactionManager, "The TransactionManager may not be null");
            this.transactionManager = transactionManager;
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
        public Builder<E> schema(@Nonnull DeadLetterSchema schema) {
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
         * configured {@link #schema(DeadLetterSchema) schema},
         * {@link #genericSerializer(Serializer) generic Serializer}, and
         * {@link #eventSerializer(Serializer) event Serializer} are used.
         *
         * @param statementFactory The {@link DeadLetterStatementFactory} used to construct all
         *                         {@link java.sql.PreparedStatement PreparedStatements} executed by this
         *                         {@link SequencedDeadLetterQueue}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> statementFactory(@Nonnull DeadLetterStatementFactory<E> statementFactory) {
            assertNonNull(statementFactory, "The DeadLetterStatementFactory may not be null");
            this.statementFactory = statementFactory;
            return this;
        }

        /**
         * Sets the {@link DeadLetterJdbcConverter} used to convert a {@link java.sql.ResultSet} into a
         * {@link JdbcDeadLetter} implementation. The {@code converter} is, for example, used to service the
         * {@link #deadLetters()} and {@link #deadLetterSequence(Object)} operations.
         * <p>
         * When the {@code converter} is not explicitly configured, this builder defaults to the
         * {@link DefaultDeadLetterJdbcConverter}. To construct the {@code DefaultDeadLetterJdbcConverter}, the
         * configured {@link #genericSerializer(Serializer) generic Serializer}, and
         * {@link #eventSerializer(Serializer) event Serializer} are used.
         *
         * @param converter The {@link DeadLetterJdbcConverter} used to convert a {@link java.sql.ResultSet} into a
         *                  {@link JdbcDeadLetter} implementation.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> converter(@Nonnull DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter) {
            assertNonNull(converter, "The DeadLetterJdbcConverter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the {@link org.axonframework.eventhandling.TrackingToken} (if
         * present) of the event in the {@link DeadLetter} when storing it to the database.
         * <p>
         * The {@code genericSerializer} will be used to construct the {@link DeadLetterStatementFactory} and/or
         * {@link DeadLetterJdbcConverter} when either of them are not explicitly configured.
         *
         * @param genericSerializer The serializer to use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> genericSerializer(@Nonnull Serializer genericSerializer) {
            assertNonNull(genericSerializer, "The generic serializer may not be null");
            this.genericSerializer = genericSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the events, metadata and diagnostics of the {@link DeadLetter}
         * when storing it to a database.
         * <p>
         * The {@code eventSerializer} will be used to construct the {@link DeadLetterStatementFactory} and/or
         * {@link DeadLetterJdbcConverter} when either of them are not explicitly configured.
         *
         * @param eventSerializer The serializer to use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> eventSerializer(@Nonnull Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event serializer may not be null");
            this.eventSerializer = eventSerializer;
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
         * complete. Other invocations of the {@link #process(Predicate, Function)} method will be unable to process a
         * sequence while the claim is active. The claim duration defaults to 30 seconds.
         * <p>
         * Claims are automatically released once the item is requeued. Thus, the claim time is a backup policy in case
         * of unforeseen trouble such as database connection issues.
         *
         * @param claimDuration The longest claim duration allowed.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> claimDuration(@Nonnull Duration claimDuration) {
            assertNonNull(claimDuration, "Claim duration can not be set to null.");
            this.claimDuration = claimDuration;
            return this;
        }

        /**
         * Modifies the page size used when retrieving a sequence of dead letters.
         * <p>
         * Used during the {@link #deadLetterSequence(Object)} and {@link #deadLetters()} operations. Defaults to a
         * {@code 100}.
         *
         * @param pageSize The page size used when retrieving a sequence of dead letters.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<E> pageSize(int pageSize) {
            assertStrictPositive(pageSize, "The page size  should be larger than 0.");
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
         * {@link #schema(DeadLetterSchema) schema}, {@link #genericSerializer(Serializer) generic Serializer}, and
         * {@link #eventSerializer(Serializer) event Serializer}.
         *
         * @return The {@link DeadLetterStatementFactory} as defined within this builder.
         */
        private DeadLetterStatementFactory<E> statementFactory() {
            return getOrDefault(statementFactory, DefaultDeadLetterStatementFactory.<E>builder()
                                                                                   .schema(schema)
                                                                                   .genericSerializer(genericSerializer)
                                                                                   .eventSerializer(eventSerializer)
                                                                                   .build());
        }

        /**
         * Retrieve the {@link DeadLetterJdbcConverter} for this {@link SequencedDeadLetterQueue}.
         * <p>
         * When not set explicitly through {@link #converter(DeadLetterJdbcConverter)} a
         * {@link DefaultDeadLetterJdbcConverter} is constructed based on the
         * {@link #genericSerializer(Serializer) generic Serializer}, and
         * {@link #eventSerializer(Serializer) event Serializer}.
         *
         * @return The {@link DeadLetterJdbcConverter} as defined within this builder.
         */
        private DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter() {
            return getOrDefault(converter, () -> DefaultDeadLetterJdbcConverter.<E>builder()
                                                                               .genericSerializer(genericSerializer)
                                                                               .eventSerializer(eventSerializer)
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
            assertNonNull(connectionProvider, "The ConnectionProvider is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
            if (statementFactory == null) {
                assertNonNull(
                        genericSerializer,
                        "The generic Serializer is a hard requirement when the DeadLetterStatementFactory is not provided"
                );
                assertNonNull(
                        eventSerializer,
                        "The event Serializer is a hard requirement when the DeadLetterStatementFactory is not provided"
                );
            }
            if (converter == null) {
                assertNonNull(
                        genericSerializer,
                        "The generic Serializer is a hard requirement when the DeadLetterJdbcConverter is not provided"
                );
                assertNonNull(
                        eventSerializer,
                        "The event Serializer is a hard requirement when the DeadLetterJdbcConverter is not provided"
                );
            }
        }
    }
}
