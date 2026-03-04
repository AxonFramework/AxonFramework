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
import org.axonframework.common.DateTimeUtils;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Default implementation of the {@link DeadLetterStatementFactory} used by the {@link JdbcSequencedDeadLetterQueue}.
 * Constructs {@link PreparedStatement PreparedStatements} that are compatible with most databases.
 * <p>
 * This factory expects a {@link DeadLetterSchema} to base the table and columns names used for <b>all</b>
 * {@code PreparedStatements}. Furthermore, it uses the configurable {@code genericConverter} to convert
 * {@link TrackingToken TrackingTokens} and diagnostics. Lastly, this factory uses the {@code eventConverter} to convert
 * the {@link EventMessage#payload() event payload} and {@link EventMessage#metadata() Metadata} of any
 * {@code DeadLetter}.
 * <p>
 * This factory and the {@link DeadLetterJdbcConverter} must use the same {@link Converter} and
 * {@link EventConverter} and {@code DeadLetterSchema} for the applicable fields.
 *
 * @param <E> An implementation of {@link EventMessage} within the {@link DeadLetter} this factory constructs
 *            {@link PreparedStatement PreparedStatements} for.
 * @author Steven van Beelen
 * @since 4.8.0
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class DefaultDeadLetterStatementFactory<E extends EventMessage> implements DeadLetterStatementFactory<E> {

    private final DeadLetterSchema schema;
    private final Converter genericConverter;
    private final EventConverter eventConverter;

    /**
     * Instantiate a default {@link DeadLetterStatementFactory} based on the given {@code builder}.
     * <p>
     * Will validate whether the {@link Builder#genericConverter(Converter) generic Converter} and
     * {@link Builder#eventConverter(EventConverter) EventConverter} are set. If for either this is not the case an
     * {@link AxonConfigurationException} is thrown.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DefaultDeadLetterStatementFactory} instance.
     */
    protected DefaultDeadLetterStatementFactory(Builder<E> builder) {
        builder.validate();
        this.schema = builder.schema;
        this.genericConverter = builder.genericConverter;
        this.eventConverter = builder.eventConverter;
    }

    /**
     * Instantiate a builder to construct a {@link DefaultDeadLetterStatementFactory}.
     * <p>
     * The {@link Builder#schema(DeadLetterSchema) schema} is defaulted to a {@link DeadLetterSchema#defaultSchema()}.
     * The {@link Builder#genericConverter(Converter) generic Converter} and
     * {@link Builder#eventConverter(EventConverter) EventConverter} are hard requirements and should be provided.
     *
     * @param <E> An implementation of {@link EventMessage} within the {@link DeadLetter} this factory constructs
     *            {@link PreparedStatement PreparedStatements} for.
     * @return A builder that can construct a {@link DefaultDeadLetterStatementFactory}.
     */
    public static <E extends EventMessage> Builder<E> builder() {
        return new Builder<>();
    }

    @Override
    public PreparedStatement enqueueStatement(@NonNull Connection connection,
                                              @NonNull String processingGroup,
                                              @NonNull String sequenceIdentifier,
                                              @NonNull DeadLetter<? extends E> letter,
                                              long sequenceIndex,
                                              @Nullable ProcessingContext context) throws SQLException {
        String sql = "INSERT INTO " + schema.deadLetterTable() + " "
                + "(" + schema.deadLetterFields() + ") "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);
        AtomicInteger fieldIndex = new AtomicInteger(1);
        E eventMessage = letter.message();

        Context effectiveContext = context != null ? context : Context.empty();

        setIdFields(statement, fieldIndex, processingGroup, sequenceIdentifier, sequenceIndex);
        setEventFields(statement, fieldIndex, eventMessage);
        setAggregateBasedEventFields(statement, fieldIndex, effectiveContext);
        setTrackingTokenFields(statement, fieldIndex, effectiveContext);
        setDeadLetterFields(statement, fieldIndex, letter);

        return statement;
    }

    private void setIdFields(PreparedStatement statement,
                             AtomicInteger fieldIndex,
                             String processingGroup,
                             String sequenceIdentifier,
                             long sequenceIndex) throws SQLException {
        String deadLetterId = IdentifierFactory.getInstance().generateIdentifier();
        statement.setString(fieldIndex.getAndIncrement(), deadLetterId);
        statement.setString(fieldIndex.getAndIncrement(), processingGroup);
        statement.setString(fieldIndex.getAndIncrement(), sequenceIdentifier);
        statement.setLong(fieldIndex.getAndIncrement(), sequenceIndex);
    }

    private void setEventFields(PreparedStatement statement,
                                AtomicInteger fieldIndex,
                                E eventMessage) throws SQLException {
        byte[] serializedPayload = eventConverter.convert(eventMessage.payload(), byte[].class);
        byte[] serializedMetadata = eventConverter.convert(eventMessage.metadata(), byte[].class);
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getClass().getName());
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.identifier());
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.type().toString());
        statement.setString(fieldIndex.getAndIncrement(), DateTimeUtils.formatInstant(eventMessage.timestamp()));
        statement.setBytes(fieldIndex.getAndIncrement(), serializedPayload);
        statement.setBytes(fieldIndex.getAndIncrement(), serializedMetadata);
    }

    private void setAggregateBasedEventFields(PreparedStatement statement,
                                              AtomicInteger fieldIndex,
                                              Context context) throws SQLException {
        statement.setString(fieldIndex.getAndIncrement(), context.getResource(LegacyResources.AGGREGATE_TYPE_KEY));
        statement.setString(fieldIndex.getAndIncrement(),
                            context.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY));
        Long sequenceNumber = context.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY);
        if (sequenceNumber != null) {
            statement.setLong(fieldIndex.getAndIncrement(), sequenceNumber);
        } else {
            statement.setNull(fieldIndex.getAndIncrement(), java.sql.Types.BIGINT);
        }
    }

    private void setTrackingTokenFields(PreparedStatement statement,
                                        AtomicInteger fieldIndex,
                                        Context context) throws SQLException {
        TrackingToken token = context.getResource(TrackingToken.RESOURCE_KEY);
        if (token != null) {
            byte[] serializedToken = genericConverter.convert(token, byte[].class);
            statement.setString(fieldIndex.getAndIncrement(), token.getClass().getName());
            statement.setBytes(fieldIndex.getAndIncrement(), serializedToken);
        } else {
            statement.setString(fieldIndex.getAndIncrement(), null);
            statement.setBytes(fieldIndex.getAndIncrement(), null);
        }
    }

    private void setDeadLetterFields(PreparedStatement statement,
                                     AtomicInteger fieldIndex,
                                     DeadLetter<? extends E> letter) throws SQLException {
        statement.setString(fieldIndex.getAndIncrement(), DateTimeUtils.formatInstant(letter.enqueuedAt()));
        statement.setString(fieldIndex.getAndIncrement(), DateTimeUtils.formatInstant(letter.lastTouched()));
        Optional<Cause> cause = letter.cause();
        statement.setString(fieldIndex.getAndIncrement(), cause.map(Cause::type).orElse(null));
        statement.setString(fieldIndex.getAndIncrement(), cause.map(Cause::message).orElse(null));
        byte[] serializedDiagnostics = genericConverter.convert(letter.diagnostics(), byte[].class);
        statement.setBytes(fieldIndex.getAndIncrement(), serializedDiagnostics);
    }

    @Override
    public PreparedStatement maxIndexStatement(@NonNull Connection connection,
                                               @NonNull String processingGroup,
                                               @NonNull String sequenceId) throws SQLException {
        String sql = "SELECT MAX(" + schema.sequenceIndexColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement evictStatement(@NonNull Connection connection,
                                            @NonNull String identifier) throws SQLException {
        String sql = "DELETE "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.deadLetterIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, identifier);
        return statement;
    }

    @Override
    public PreparedStatement requeueStatement(@NonNull Connection connection,
                                              @NonNull String letterIdentifier,
                                              Cause cause,
                                              @NonNull Instant lastTouched,
                                              Metadata diagnostics) throws SQLException {
        String sql = "UPDATE " + schema.deadLetterTable() + " SET "
                + schema.causeTypeColumn() + "=?, "
                + schema.causeMessageColumn() + "=?, "
                + schema.lastTouchedColumn() + "=?, "
                + schema.diagnosticsColumn() + "=?, "
                + schema.processingStartedColumn() + "=NULL "
                + "WHERE " + schema.deadLetterIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, getOrDefault(cause, Cause::type, null));
        statement.setString(2, getOrDefault(cause, Cause::message, null));
        statement.setString(3, DateTimeUtils.formatInstant(lastTouched));
        byte[] serializedDiagnostics = genericConverter.convert(diagnostics, byte[].class);
        statement.setBytes(4, serializedDiagnostics);
        statement.setString(5, letterIdentifier);
        return statement;
    }

    @Override
    public PreparedStatement containsStatement(@NonNull Connection connection,
                                               @NonNull String processingGroup,
                                               @NonNull String sequenceId) throws SQLException {
        return sequenceSizeStatement(connection, processingGroup, sequenceId);
    }

    @Override
    public PreparedStatement letterSequenceStatement(@NonNull Connection connection,
                                                     @NonNull String processingGroup,
                                                     @NonNull String sequenceId,
                                                     int offset,
                                                     int maxSize) throws SQLException {
        String sql = "SELECT * "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=? "
                + "AND " + schema.sequenceIndexColumn() + ">=? "
                + "ORDER BY " + schema.sequenceIndexColumn() + " "
                + "LIMIT ?";

        PreparedStatement statement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        statement.setInt(3, offset);
        statement.setInt(4, maxSize);
        return statement;
    }

    @Override
    public PreparedStatement sequenceIdentifiersStatement(@NonNull Connection connection,
                                                          @NonNull String processingGroup) throws SQLException {
        String sql = "SELECT dl." + schema.sequenceIdentifierColumn() + " "
                + "FROM " + schema.deadLetterTable() + " dl "
                + "WHERE dl." + schema.processingGroupColumn() + "=? "
                + "AND dl." + schema.sequenceIndexColumn() + "=("
                + "SELECT MIN(dl2." + schema.sequenceIndexColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " dl2 "
                + "WHERE dl2." + schema.processingGroupColumn() + "=dl." + schema.processingGroupColumn() + " "
                + "AND dl2." + schema.sequenceIdentifierColumn() + "=dl." + schema.sequenceIdentifierColumn() + ") "
                + "ORDER BY dl." + schema.lastTouchedColumn() + " "
                + "ASC";

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement sizeStatement(@NonNull Connection connection,
                                           @NonNull String processingGroup) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement sequenceSizeStatement(@NonNull Connection connection,
                                                   @NonNull String processingGroup,
                                                   @NonNull String sequenceId) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement amountOfSequencesStatement(@NonNull Connection connection,
                                                        @NonNull String processingGroup) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT " + schema.sequenceIdentifierColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement claimableSequencesStatement(@NonNull Connection connection,
                                                         @NonNull String processingGroup,
                                                         @NonNull Instant processingStartedLimit,
                                                         int offset,
                                                         int maxSize) throws SQLException {
        String sql = "SELECT * "
                + "FROM " + schema.deadLetterTable() + " dl "
                + "WHERE dl." + schema.processingGroupColumn() + "=? "
                + "AND dl." + schema.sequenceIndexColumn() + ">=? "
                + "AND dl." + schema.sequenceIndexColumn() + "="
                + "("
                + "SELECT MIN(dl2." + schema.sequenceIndexColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " dl2 "
                + "WHERE dl2." + schema.processingGroupColumn() + "=dl." + schema.processingGroupColumn() + " "
                + "AND dl2." + schema.sequenceIdentifierColumn() + "=dl." + schema.sequenceIdentifierColumn()
                + ") "
                + "AND ("
                + "dl." + schema.processingStartedColumn() + " IS NULL "
                + "OR dl." + schema.processingStartedColumn() + "<?"
                + ") "
                + "ORDER BY dl." + schema.lastTouchedColumn() + " "
                + "ASC "
                + "LIMIT ?";

        PreparedStatement statement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        statement.setString(1, processingGroup);
        statement.setInt(2, offset);
        statement.setString(3, DateTimeUtils.formatInstant(processingStartedLimit));
        statement.setInt(4, maxSize);
        return statement;
    }

    @Override
    public PreparedStatement claimStatement(@NonNull Connection connection,
                                            @NonNull String identifier,
                                            @NonNull Instant current,
                                            @NonNull Instant processingStartedLimit) throws SQLException {
        String sql = "UPDATE " + schema.deadLetterTable() + " SET "
                + schema.processingStartedColumn() + "=? "
                + "WHERE " + schema.deadLetterIdentifierColumn() + "=? "
                + "AND ("
                + schema.processingStartedColumn() + " IS NULL "
                + "OR " + schema.processingStartedColumn() + "<?"
                + ")";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, DateTimeUtils.formatInstant(current));
        statement.setString(2, identifier);
        statement.setString(3, DateTimeUtils.formatInstant(processingStartedLimit));
        return statement;
    }

    @Override
    public PreparedStatement nextLetterInSequenceStatement(@NonNull Connection connection,
                                                           @NonNull String processingGroup,
                                                           @NonNull String sequenceIdentifier,
                                                           long sequenceIndex) throws SQLException {
        String sql = "SELECT * "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=? "
                + "AND " + schema.sequenceIndexColumn() + ">? "
                + "ORDER BY " + schema.sequenceIndexColumn() + " "
                + "ASC "
                + "LIMIT 1";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceIdentifier);
        statement.setLong(3, sequenceIndex);
        return statement;
    }

    @Override
    public PreparedStatement clearStatement(@NonNull Connection connection,
                                            @NonNull String processingGroup) throws SQLException {
        String sql = "DELETE "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    /**
     * Builder class to instantiate a {@link DefaultDeadLetterStatementFactory}.
     * <p>
     * The {@link Builder#schema(DeadLetterSchema) schema} is defaulted to a {@link DeadLetterSchema#defaultSchema()}.
     * The {@link Builder#genericConverter(Converter) generic Converter} and
     * {@link Builder#eventConverter(EventConverter) EventConverter} are hard requirements and should be provided.
     *
     * @param <E> An implementation of {@link EventMessage} within the {@link DeadLetter} this factory constructs
     *            {@link PreparedStatement PreparedStatements} for.
     */
    protected static class Builder<E extends EventMessage> {

        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private Converter genericConverter;
        private EventConverter eventConverter;

        /**
         * Sets the given {@code schema} used to define the table and column names used when constructing <b>all</b>
         * {@link PreparedStatement PreparedStatements}. Defaults to a {@link DeadLetterSchema#defaultSchema()}.
         *
         * @param schema The {@link DeadLetterSchema} used to define the table and column names used when constructing
         *               <b>all</b> {@link PreparedStatement PreparedStatements}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> schema(DeadLetterSchema schema) {
            assertNonNull(schema, "DeadLetterSchema may not be null");
            this.schema = schema;
            return this;
        }

        /**
         * Sets the {@link Converter} to convert the {@link TrackingToken} and diagnostics of the {@link DeadLetter}
         * when storing it to the database.
         *
         * @param genericConverter The converter to use for {@link TrackingToken TrackingTokens} and diagnostics.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> genericConverter(Converter genericConverter) {
            assertNonNull(genericConverter, "The generic Converter may not be null");
            this.genericConverter = genericConverter;
            return this;
        }

        /**
         * Sets the {@link EventConverter} to convert the {@link EventMessage#payload() event payload} and
         * {@link EventMessage#metadata() Metadata} of the {@link DeadLetter} when storing it to a database.
         *
         * @param eventConverter The event converter to use for {@link EventMessage#payload() event payload}s and
         *                       {@link EventMessage#metadata() Metadata} instances.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> eventConverter(EventConverter eventConverter) {
            assertNonNull(eventConverter, "The EventConverter may not be null");
            this.eventConverter = eventConverter;
            return this;
        }

        /**
         * Initializes a {@link DefaultDeadLetterStatementFactory} as specified through this Builder.
         *
         * @return A {@link DefaultDeadLetterStatementFactory} as specified through this Builder.
         */
        public DefaultDeadLetterStatementFactory<E> build() {
            return new DefaultDeadLetterStatementFactory<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(genericConverter, "The generic Converter is a hard requirement and should be provided");
            assertNonNull(eventConverter, "The EventConverter is a hard requirement and should be provided");
        }
    }
}
