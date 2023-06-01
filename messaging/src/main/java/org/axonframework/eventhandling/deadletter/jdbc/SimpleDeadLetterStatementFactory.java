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

package org.axonframework.eventhandling.deadletter.jdbc;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Steven van Beelen
 * @since 4.8.0
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class SimpleDeadLetterStatementFactory<M extends EventMessage<?>> implements DeadLetterStatementFactory<M> {

    private static final String COMMA = ", ";

    private final String processingGroup;
    private final DeadLetterSchema schema;
    private final Serializer genericSerializer;
    private final Serializer eventSerializer;

    protected SimpleDeadLetterStatementFactory(Builder<M> builder) {
        builder.validate();
        this.processingGroup = builder.processingGroup;
        this.schema = builder.schema;
        this.genericSerializer = builder.genericSerializer;
        this.eventSerializer = builder.eventSerializer;
    }

    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public PreparedStatement enqueueStatement(@Nonnull Connection connection, @Nonnull String sequenceIdentifier,
                                              @Nonnull DeadLetter<? extends M> letter,
                                              long sequenceIndex) throws SQLException {
        String enqueueSql = enqueueSql(letter);

        M eventMessage = letter.message();
        boolean isDomainEvent = eventMessage instanceof DomainEventMessage;
        boolean isTrackedEvent = eventMessage instanceof TrackedEventMessage;
        PreparedStatement statement = connection.prepareStatement(enqueueSql);
        AtomicInteger fieldIndex = new AtomicInteger(1);

        setIdFields(statement, fieldIndex, sequenceIdentifier, sequenceIndex);
        setEventFields(statement, fieldIndex, eventMessage);
        if (isDomainEvent) {
            setDomainEventFields(statement, fieldIndex, (DomainEventMessage<?>) eventMessage);
        }
        if (isTrackedEvent) {
            setTrackedEventFields(statement, fieldIndex, (TrackedEventMessage<?>) eventMessage);
        }
        setDeadLetterFields(statement, fieldIndex, letter);

        return statement;
    }

    private String enqueueSql(DeadLetter<? extends M> letter) {
        M eventMessage = letter.message();
        boolean isDomainEvent = eventMessage instanceof DomainEventMessage;
        boolean isTrackedEvent = eventMessage instanceof TrackedEventMessage;
        boolean hasCause = letter.cause().isPresent();

        int fieldCount = 14;
        StringBuilder enqueueSqlBuilder =
                new StringBuilder().append("INSERT INTO ")
                                   .append(schema.deadLetterTable())
                                   .append(" (")
                                   .append(schema.deadLetterIdColumn())
                                   .append(COMMA)
                                   .append(schema.processingGroupColumn())
                                   .append(COMMA)
                                   .append(schema.sequenceIdentifierColumn())
                                   .append(COMMA)
                                   .append(schema.sequenceIndexColumn())
                                   .append(COMMA)
                                   .append(schema.messageTypeColumn())
                                   .append(COMMA)
                                   .append(schema.eventIdentifierColumn())
                                   .append(COMMA)
                                   .append(schema.timeStampColumn())
                                   .append(COMMA)
                                   .append(schema.payloadTypeColumn())
                                   .append(COMMA)
                                   .append(schema.payloadRevisionColumn())
                                   .append(COMMA)
                                   .append(schema.payloadColumn())
                                   .append(COMMA)
                                   .append(schema.metaDataColumn())
                                   .append(COMMA);
        if (isDomainEvent) {
            enqueueSqlBuilder.append(schema.aggregateTypeColumn())
                             .append(COMMA)
                             .append(schema.aggregateIdentifierColumn())
                             .append(COMMA)
                             .append(schema.sequenceNumberColumn())
                             .append(COMMA);
            fieldCount += 3;
        }
        if (isTrackedEvent) {
            enqueueSqlBuilder.append(schema.tokenTypeColumn())
                             .append(COMMA).append(schema.tokenColumn())
                             .append(COMMA);
            fieldCount += 2;
        }
        enqueueSqlBuilder.append(schema.enqueuedAtColumn())
                         .append(COMMA)
                         .append(schema.lastTouchedColumn())
                         .append(COMMA);
        if (hasCause) {
            enqueueSqlBuilder.append(schema.causeTypeColumn())
                             .append(COMMA)
                             .append(schema.causeMessageColumn())
                             .append(COMMA);
            fieldCount += 2;
        }
        enqueueSqlBuilder.append(schema.diagnosticsColumn())
                         .append(") ")
                         .append("VALUES(");
        for (int fieldNumber = 0; fieldNumber < fieldCount; fieldNumber++) {
            enqueueSqlBuilder.append("?");
            if (fieldCount - 1 != fieldNumber) {
                enqueueSqlBuilder.append(COMMA);
            }
        }
        enqueueSqlBuilder.append(")");

        return enqueueSqlBuilder.toString();
    }

    private void setIdFields(PreparedStatement statement,
                             AtomicInteger fieldIndex,
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
                                M eventMessage) throws SQLException {
        SerializedObject<byte[]> serializedPayload =
                eventSerializer.serialize(eventMessage.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetaData =
                eventSerializer.serialize(eventMessage.getMetaData(), byte[].class);
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getClass().getName());
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getIdentifier());
        // TODO Timestamp converter!
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getTimestamp().toString());
        statement.setString(fieldIndex.getAndIncrement(), serializedPayload.getType().getName());
        statement.setString(fieldIndex.getAndIncrement(), serializedPayload.getType().getRevision());
        statement.setBytes(fieldIndex.getAndIncrement(), serializedPayload.getData());
        statement.setBytes(fieldIndex.getAndIncrement(), serializedMetaData.getData());
    }

    private void setDomainEventFields(PreparedStatement statement,
                                      AtomicInteger fieldIndex,
                                      DomainEventMessage<?> eventMessage) throws SQLException {
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getType());
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getAggregateIdentifier());
        statement.setLong(fieldIndex.getAndIncrement(), eventMessage.getSequenceNumber());
    }

    private void setTrackedEventFields(PreparedStatement statement,
                                       AtomicInteger fieldIndex,
                                       TrackedEventMessage<?> eventMessage) throws SQLException {
        TrackingToken token = eventMessage.trackingToken();
        SerializedObject<byte[]> serializedToken = genericSerializer.serialize(token, byte[].class);
        statement.setString(fieldIndex.getAndIncrement(), serializedToken.getType().getName());
        statement.setBytes(fieldIndex.getAndIncrement(), serializedToken.getData());
    }

    private void setDeadLetterFields(PreparedStatement statement,
                                     AtomicInteger fieldIndex,
                                     DeadLetter<? extends M> letter) throws SQLException {
        statement.setString(fieldIndex.getAndIncrement(), letter.enqueuedAt().toString());
        statement.setString(fieldIndex.getAndIncrement(), letter.lastTouched().toString());
        Optional<Cause> optionalCause = letter.cause();
        if (optionalCause.isPresent()) {
            Cause cause = optionalCause.get();
            statement.setString(fieldIndex.getAndIncrement(), cause.type());
            statement.setString(fieldIndex.getAndIncrement(), cause.message());
        }
        SerializedObject<byte[]> serializedDiagnostics = eventSerializer.serialize(letter.diagnostics(), byte[].class);
        statement.setBytes(fieldIndex.getAndIncrement(), serializedDiagnostics.getData());
    }

    @Override
    public PreparedStatement containsStatement(Connection connection, String sequenceId) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=? ";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement letterSequenceStatement(Connection connection,
                                                     String sequenceId,
                                                     int firstResult,
                                                     int maxSize) throws SQLException {
        String sql = "SELECT * "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=? "
                + "AND " + schema.sequenceIndexColumn() + ">= " + firstResult + " "
                + "LIMIT " + maxSize;

        PreparedStatement statement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement sequenceIdentifiersStatement(Connection connection) throws SQLException {
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
    public PreparedStatement sizeStatement(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? ";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement sequenceSizeStatement(Connection connection, String sequenceId) throws SQLException {
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
    public PreparedStatement amountOfSequencesStatement(Connection c) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT " + schema.sequenceIdentifierColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = c.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement clearStatement(Connection c) throws SQLException {
        String sql = "DELETE "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = c.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement maxIndexStatement(Connection connection, String sequenceId) throws SQLException {
        String sql = "SELECT MAX(" + schema.sequenceIndexColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    protected static class Builder<M extends EventMessage<?>> {

        private String processingGroup;
        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private Serializer genericSerializer;
        private Serializer eventSerializer;

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
         * @param schema
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> schema(DeadLetterSchema schema) {
            assertNonNull(schema, "DeadLetterSchema may not be null");
            this.schema = schema;
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
        public SimpleDeadLetterStatementFactory<M> build() {
            return new SimpleDeadLetterStatementFactory<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonEmpty(processingGroup, "The processingGroup is a hard requirement and should be non-empty");
            assertNonNull(genericSerializer, "The generic Serializer is a hard requirement and should be provided");
            assertNonNull(eventSerializer, "The event Serializer is a hard requirement and should be provided");
        }
    }
}
