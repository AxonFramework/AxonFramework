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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueueOverflowException;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.NoSuchDeadLetterException;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.*;
import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdates;

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
 * @param <M>
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class JdbcSequencedDeadLetterQueue<M extends EventMessage<?>> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String processingGroup;
    private final int maxSequences;
    private final int maxSequenceSize;
    private final int queryPageSize;
    private final ConnectionProvider connectionProvider;
    private final DeadLetterSchema schema;
    private final TransactionManager transactionManager;
    private final Serializer serializer;
    private final Duration claimDuration;

    protected JdbcSequencedDeadLetterQueue(Builder<M> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.maxSequences = builder.maxSequences;
        this.maxSequenceSize = builder.maxSequenceSize;
        this.queryPageSize = builder.queryPageSize;
        this.connectionProvider = builder.connectionProvider;
        this.schema = builder.schema;
        this.transactionManager = builder.transactionManager;
        this.serializer = builder.serializer;
        this.claimDuration = builder.claimDuration;
    }

    /**
     * Instantiate a builder to construct a {@link JdbcSequencedDeadLetterQueue}.
     * <p>
     * The following defaults are set by the builder:
     * <ul>
     *     <li>The {@link Builder#maxSequences(int) maximum amount of sequences} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#maxSequenceSize(int) maximum sequence size} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#queryPageSize(int) query page size} defaults to {@code 100}.</li>
     *     <li>The {@link Builder#schema(DeadLetterSchema) table's schema} defaults to a {@link DeadLetterSchema#defaultSchema()}.</li>
     *     <li>The {@link Builder#claimDuration(Duration) claim duration} defaults to 30 seconds.</li>
     * </ul>
     * <p>
     * The {@link Builder#processingGroup(String) processing group},
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider},
     * {@link Builder#transactionManager(TransactionManager) TransactionManager} and
     * {@link Builder#serializer(Serializer) Serializer} are hard requirements and should be provided.
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
    public void enqueue(@Nonnull Object sequenceIdentifier, @Nonnull DeadLetter<? extends M> letter)
            throws DeadLetterQueueOverflowException {

    }

    @Override
    public void evict(DeadLetter<? extends M> letter) {

    }

    @Override
    public void requeue(@Nonnull DeadLetter<? extends M> letter,
                        @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater)
            throws NoSuchDeadLetterException {

    }

    @Override
    public boolean contains(@Nonnull Object sequenceIdentifier) {
        return false;
    }

    @Override
    public Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        return null;
    }

    @Override
    public Iterable<Iterable<DeadLetter<? extends M>>> deadLetters() {
        return null;
    }

    @Override
    public boolean isFull(@Nonnull Object sequenceIdentifier) {
        return false;
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public long sequenceSize(@Nonnull Object sequenceIdentifier) {
        return 0;
    }

    @Override
    public long amountOfSequences() {
        return 0;
    }

    @Override
    public boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                           @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        return false;
    }

    @Override
    public void clear() {

    }

    /**
     * Builder class to instantiate an {@link JdbcSequencedDeadLetterQueue}.
     * <p>
     * The following defaults are set by the builder:
     * <ul>
     *     <li>The {@link Builder#maxSequences(int) maximum amount of sequences} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#maxSequenceSize(int) maximum sequence size} defaults to {@code 1024}.</li>
     *     <li>The {@link Builder#queryPageSize(int) query page size} defaults to {@code 100}.</li>
     *     <li>The {@link Builder#schema(DeadLetterSchema) table's schema} defaults to a {@link DeadLetterSchema#defaultSchema()}.</li>
     *     <li>The {@link Builder#claimDuration(Duration) claim duration} defaults to 30 seconds.</li>
     * </ul>
     * <p>
     * The {@link Builder#processingGroup(String) processing group},
     * {@link Builder#connectionProvider(ConnectionProvider) ConnectionProvider},
     * {@link Builder#transactionManager(TransactionManager) TransactionManager} and
     * {@link Builder#serializer(Serializer) Serializer} are hard requirements and should be provided.
     *
     * @param <M> The type of {@link Message} maintained in the {@link DeadLetter dead letter} of this
     *            {@link SequencedDeadLetterQueue}.
     */
    public static class Builder<M extends EventMessage<?>> {

        private String processingGroup;
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private int queryPageSize = 100;
        private ConnectionProvider connectionProvider;
        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private TransactionManager transactionManager;
        private Serializer serializer;
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
         * @param queryPageSize The page size
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> queryPageSize(int queryPageSize) {
            assertStrictPositive(queryPageSize, "The query page size must be at least 1.");
            this.queryPageSize = queryPageSize;
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
         * Sets the {@link Serializer} to deserialize the events, metadata and diagnostics of the {@link DeadLetter}
         * when storing it to a database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> serializer(Serializer serializer) {
            assertNonNull(serializer, "The serializer may not be null");
            this.serializer = serializer;
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
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
        }
    }
}
