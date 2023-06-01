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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.ThrowableCause;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class SimpleDeadLetterJdbcConverter<M extends EventMessage<?>> implements DeadLetterJdbcConverter<M> {

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

    private final Serializer genericSerializer;
    private final Serializer eventSerializer;

    protected SimpleDeadLetterJdbcConverter(Builder<M> builder) {
        builder.validate();
        genericSerializer = builder.genericSerializer;
        eventSerializer = builder.eventSerializer;
    }

    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
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

    protected static class Builder<M extends EventMessage<?>> {

        private Serializer genericSerializer;
        private Serializer eventSerializer;

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
        public SimpleDeadLetterJdbcConverter<M> build() {
            return new SimpleDeadLetterJdbcConverter<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(genericSerializer, "The generic Serializer is a hard requirement and should be provided");
            assertNonNull(eventSerializer, "The event Serializer is a hard requirement and should be provided");
        }
    }
}
