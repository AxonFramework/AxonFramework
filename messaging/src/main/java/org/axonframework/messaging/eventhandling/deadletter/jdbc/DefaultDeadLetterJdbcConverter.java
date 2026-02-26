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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ClassUtils;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.ThrowableCause;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Default implementation of the {@link DeadLetterJdbcConverter}, converting {@link ResultSet ResultSets} into
 * {@link JdbcDeadLetter} instances.
 * <p>
 * This converter expects a {@link DeadLetterSchema} to define the column names / labels used to retrieve the fields
 * from the {@link ResultSet}. Furthermore, it uses the configurable {@code genericConverter} to deserialize
 * {@link TrackingToken TrackingTokens}. Lastly, this converter uses the {@code eventConverter} to deserialize the
 * {@link EventMessage#metadata() Metadata} and {@link DeadLetter#diagnostics() diagnostics} for the
 * {@code JdbcDeadLetter} to return.
 * <p>
 * The event payload is kept in its serialized {@code byte[]} form within the reconstructed {@link EventMessage} for
 * lazy deserialization by the framework's {@link Converter} infrastructure.
 *
 * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation this
 *            converter converts.
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class DefaultDeadLetterJdbcConverter<E extends EventMessage>
        implements DeadLetterJdbcConverter<E, JdbcDeadLetter<E>> {

    private static final TypeReference<Map<String, String>> METADATA_MAP_TYPE_REF = new TypeReference<>() {
    };

    private final DeadLetterSchema schema;
    private final Converter genericConverter;
    private final EventConverter eventConverter;

    /**
     * Instantiate a default {@link DeadLetterJdbcConverter} based on the given {@code builder}.
     * <p>
     * Will validate whether the {@link Builder#genericConverter(Converter) generic Converter} and
     * {@link Builder#eventConverter(EventConverter) EventConverter} are set. If for either this is not the case an
     * {@link AxonConfigurationException} is thrown.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DefaultDeadLetterJdbcConverter} instance.
     */
    protected DefaultDeadLetterJdbcConverter(Builder<E> builder) {
        builder.validate();
        schema = builder.schema;
        genericConverter = builder.genericConverter;
        eventConverter = builder.eventConverter;
    }

    /**
     * Instantiate a builder to construct a {@link DefaultDeadLetterJdbcConverter}.
     * <p>
     * The {@link Builder#schema(DeadLetterSchema) schema} is defaulted to a {@link DeadLetterSchema#defaultSchema()}.
     * The {@link Builder#genericConverter(Converter) generic Converter} and
     * {@link Builder#eventConverter(EventConverter) EventConverter} are hard requirements and should be provided.
     *
     * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation
     *            this converter converts.
     * @return A builder that can construct a {@link DefaultDeadLetterJdbcConverter}.
     */
    public static <E extends EventMessage> Builder<E> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public JdbcDeadLetter<E> convertToLetter(ResultSet resultSet) throws SQLException {
        // Read event fields
        String eventIdentifier = resultSet.getString(schema.eventIdentifierColumn());
        String type = resultSet.getString(schema.typeColumn());
        String timestampString = resultSet.getString(schema.timestampColumn());
        byte[] payloadBytes = resultSet.getBytes(schema.payloadColumn());
        byte[] metadataBytes = resultSet.getBytes(schema.metadataColumn());

        // Deserialize metadata
        Map<String, String> metadataMap = eventConverter.convert(metadataBytes, METADATA_MAP_TYPE_REF.getType());

        // Construct event message with raw payload bytes (lazy deserialization)
        EventMessage eventMessage = new GenericEventMessage(
                eventIdentifier,
                MessageType.fromString(type),
                payloadBytes,
                Metadata.from(metadataMap),
                DateTimeUtils.parseInstant(timestampString)
        );

        // Restore context with tracking token and aggregate data
        Context context = restoreContext(resultSet);

        // Dead letter fields
        String deadLetterIdentifier = resultSet.getString(schema.deadLetterIdentifierColumn());
        long sequenceIndex = resultSet.getLong(schema.sequenceIndexColumn());
        String sequenceIdentifier = resultSet.getString(schema.sequenceIdentifierColumn());
        Instant enqueuedAt = DateTimeUtils.parseInstant(resultSet.getString(schema.enqueuedAtColumn()));
        Instant lastTouched = DateTimeUtils.parseInstant(resultSet.getString(schema.lastTouchedColumn()));
        Cause cause = null;
        String causeType = resultSet.getString(schema.causeTypeColumn());
        if (causeType != null) {
            cause = new ThrowableCause(causeType, resultSet.getString(schema.causeMessageColumn()));
        }
        Metadata diagnostics = convertToDiagnostics(resultSet);

        return new JdbcDeadLetter<>(deadLetterIdentifier,
                                    sequenceIndex,
                                    sequenceIdentifier,
                                    enqueuedAt,
                                    lastTouched,
                                    cause,
                                    diagnostics,
                                    (E) eventMessage,
                                    context);
    }

    private Context restoreContext(ResultSet resultSet) throws SQLException {
        Context context = Context.empty();
        String tokenTypeName = resultSet.getString(schema.tokenTypeColumn());
        if (tokenTypeName != null) {
            byte[] tokenBytes = resultSet.getBytes(schema.tokenColumn());
            TrackingToken token = genericConverter.convert(tokenBytes, ClassUtils.loadClass(tokenTypeName));
            if (token != null) {
                context = context.withResource(TrackingToken.RESOURCE_KEY, token);
            }
        }
        String aggregateIdentifier = resultSet.getString(schema.aggregateIdentifierColumn());
        if (aggregateIdentifier != null) {
            context = context.withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, aggregateIdentifier);
        }
        String aggregateType = resultSet.getString(schema.aggregateTypeColumn());
        if (aggregateType != null) {
            context = context.withResource(LegacyResources.AGGREGATE_TYPE_KEY, aggregateType);
        }
        long sequenceNumber = resultSet.getLong(schema.sequenceNumberColumn());
        if (!resultSet.wasNull() && sequenceNumber >= 0) {
            context = context.withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, sequenceNumber);
        }
        return context;
    }

    private Metadata convertToDiagnostics(ResultSet resultSet) throws SQLException {
        byte[] diagnosticsBytes = resultSet.getBytes(schema.diagnosticsColumn());
        Map<String, String> diagnosticsMap = eventConverter.convert(diagnosticsBytes, METADATA_MAP_TYPE_REF.getType());
        return Metadata.from(diagnosticsMap);
    }

    /**
     * Builder class to instantiate a {@link DefaultDeadLetterJdbcConverter}.
     * <p>
     * The {@link Builder#schema(DeadLetterSchema) schema} is defaulted to a {@link DeadLetterSchema#defaultSchema()}.
     * The {@link Builder#genericConverter(Converter) generic Converter} and
     * {@link Builder#eventConverter(EventConverter) EventConverter} are hard requirements and should be provided.
     *
     * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation
     *            this converter converts.
     */
    protected static class Builder<E extends EventMessage> {

        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private Converter genericConverter;
        private EventConverter eventConverter;

        /**
         * Sets the given {@code schema} used to define the column names / labels with to return fields from the
         * {@link ResultSet}. Defaults to a {@link DeadLetterSchema#defaultSchema()}.
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
         * Sets the {@link Converter} to deserialize the {@link TrackingToken} when reconstructing dead letters from
         * the database.
         *
         * @param genericConverter The converter used to deserialize {@link TrackingToken TrackingTokens}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> genericConverter(Converter genericConverter) {
            assertNonNull(genericConverter, "The generic Converter may not be null");
            this.genericConverter = genericConverter;
            return this;
        }

        /**
         * Sets the {@link EventConverter} to deserialize {@link EventMessage#metadata() Metadata} instances and
         * {@link DeadLetter#diagnostics() diagnostics} when reconstructing dead letters from the database.
         *
         * @param eventConverter The converter used to deserialize metadata and diagnostics.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> eventConverter(EventConverter eventConverter) {
            assertNonNull(eventConverter, "The EventConverter may not be null");
            this.eventConverter = eventConverter;
            return this;
        }

        /**
         * Initializes a {@link DefaultDeadLetterJdbcConverter} as specified through this Builder.
         *
         * @return A {@link DefaultDeadLetterJdbcConverter} as specified through this Builder.
         */
        public DefaultDeadLetterJdbcConverter<E> build() {
            return new DefaultDeadLetterJdbcConverter<>(this);
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
