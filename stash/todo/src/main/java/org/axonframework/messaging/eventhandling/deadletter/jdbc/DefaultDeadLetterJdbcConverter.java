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
 * from the {@link ResultSet}. Furthermore, it uses the configurable {@code genericConverter} to convert
 * {@link TrackingToken TrackingTokens} and diagnostics. Lastly, this converter uses the {@code eventConverter} to
 * convert the {@link EventMessage#payload() event payload} and {@link EventMessage#metadata() Metadata} for the
 * {@code JdbcDeadLetter} to return.
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
    private static final TypeReference<Map<String, String>> DIAGNOSTICS_MAP_TYPE_REF = new TypeReference<>() {
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

    @Override
    public JdbcDeadLetter<E> convertToLetter(ResultSet resultSet) throws SQLException {
        EventMessage eventMessage = deserializeMessage(resultSet);
        Context context = restoreContext(resultSet);

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

        //noinspection unchecked
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

    private EventMessage deserializeMessage(ResultSet resultSet) throws SQLException {
        byte[] payloadBytes = resultSet.getBytes(schema.payloadColumn());
        byte[] metadataBytes = resultSet.getBytes(schema.metadataColumn());
        Map<String, String> metadataMap = eventConverter.convert(metadataBytes, METADATA_MAP_TYPE_REF.getType());

        String eventTimestampString = resultSet.getString(schema.timestampColumn());

        return new GenericEventMessage(
                resultSet.getString(schema.eventIdentifierColumn()),
                MessageType.fromString(resultSet.getString(schema.typeColumn())),
                payloadBytes,
                Metadata.from(metadataMap),
                DateTimeUtils.parseInstant(eventTimestampString)
        );
    }

    private Context restoreContext(ResultSet resultSet) throws SQLException {
        Context context = Context.empty();

        String tokenType = resultSet.getString(schema.tokenTypeColumn());
        if (tokenType != null) {
            byte[] tokenBytes = resultSet.getBytes(schema.tokenColumn());
            if (tokenBytes != null) {
                TrackingToken token = genericConverter.convert(tokenBytes, ClassUtils.loadClass(tokenType));
                if (token != null) {
                    context = context.withResource(TrackingToken.RESOURCE_KEY, token);
                }
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
        if (!resultSet.wasNull()) {
            context = context.withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, sequenceNumber);
        }

        return context;
    }

    private Metadata convertToDiagnostics(ResultSet resultSet) throws SQLException {
        byte[] diagnosticsBytes = resultSet.getBytes(schema.diagnosticsColumn());
        if (diagnosticsBytes == null) {
            return Metadata.emptyInstance();
        }
        Map<String, String> diagnosticsMap = genericConverter.convert(diagnosticsBytes,
                                                                      DIAGNOSTICS_MAP_TYPE_REF.getType());
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
         * Sets the {@link Converter} to convert the {@link TrackingToken} and diagnostics.
         *
         * @param genericConverter The converter used to convert {@link TrackingToken TrackingTokens} and diagnostics.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> genericConverter(Converter genericConverter) {
            assertNonNull(genericConverter, "The generic Converter may not be null");
            this.genericConverter = genericConverter;
            return this;
        }

        /**
         * Sets the {@link EventConverter} to convert {@link EventMessage#payload() event payloads} and
         * {@link EventMessage#metadata() Metadata} instances.
         *
         * @param eventConverter The event converter used to convert {@link EventMessage#payload() event payloads} and
         *                       {@link EventMessage#metadata() Metadata} instances.
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
