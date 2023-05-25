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
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.ThrowableCause;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class SimpleJdbcDeadLetterConverter<M extends EventMessage<?>> implements JdbcDeadLetterConverter<M> {

    private static final String COMMA = ", ";
    // TODO move the indices to the schema perhaps
    private static final int SEQUENCE_ID_INDEX = 3;
    private static final int EVENT_MESSAGE_TYPE_INDEX = 6;
    private static final int EVENT_TIMESTAMP_INDEX = 7;
    private static final int PAYLOAD_TYPE_INDEX = 8;
    private static final int PAYLOAD_REVISION_INDEX = 9;
    private static final int PAYLOAD_INDEX = 10;
    private static final int META_DATA_INDEX = 11;
    private static final int AGGREGATE_TYPE_INDEX = 12;
    private static final int AGGREGATE_ID_INDEX = 13;
    private static final int TOKEN_TYPE_INDEX = 15;
    private static final int TOKEN_INDEX = 16;
    private static final int ENQUEUED_AT_INDEX = 17;
    private static final int LAST_TOUCHED_INDEX = 18;
    private static final int CAUSE_TYPE_INDEX = 20;
    private static final int CAUSE_MESSAGE_INDEX = 21;
    private static final int DIAGNOSTICS_INDEX = 22;

    private final String processingGroup;
    private final DeadLetterSchema schema;
    private final Serializer genericSerializer;
    private final Serializer eventSerializer;

    protected SimpleJdbcDeadLetterConverter(Builder<M> builder) {
        processingGroup = builder.processingGroup;
        schema = builder.schema;
        genericSerializer = builder.genericSerializer;
        eventSerializer = builder.eventSerializer;
    }

    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public PreparedStatement enqueueStatement(@Nonnull String sequenceIdentifier,
                                              @Nonnull DeadLetter<? extends M> letter,
                                              long sequenceIndex,
                                              @Nonnull Connection connection) throws SQLException {
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
            fieldCount += SEQUENCE_ID_INDEX;
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
    public DeadLetter<? extends M> convertToLetter(ResultSet resultSet) throws SQLException {
        EventMessage<?> eventMessage;
        Message<?> serializedMessage = convertToSerializedMessage(resultSet);
        String eventTimestampString = resultSet.getString(EVENT_TIMESTAMP_INDEX);
        Supplier<Instant> timestampSupplier = () -> Instant.parse(eventTimestampString);

        if (resultSet.getString(TOKEN_TYPE_INDEX) != null) {
            TrackingToken trackingToken = convertToTrackingToken(resultSet);
            if (resultSet.getString(AGGREGATE_ID_INDEX) != null) {
                eventMessage = new GenericTrackedDomainEventMessage<>(trackingToken,
                                                                      resultSet.getString(AGGREGATE_TYPE_INDEX),
                                                                      resultSet.getString(AGGREGATE_ID_INDEX),
                                                                      resultSet.getLong(TOKEN_TYPE_INDEX),
                                                                      serializedMessage,
                                                                      timestampSupplier);
            } else {
                eventMessage = new GenericTrackedEventMessage<>(trackingToken, serializedMessage, timestampSupplier);
            }
        } else if (resultSet.getString(AGGREGATE_ID_INDEX) != null) {
            eventMessage = new GenericDomainEventMessage<>(resultSet.getString(AGGREGATE_TYPE_INDEX),
                                                           resultSet.getString(AGGREGATE_ID_INDEX),
                                                           resultSet.getLong(TOKEN_TYPE_INDEX),
                                                           serializedMessage.getPayload(),
                                                           serializedMessage.getMetaData(),
                                                           serializedMessage.getIdentifier(),
                                                           timestampSupplier.get());
        } else {
            eventMessage = new GenericEventMessage<>(serializedMessage, timestampSupplier);
        }

        Cause cause = null;
        if (resultSet.getString(CAUSE_TYPE_INDEX) != null) {
            cause = new ThrowableCause(resultSet.getString(CAUSE_TYPE_INDEX), resultSet.getString(CAUSE_MESSAGE_INDEX));
        }
        Instant enqueuedAt = Instant.parse(resultSet.getString(ENQUEUED_AT_INDEX));
        Instant lastTouched = Instant.parse(resultSet.getString(LAST_TOUCHED_INDEX));
        MetaData diagnostics = convertToDiagnostics(resultSet);

        //noinspection unchecked
        return (DeadLetter<? extends M>) new GenericDeadLetter<>(
                resultSet.getString(SEQUENCE_ID_INDEX), eventMessage, cause, enqueuedAt, lastTouched, diagnostics
        );
    }

    private SerializedMessage<?> convertToSerializedMessage(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedPayload = convertToSerializedPayload(resultSet);
        SerializedObject<byte[]> serializedMetaData = convertToSerializedMetaData(resultSet);
        return new SerializedMessage<>(resultSet.getString(EVENT_MESSAGE_TYPE_INDEX),
                                       serializedPayload,
                                       serializedMetaData,
                                       eventSerializer);
    }

    private SerializedObject<byte[]> convertToSerializedPayload(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedObject<>(resultSet.getBytes(PAYLOAD_INDEX),
                                            byte[].class,
                                            resultSet.getString(PAYLOAD_TYPE_INDEX),
                                            resultSet.getString(PAYLOAD_REVISION_INDEX));
    }

    private SerializedObject<byte[]> convertToSerializedMetaData(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedObject<>(resultSet.getBytes(META_DATA_INDEX),
                                            byte[].class,
                                            MetaData.class.getName(),
                                            null);
    }

    private TrackingToken convertToTrackingToken(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedToken = new SimpleSerializedObject<>(resultSet.getBytes(TOKEN_INDEX),
                                                                                byte[].class,
                                                                                resultSet.getString(TOKEN_TYPE_INDEX),
                                                                                null);
        return genericSerializer.deserialize(serializedToken);
    }

    private MetaData convertToDiagnostics(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedDiagnostics =
                new SimpleSerializedObject<>(resultSet.getBytes(DIAGNOSTICS_INDEX),
                                             byte[].class,
                                             MetaData.class.getName(),
                                             null);
        return eventSerializer.deserialize(serializedDiagnostics);
    }

    public static class Builder<M extends EventMessage<?>> {

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
        public SimpleJdbcDeadLetterConverter<M> build() {
            return new SimpleJdbcDeadLetterConverter<>(this);
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
