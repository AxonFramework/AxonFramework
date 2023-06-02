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
import org.axonframework.messaging.Message;
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
import static org.axonframework.common.jdbc.JdbcUtils.*;

/**
 * JDBC-backed implementation of the {@link SequencedDeadLetterQueue}, used for storing dead letters containing
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
 * @param <E> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class JdbcSequencedDeadLetterQueue<E extends EventMessage<?>> implements SequencedDeadLetterQueue<E> {

    // TODO make this configurable, or use something like the PersistenceExceptionResolver
    private static Function<SQLException, RuntimeException> handleException() {
        return e -> {
            logger.warn("SHEIZE", e);
            return new RuntimeException(e);
        };
    }

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final boolean CLOSE_QUIETLY = true;

    private final String processingGroup;
    private final int maxSequences;
    private final int maxSequenceSize;
    private final int pageSize;
    private final ConnectionProvider connectionProvider;
    private final DeadLetterSchema schema;
    private final TransactionManager transactionManager;
    private final Serializer genericSerializer;
    private final Serializer eventSerializer;
    private final Duration claimDuration;

    // TODO get a feel whether this thing makes sense in it's current form
    private final DeadLetterStatementFactory<E> statementFactory;
    private final DeadLetterJdbcConverter<E, ? extends JdbcDeadLetter<E>> converter;

    protected JdbcSequencedDeadLetterQueue(Builder<E> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
        this.pageSize = builder.pageSize;
        this.connectionProvider = builder.connectionProvider;
        this.schema = builder.schema;
        this.transactionManager = builder.transactionManager;
        this.genericSerializer = builder.genericSerializer;
        this.eventSerializer = builder.eventSerializer;
        this.claimDuration = builder.claimDuration;

        // TODO get a feel whether this thing makes sense in it's current form
        this.statementFactory = DefaultDeadLetterStatementFactory.<E>builder()
                                                                 .schema(builder.schema)
                                                                 .genericSerializer(builder.genericSerializer)
                                                                 .eventSerializer(builder.eventSerializer)
                                                                 .build();
        this.converter = SimpleDeadLetterJdbcConverter.<E>builder()
                                                      .genericSerializer(builder.genericSerializer)
                                                      .eventSerializer(builder.eventSerializer)
                                                      .build();
    }

    /**
     * Instantiate a builder to construct a {@link JdbcSequencedDeadLetterQueue}.
     * <p>
     * The following defaults are set by the builder:
     * <ul>
     *     <li>The {@link Builder#maxSequences(int) maximum amount of sequences} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#maxSequenceSize(int) maximum sequence size} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#pageSize(int) query page size} defaults to {@code 100}.</li>
     *     <li>The {@link Builder#schema(DeadLetterSchema) table's schema} defaults to a {@link DeadLetterSchema#defaultSchema()}.</li>
     *     <li>The {@link Builder#claimDuration(Duration) claim duration} defaults to 30 seconds.</li>
     * </ul>
     * <p>
     * The {@link Builder#processingGroup(String) processing group},
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider},
     * {@link Builder#transactionManager(TransactionManager) TransactionManager},
     * {@link Builder#genericSerializer(Serializer) generic Serializer}, and the
     * {@link Builder#eventSerializer(Serializer) event Serializer} are hard requirements and should be provided.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     * @return A Builder that can construct an {@link JdbcSequencedDeadLetterQueue}.
     */
    public static <M extends EventMessage<?>> Builder<M> builder() {
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
                          handleException());
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
                handleException()
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
                                                handleException());
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
                        handleException()
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
                            handleException(),
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
                handleException(),
                CLOSE_QUIETLY
        );
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends E>>> deadLetters() {
        List<String> sequenceIdentifiers = executeQuery(
                getConnection(),
                connection -> statementFactory.sequenceIdentifiersStatement(connection, processingGroup),
                listResults(resultSet -> resultSet.getString(1)),
                handleException(),
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
                            handleException(),
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
                            handleException(),
                            CLOSE_QUIETLY
        );
    }

    @Override
    public long amountOfSequences() {
        return executeQuery(getConnection(),
                            connection -> statementFactory.amountOfSequencesStatement(connection, processingGroup),
                            resultSet -> nextAndExtract(resultSet, 1, Long.class, 0L),
                            handleException(),
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
     * the first letters have the lowest {@link JdbcDeadLetter#getIndex() index} within their sequence.
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
                handleException(), CLOSE_QUIETLY
        ).iterator();
    }

    /**
     * Claims the provided {@code letter} in the database by setting the {@code processingStarted} property. Will check
     * whether it was claimed successfully and return an appropriate boolean result.
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
                        handleException()
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
     * Determines the time the {@code processingStarted} value a dead letter entry at least needs to have to stay
     * claimed. The returned limit is based on the difference between the {@link GenericDeadLetter#clock} and the
     * configurable {@link Builder#claimDuration claim duration}.
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
     * {@link JdbcDeadLetter#getSequenceIdentifier()}, {@link #evict(DeadLetter) evicting} letters that succeed and
     * stopping when the first one fails (and is {@link #requeue(DeadLetter, UnaryOperator) requeued}).
     * <p>
     * Will claim the next letter in the same sequence before removing the previous letter to prevent concurrency
     * issues. Note that we should not use paging on the results here, since letters can be added to the end of the
     * sequence before processing ends, and deletes would throw the ordering off.
     *
     * @param initialLetter  The dead letter to start processing.
     * @param processingTask The task to use to process the dead letter, providing a decision afterwards.
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
                handleException(),
                CLOSE_QUIETLY
        ));
    }

    @Override
    public void clear() {
        Connection connection = getConnection();
        try {
            executeUpdate(connection, c -> statementFactory.clearStatement(c, processingGroup), handleException());
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
     *     <li>The {@link Builder#maxSequences(int) maximum amount of sequences} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#maxSequenceSize(int) maximum sequence size} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#pageSize(int) page size} defaults to {@code 100}.</li>
     *     <li>The {@link Builder#schema(DeadLetterSchema) table's schema} defaults to a {@link DeadLetterSchema#defaultSchema()}.</li>
     *     <li>The {@link Builder#claimDuration(Duration) claim duration} defaults to 30 seconds.</li>
     * </ul>
     * <p>
     * The {@link Builder#processingGroup(String) processing group},
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider},
     * {@link Builder#transactionManager(TransactionManager) TransactionManager},
     * {@link Builder#genericSerializer(Serializer) generic Serializer}, and the
     * {@link Builder#eventSerializer(Serializer) event Serializer} are hard requirements and should be provided.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<M extends EventMessage<?>> {

        private String processingGroup;
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private int pageSize = 100;
        private ConnectionProvider connectionProvider;
        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private TransactionManager transactionManager;
        private Serializer genericSerializer;
        private Serializer eventSerializer;
        private Duration claimDuration = Duration.ofSeconds(30);

        /**
         * Sets the processing group, which is used for storing and quering which event processor the deadlettered item
         * belonged to.
         *
         * @param processingGroup The processing group of this {@link SequencedDeadLetterQueue}.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> processingGroup(String processingGroup) {
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
        public Builder<M> maxSequences(int maxSequences) {
            assertStrictPositive(maxSequences, "The maximum number of sequences should be larger or equal to 0");
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
        public Builder<M> maxSequenceSize(int maxSequenceSize) {
            assertStrictPositive(maxSequenceSize,
                                 "The maximum number of entries in a sequence should be larger or equal to 128");
            this.maxSequenceSize = maxSequenceSize;
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
        public Builder<M> claimDuration(Duration claimDuration) {
            assertNonNull(claimDuration, "Claim duration can not be set to null.");
            this.claimDuration = claimDuration;
            return this;
        }

        /**
         * Modifies the page size used when retrieving a sequence of dead letters. Defaults to {@code 100} items in a
         * page.
         *
         * @param fetchSize The page size
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> pageSize(int fetchSize) {
            assertStrictPositive(fetchSize, "The fetch size must be at least 1.");
            this.pageSize = fetchSize;
            return this;
        }

        /**
         * @param connectionProvider
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> connectionProvider(ConnectionProvider connectionProvider) {
            assertNonNull(connectionProvider, "ConnectionProvider may not be null");
            this.connectionProvider = connectionProvider;
            return this;
        }

        /**
         * @param schema
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> schema(DeadLetterSchema schema) {
            assertNonNull(schema, "DeadLetterSchema may not be null");
            this.schema = schema;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transaction around fetching event data. Required by
         * certain databases for reading blob data.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transaction around fetching event data
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the {@link org.axonframework.eventhandling.TrackingToken} (if
         * present) of the event in the {@link DeadLetter} when storing it to the database.
         *
         * @param genericSerializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> genericSerializer(Serializer genericSerializer) {

            assertNonNull(genericSerializer, "The generic serializer may not be null");
            this.genericSerializer = genericSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the events, metadata and diagnostics of the {@link DeadLetter}
         * when storing it to a database.
         *
         * @param eventSerializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event serializer may not be null");
            this.eventSerializer = eventSerializer;
            return this;
        }

        /**
         * Initializes a {@link JdbcSequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link JdbcSequencedDeadLetterQueue} as specified through this Builder.
         */
        public JdbcSequencedDeadLetterQueue<M> build() {
            return new JdbcSequencedDeadLetterQueue<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonEmpty(processingGroup, "The processingGroup is a hard requirement and should be non-empty");
            assertNonNull(connectionProvider, "The ConnectionProvider is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
            assertNonNull(genericSerializer, "The generic Serializer is a hard requirement and should be provided");
            assertNonNull(eventSerializer, "The event Serializer is a hard requirement and should be provided");
        }
    }
}
