/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;

import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;

/**
 * Schema description for an {@link org.axonframework.eventhandling.EventMessage} holding
 * {@link org.axonframework.messaging.deadletter.DeadLetter} entry table in JDBC.
 *
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class DeadLetterSchema {

    private final String deadLetterTable;
    // Dead letter identifying columns
    private final String deadLetterIdentifierColumn;
    private final String processingGroupColumn;
    private final String sequenceIdentifierColumn;
    private final String sequenceIndexColumn;
    // Event Message columns
    private final String eventTypeColumn;
    private final String eventIdentifierColumn;
    private final String typeColumn;
    private final String timestampColumn;
    private final String payloadTypeColumn;
    private final String payloadRevisionColumn;
    private final String payloadColumn;
    private final String metaDataColumn;
    private final String aggregateTypeColumn;
    private final String aggregateIdentifierColumn;
    private final String sequenceNumberColumn;
    private final String tokenTypeColumn;
    private final String tokenColumn;
    // Dead letter processing columns
    private final String enqueuedAtColumn;
    private final String lastTouchedColumn;
    private final String processingStartedColumn;
    private final String causeTypeColumn;
    private final String causeMessageColumn;
    private final String diagnosticsColumn;

    private final Function<DeadLetterSchema, String> deadLetterFields = schema ->
            String.join(",",
                        schema.deadLetterIdentifierColumn(),
                        schema.processingGroupColumn(),
                        schema.sequenceIdentifierColumn(),
                        schema.sequenceIndexColumn(),
                        schema.eventTypeColumn(),
                        schema.eventIdentifierColumn(),
                        schema.typeColumn(),
                        schema.timestampColumn(),
                        schema.payloadTypeColumn(),
                        schema.payloadRevisionColumn(),
                        schema.payloadColumn(),
                        schema.metaDataColumn(),
                        schema.aggregateTypeColumn(),
                        schema.aggregateIdentifierColumn(),
                        schema.sequenceNumberColumn(),
                        schema.tokenTypeColumn(),
                        schema.tokenColumn(),
                        schema.enqueuedAtColumn(),
                        schema.lastTouchedColumn(),
                        schema.causeTypeColumn(),
                        schema.causeMessageColumn(),
                        schema.diagnosticsColumn());

    /**
     * Instantiate a {@link DeadLetterSchema} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetterSchema} instance.
     */
    protected DeadLetterSchema(Builder builder) {
        this.deadLetterTable = builder.deadLetterTable;
        this.deadLetterIdentifierColumn = builder.deadLetterIdentifierColumn;
        this.processingGroupColumn = builder.processingGroupColumn;
        this.sequenceIdentifierColumn = builder.sequenceIdentifierColumn;
        this.sequenceIndexColumn = builder.sequenceIndexColumn;
        this.eventTypeColumn = builder.eventTypeColumn;
        this.eventIdentifierColumn = builder.eventIdentifierColumn;
        this.typeColumn = builder.typeColumn;
        this.timestampColumn = builder.timeStampColumn;
        this.payloadTypeColumn = builder.payloadTypeColumn;
        this.payloadRevisionColumn = builder.payloadRevisionColumn;
        this.payloadColumn = builder.payloadColumn;
        this.metaDataColumn = builder.metaDataColumn;
        this.aggregateTypeColumn = builder.aggregateTypeColumn;
        this.aggregateIdentifierColumn = builder.aggregateIdentifierColumn;
        this.sequenceNumberColumn = builder.sequenceNumberColumn;
        this.tokenTypeColumn = builder.tokenTypeColumn;
        this.tokenColumn = builder.tokenColumn;
        this.enqueuedAtColumn = builder.enqueuedAtColumn;
        this.lastTouchedColumn = builder.lastTouchedColumn;
        this.processingStartedColumn = builder.processingStartedColumn;
        this.causeTypeColumn = builder.causeTypeColumn;
        this.causeMessageColumn = builder.causeMessageColumn;
        this.diagnosticsColumn = builder.diagnosticsColumn;
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetterSchema}.
     * <p>
     * All configurable columns default to their respective field name. Thus, the result of the
     * {@link #diagnosticsColumn()} defaults to {@code diagnosticsColumn}, etc.
     *
     * @return A Builder that can construct a {@link DeadLetterSchema}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a default {@link DeadLetterSchema} using the pre-configured column names.
     *
     * @return A default {@link DeadLetterSchema} using the pre-configured column names
     */
    public static DeadLetterSchema defaultSchema() {
        return builder().build();
    }

    /**
     * Returns the configured {@code deadLetter} table name.
     *
     * @return The configured {@code deadLetter} table name.
     */
    public String deadLetterTable() {
        return deadLetterTable;
    }

    /**
     * Returns the configured {@code deadLetterIdentifier} column name.
     *
     * @return The configured {@code deadLetterIdentifier} column name.
     */
    public String deadLetterIdentifierColumn() {
        return deadLetterIdentifierColumn;
    }

    /**
     * Returns the configured {@code processingGroup} column name.
     *
     * @return The configured {@code processingGroup} column name.
     */
    public String processingGroupColumn() {
        return processingGroupColumn;
    }

    /**
     * Returns the configured {@code sequenceIdentifier} column name.
     *
     * @return The configured {@code sequenceIdentifier} column name.
     */
    public String sequenceIdentifierColumn() {
        return sequenceIdentifierColumn;
    }

    /**
     * Returns the configured {@code sequenceIndex} column name.
     *
     * @return The configured {@code sequenceIndex} column name.
     */
    public String sequenceIndexColumn() {
        return sequenceIndexColumn;
    }

    /**
     * Returns the configured {@code eventType} column name.
     *
     * @return The configured {@code eventType} column name.
     */
    public String eventTypeColumn() {
        return eventTypeColumn;
    }

    /**
     * Returns the configured {@code eventIdentifier} column name.
     *
     * @return The configured {@code eventIdentifier} column name.
     */
    public String eventIdentifierColumn() {
        return eventIdentifierColumn;
    }

    /**
     * Returns the configured {@code typeColumn} column name.
     * <p>
     * Represents the {@link Message#type()} field, as a {@link QualifiedName#toSimpleString() simple String.}
     *
     * @return The configured {@code typeColumn} column name.
     */
    public String typeColumn() {
        return typeColumn;
    }

    /**
     * Returns the configured {@code timestamp} column name.
     *
     * @return The configured {@code timestamp} column name.
     */
    public String timestampColumn() {
        return timestampColumn;
    }

    /**
     * Returns the configured {@code payloadType} column name.
     *
     * @return The configured {@code payloadType} column name.
     */
    public String payloadTypeColumn() {
        return payloadTypeColumn;
    }

    /**
     * Returns the configured {@code payloadRevision} column name.
     *
     * @return The configured {@code payloadRevision} column name.
     */
    public String payloadRevisionColumn() {
        return payloadRevisionColumn;
    }

    /**
     * Returns the configured {@code payload} column name.
     *
     * @return The configured {@code payload} column name.
     */
    public String payloadColumn() {
        return payloadColumn;
    }

    /**
     * Returns the configured {@code metaData} column name.
     *
     * @return The configured {@code metaData} column name.
     */
    public String metaDataColumn() {
        return metaDataColumn;
    }

    /**
     * Returns the configured {@code aggregateType} column name.
     *
     * @return The configured {@code aggregateType} column name.
     */
    public String aggregateTypeColumn() {
        return aggregateTypeColumn;
    }

    /**
     * Returns the configured {@code aggregateIdentifier} column name.
     *
     * @return The configured {@code aggregateIdentifier} column name.
     */
    public String aggregateIdentifierColumn() {
        return aggregateIdentifierColumn;
    }

    /**
     * Returns the configured {@code sequenceNumber} column name.
     *
     * @return The configured {@code sequenceNumber} column name.
     */
    public String sequenceNumberColumn() {
        return sequenceNumberColumn;
    }

    /**
     * Returns the configured {@code tokenType} column name.
     *
     * @return The configured {@code tokenType} column name.
     */
    public String tokenTypeColumn() {
        return tokenTypeColumn;
    }

    /**
     * Returns the configured {@code token} column name.
     *
     * @return The configured {@code token} column name.
     */
    public String tokenColumn() {
        return tokenColumn;
    }

    /**
     * Returns the configured {@code enqueuedAt} column name.
     *
     * @return The configured {@code enqueuedAt} column name.
     */
    public String enqueuedAtColumn() {
        return enqueuedAtColumn;
    }

    /**
     * Returns the configured {@code lastTouched} column name.
     *
     * @return The configured {@code lastTouched} column name.
     */
    public String lastTouchedColumn() {
        return lastTouchedColumn;
    }

    /**
     * Returns the configured {@code processingStarted} column name.
     *
     * @return The configured {@code processingStarted} column name.
     */
    public String processingStartedColumn() {
        return processingStartedColumn;
    }

    /**
     * Returns the configured {@code causeType} column name.
     *
     * @return The configured {@code causeType} column name.
     */
    public String causeTypeColumn() {
        return causeTypeColumn;
    }

    /**
     * Returns the configured {@code causeMessage} column name.
     *
     * @return The configured {@code causeMessage} column name.
     */
    public String causeMessageColumn() {
        return causeMessageColumn;
    }

    /**
     * Returns the configured {@code diagnostics} column name.
     *
     * @return The configured {@code diagnostics} column name.
     */
    public String diagnosticsColumn() {
        return diagnosticsColumn;
    }

    /**
     * Return a comma separated list of dead letter column names to insert a dead letter into the
     * {@link #deadLetterTable() dead letter table}.
     *
     * @return A comma separated list of dead letter column names.
     */
    public String deadLetterFields() {
        return deadLetterFields.apply(this);
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetterSchema}.
     * <p>
     * All configurable columns default to their respective field name. Thus, the result of the
     * {@link #diagnosticsColumn()} defaults to {@code diagnosticsColumn}, etc.
     */
    public static class Builder {

        private String deadLetterTable = "DeadLetterEntry";
        private String deadLetterIdentifierColumn = "deadLetterIdentifier";
        private String processingGroupColumn = "processingGroup";
        private String sequenceIdentifierColumn = "sequenceIdentifier";
        private String sequenceIndexColumn = "sequenceIndex";
        private String eventTypeColumn = "eventType";
        private String eventIdentifierColumn = "eventIdentifier";
        private String typeColumn = "type";
        private String timeStampColumn = "timestamp";
        private String payloadTypeColumn = "payloadType";
        private String payloadRevisionColumn = "payloadRevision";
        private String payloadColumn = "payload";
        private String metaDataColumn = "metaData";
        private String aggregateTypeColumn = "aggregateType";
        private String aggregateIdentifierColumn = "aggregateIdentifier";
        private String sequenceNumberColumn = "sequenceNumber";
        private String tokenTypeColumn = "tokenType";
        private String tokenColumn = "token";
        private String enqueuedAtColumn = "enqueuedAt";
        private String lastTouchedColumn = "lastTouched";
        private String processingStartedColumn = "processingStarted";
        private String causeTypeColumn = "causeType";
        private String causeMessageColumn = "causeMessage";
        private String diagnosticsColumn = "diagnostics";

        /**
         * Sets the name of the dead-letter table. Defaults to {@code DeadLetterEntry}.
         *
         * @param deadLetterTable The name for the dead-letter table.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder deadLetterTable(String deadLetterTable) {
            assertNonEmpty(deadLetterTable, "The DeadLetterEntryColumn should be not null or empty");
            this.deadLetterTable = deadLetterTable;
            return this;
        }

        /**
         * Sets the name of the {@code deadLetterIdentifier} column. Defaults to {@code deadLetterIdentifier}.
         *
         * @param deadLetterIdentifierColumn The name for the {@code deadLetterIdentifier} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder deadLetterIdentifierColumn(String deadLetterIdentifierColumn) {
            assertNonEmpty(deadLetterIdentifierColumn, "The deadLetterIdentifierColumn should be not null or empty");
            this.deadLetterIdentifierColumn = deadLetterIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the {@code processingGroup} column. Defaults to {@code processingGroup}.
         *
         * @param processingGroupColumn The name for the {@code processingGroup} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder processingGroupColumn(String processingGroupColumn) {
            assertNonEmpty(processingGroupColumn, "The processingGroupColumn should be not null or empty");
            this.processingGroupColumn = processingGroupColumn;
            return this;
        }

        /**
         * Sets the name of the {@code sequenceIdentifier} column. Defaults to {@code sequenceIdentifier}.
         *
         * @param sequenceIdentifierColumn The name for the {@code sequenceIdentifier} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder sequenceIdentifierColumn(String sequenceIdentifierColumn) {
            assertNonEmpty(sequenceIdentifierColumn, "The sequenceIdentifierColumn should be not null or empty");
            this.sequenceIdentifierColumn = sequenceIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the {@code sequenceIndex} column. Defaults to {@code sequenceIndex}.
         *
         * @param sequenceIndexColumn The name for the {@code sequenceIndex} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder sequenceIndexColumn(String sequenceIndexColumn) {
            assertNonEmpty(sequenceIndexColumn, "The sequenceIndexColumn should be not null or empty");
            this.sequenceIndexColumn = sequenceIndexColumn;
            return this;
        }

        /**
         * Sets the name of the {@code eventType} column. Defaults to {@code eventType}.
         *
         * @param eventTypeColumn The name for the {@code eventType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder eventTypeColumn(String eventTypeColumn) {
            assertNonEmpty(eventTypeColumn, "The eventTypeColumn should be not null or empty");
            this.eventTypeColumn = eventTypeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code eventIdentifier} column. Defaults to {@code eventIdentifier}.
         *
         * @param eventIdentifierColumn The name for the {@code eventIdentifier} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder eventIdentifierColumn(String eventIdentifierColumn) {
            assertNonEmpty(eventIdentifierColumn, "The eventIdentifierColumn should be not null or empty");
            this.eventIdentifierColumn = eventIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the {@code type} column. Defaults to {@code type}.
         *
         * @param typeColumn The name for the {@code type} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder typeColumn(String typeColumn) {
            assertNonEmpty(typeColumn, "The typeColumn should be not null or empty");
            this.typeColumn = typeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code timestamp} column. Defaults to {@code timestamp}.
         *
         * @param timestampColumn The name for the {@code timeStamp} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder timestampColumn(String timestampColumn) {
            assertNonEmpty(timestampColumn, "The timestampColumn should be not null or empty");
            this.timeStampColumn = timestampColumn;
            return this;
        }

        /**
         * Sets the name of the {@code payloadType} column. Defaults to {@code payloadType}.
         *
         * @param payloadTypeColumn The name for the {@code payloadType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder payloadTypeColumn(String payloadTypeColumn) {
            assertNonEmpty(payloadTypeColumn, "The payloadTypeColumn should be not null or empty");
            this.payloadTypeColumn = payloadTypeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code payloadRevision} column. Defaults to {@code payloadRevision}.
         *
         * @param payloadRevisionColumn The name for the {@code payloadRevision} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder payloadRevisionColumn(String payloadRevisionColumn) {
            assertNonEmpty(payloadRevisionColumn, "The payloadRevisionColumn should be not null or empty");
            this.payloadRevisionColumn = payloadRevisionColumn;
            return this;
        }

        /**
         * Sets the name of the {@code payload} column. Defaults to {@code payload}.
         *
         * @param payloadColumn The name for the {@code payload} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder payloadColumn(String payloadColumn) {
            assertNonEmpty(payloadColumn, "The payloadColumn should be not null or empty");
            this.payloadColumn = payloadColumn;
            return this;
        }

        /**
         * Sets the name of the {@code metaData} column. Defaults to {@code metaData}.
         *
         * @param metaDataColumn The name for the {@code metaData} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder metaDataColumn(String metaDataColumn) {
            assertNonEmpty(metaDataColumn, "The metaDataColumn should be not null or empty");
            this.metaDataColumn = metaDataColumn;
            return this;
        }

        /**
         * Sets the name of the {@code aggregateType} column. Defaults to {@code type}.
         *
         * @param aggregateTypeColumn The name for the {@code aggregateType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder aggregateTypeColumn(String aggregateTypeColumn) {
            assertNonEmpty(aggregateTypeColumn, "The aggregateTypeColumn should be not null or empty");
            this.aggregateTypeColumn = aggregateTypeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code aggregateIdentifier} column. Defaults to {@code aggregateIdentifier}.
         *
         * @param aggregateIdentifierColumn The name for the {@code aggregateIdentifier} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder aggregateIdentifierColumn(String aggregateIdentifierColumn) {
            assertNonEmpty(aggregateIdentifierColumn, "The aggregateIdentifierColumn should be not null or empty");
            this.aggregateIdentifierColumn = aggregateIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the {@code sequenceNumber} column. Defaults to {@code sequenceNumber}.
         *
         * @param sequenceNumberColumn The name for the {@code sequenceNumber} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder sequenceNumberColumn(String sequenceNumberColumn) {
            assertNonEmpty(sequenceNumberColumn, "The sequenceNumberColumn should be not null or empty");
            this.sequenceNumberColumn = sequenceNumberColumn;
            return this;
        }

        /**
         * Sets the name of the {@code tokenType} column. Defaults to {@code tokenType}.
         *
         * @param tokenTypeColumn The name for the {@code tokenType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder tokenTypeColumn(String tokenTypeColumn) {
            assertNonEmpty(tokenTypeColumn, "The tokenTypeColumn should be not null or empty");
            this.tokenTypeColumn = tokenTypeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code token} column. Defaults to {@code token}.
         *
         * @param tokenColumn The name for the {@code token} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder tokenColumn(String tokenColumn) {
            assertNonEmpty(tokenColumn, "The tokenColumn should be not null or empty");
            this.tokenColumn = tokenColumn;
            return this;
        }

        /**
         * Sets the name of the {@code enqueuedAt} column. Defaults to {@code enqueuedAt}.
         *
         * @param enqueuedAtColumn The name for the {@code enqueuedAt} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder enqueuedAtColumn(String enqueuedAtColumn) {
            assertNonEmpty(enqueuedAtColumn, "The enqueuedAtColumn should be not null or empty");
            this.enqueuedAtColumn = enqueuedAtColumn;
            return this;
        }

        /**
         * Sets the name of the {@code lastTouched} column. Defaults to {@code lastTouched}.
         *
         * @param lastTouchedColumn The name for the {@code lastTouched} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder lastTouchedColumn(String lastTouchedColumn) {
            assertNonEmpty(lastTouchedColumn, "The lastTouchedColumn should be not null or empty");
            this.lastTouchedColumn = lastTouchedColumn;
            return this;
        }

        /**
         * Sets the name of the {@code processingStarted} column. Defaults to {@code processingStarted}.
         *
         * @param processingStartedColumn The name for the {@code processingStarted} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder processingStartedColumn(String processingStartedColumn) {
            assertNonEmpty(processingStartedColumn, "The processingStartedColumn should be not null or empty");
            this.processingStartedColumn = processingStartedColumn;
            return this;
        }

        /**
         * Sets the name of the {@code causeType} column. Defaults to {@code causeType}.
         *
         * @param causeTypeColumn The name for the {@code causeType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder causeTypeColumn(String causeTypeColumn) {
            assertNonEmpty(causeTypeColumn, "The causeTypeColumn should be not null or empty");
            this.causeTypeColumn = causeTypeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code causeMessage} column. Defaults to {@code causeMessage}.
         *
         * @param causeMessageColumn The name for the {@code causeMessage} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder causeMessageColumn(String causeMessageColumn) {
            assertNonEmpty(causeMessageColumn, "The causeMessageColumn should be not null or empty");
            this.causeMessageColumn = causeMessageColumn;
            return this;
        }

        /**
         * Sets the name of the {@code diagnostics} column. Defaults to {@code diagnostics}.
         *
         * @param diagnosticsColumn The name for the {@code diagnostics} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder diagnosticsColumn(String diagnosticsColumn) {
            assertNonEmpty(diagnosticsColumn, "The diagnosticsColumn should be not null or empty");
            this.diagnosticsColumn = diagnosticsColumn;
            return this;
        }

        /**
         * Initializes a {@link DeadLetterSchema} as specified through this Builder.
         *
         * @return A {@link DeadLetterSchema} as specified through this Builder.
         */
        public DeadLetterSchema build() {
            return new DeadLetterSchema(this);
        }
    }
}
