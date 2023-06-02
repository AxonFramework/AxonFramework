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
 * @param <E> An implementation of {@link EventMessage}
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class SimpleDeadLetterJdbcConverter<E extends EventMessage<?>>
        implements DeadLetterJdbcConverter<E, JdbcDeadLetter<E>> {

    private final DeadLetterSchema schema;
    private final Serializer genericSerializer;
    private final Serializer eventSerializer;

    protected SimpleDeadLetterJdbcConverter(Builder<E> builder) {
        builder.validate();
        schema = builder.schema;
        genericSerializer = builder.genericSerializer;
        eventSerializer = builder.eventSerializer;
    }

    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public JdbcDeadLetter<E> convertToLetter(ResultSet resultSet) throws SQLException {
        EventMessage<?> eventMessage;
        Message<?> serializedMessage = convertToSerializedMessage(resultSet);
        String eventTimestampString = resultSet.getString(schema.timeStampColumn());
        Supplier<Instant> timestampSupplier = () -> Instant.parse(eventTimestampString);

        if (resultSet.getString(schema.tokenTypeColumn()) != null) {
            TrackingToken trackingToken = convertToTrackingToken(resultSet);
            if (resultSet.getString(schema.aggregateIdentifierColumn()) != null) {
                eventMessage = new GenericTrackedDomainEventMessage<>(
                        trackingToken,
                        resultSet.getString(schema.aggregateTypeColumn()),
                        resultSet.getString(schema.aggregateIdentifierColumn()),
                        resultSet.getLong(schema.sequenceNumberColumn()),
                        serializedMessage,
                        timestampSupplier
                );
            } else {
                eventMessage = new GenericTrackedEventMessage<>(trackingToken, serializedMessage, timestampSupplier);
            }
        } else if (resultSet.getString(schema.aggregateIdentifierColumn()) != null) {
            eventMessage = new GenericDomainEventMessage<>(resultSet.getString(schema.aggregateTypeColumn()),
                                                           resultSet.getString(schema.aggregateIdentifierColumn()),
                                                           resultSet.getLong(schema.sequenceNumberColumn()),
                                                           serializedMessage.getPayload(),
                                                           serializedMessage.getMetaData(),
                                                           serializedMessage.getIdentifier(),
                                                           timestampSupplier.get());
        } else {
            eventMessage = new GenericEventMessage<>(serializedMessage, timestampSupplier);
        }

        String deadLetterIdentifier = resultSet.getString(schema.deadLetterIdentifierColumn());
        long sequenceIndex = resultSet.getLong(schema.sequenceIndexColumn());
        String sequenceIdentifier = resultSet.getString(schema.sequenceIdentifierColumn());
        Instant enqueuedAt = Instant.parse(resultSet.getString(schema.enqueuedAtColumn()));
        Instant lastTouched = Instant.parse(resultSet.getString(schema.lastTouchedColumn()));
        Cause cause = null;
        String causeType = resultSet.getString(schema.causeTypeColumn());
        if (causeType != null) {
            cause = new ThrowableCause(causeType, resultSet.getString(schema.causeMessageColumn()));
        }
        MetaData diagnostics = convertToDiagnostics(resultSet);

        //noinspection unchecked
        return new JdbcDeadLetter<>(deadLetterIdentifier,
                                    sequenceIndex,
                                    sequenceIdentifier,
                                    enqueuedAt,
                                    lastTouched,
                                    cause,
                                    diagnostics,
                                    (E) eventMessage);
    }

    private SerializedMessage<?> convertToSerializedMessage(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedPayload = convertToSerializedPayload(resultSet);
        SerializedObject<byte[]> serializedMetaData = convertToSerializedMetaData(resultSet);
        return new SerializedMessage<>(resultSet.getString(schema.eventIdentifierColumn()),
                                       serializedPayload,
                                       serializedMetaData,
                                       eventSerializer);
    }

    private SerializedObject<byte[]> convertToSerializedPayload(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedObject<>(resultSet.getBytes(schema.payloadColumn()),
                                            byte[].class,
                                            resultSet.getString(schema.payloadTypeColumn()),
                                            resultSet.getString(schema.payloadRevisionColumn()));
    }

    private SerializedObject<byte[]> convertToSerializedMetaData(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedObject<>(resultSet.getBytes(schema.metaDataColumn()),
                                            byte[].class,
                                            MetaData.class.getName(),
                                            null);
    }

    private TrackingToken convertToTrackingToken(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedToken =
                new SimpleSerializedObject<>(resultSet.getBytes(schema.tokenColumn()),
                                             byte[].class,
                                             resultSet.getString(schema.tokenTypeColumn()),
                                             null);
        return genericSerializer.deserialize(serializedToken);
    }

    private MetaData convertToDiagnostics(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedDiagnostics =
                new SimpleSerializedObject<>(resultSet.getBytes(schema.diagnosticsColumn()),
                                             byte[].class,
                                             MetaData.class.getName(),
                                             null);
        return eventSerializer.deserialize(serializedDiagnostics);
    }

    protected static class Builder<E extends EventMessage<?>> {

        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private Serializer genericSerializer;
        private Serializer eventSerializer;

        /**
         * @param schema
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> schema(DeadLetterSchema schema) {
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
        public Builder<E> genericSerializer(Serializer genericSerializer) {

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
        public Builder<E> eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event serializer may not be null");
            this.eventSerializer = eventSerializer;
            return this;
        }

        /**
         * Initializes a {@link JdbcSequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link JdbcSequencedDeadLetterQueue} as specified through this Builder.
         */
        public SimpleDeadLetterJdbcConverter<E> build() {
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
