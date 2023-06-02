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

import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonBlank;

/**
 * Schema description for {@link org.axonframework.messaging.deadletter.DeadLetter} entry table in JDBC.
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
    // TODO move the event message columns to a DeadLetterEventSchema, perhaps?
    private final String messageTypeColumn;
    private final String eventIdentifierColumn;
    private final String timeStampColumn;
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
                        schema.messageTypeColumn(),
                        schema.eventIdentifierColumn(),
                        schema.timeStampColumn(),
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
        this.messageTypeColumn = builder.messageTypeColumn;
        this.eventIdentifierColumn = builder.eventIdentifierColumn;
        this.timeStampColumn = builder.timeStampColumn;
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
     * Returns the configured {@code deadLetterTable} name.
     *
     * @return The configured {@code deadLetterTable} name.
     */
    public String deadLetterTable() {
        return deadLetterTable;
    }

    /**
     * Returns the configured {@code deadLetterIdentifierColumn} name.
     *
     * @return The configured {@code deadLetterIdentifierColumn} name.
     */
    public String deadLetterIdentifierColumn() {
        return deadLetterIdentifierColumn;
    }

    /**
     * Returns the configured {@code processingGroupColumn} name.
     *
     * @return The configured {@code processingGroupColumn} name.
     */
    public String processingGroupColumn() {
        return processingGroupColumn;
    }

    /**
     * Returns the configured {@code sequenceIdentifierColumn} name.
     *
     * @return The configured {@code sequenceIdentifierColumn} name.
     */
    public String sequenceIdentifierColumn() {
        return sequenceIdentifierColumn;
    }

    /**
     * Returns the configured {@code sequenceIndexColumn} name.
     *
     * @return The configured {@code sequenceIndexColumn} name.
     */
    public String sequenceIndexColumn() {
        return sequenceIndexColumn;
    }

    /**
     * Returns the configured {@code messageTypeColumn} name.
     *
     * @return The configured {@code messageTypeColumn} name.
     */
    public String messageTypeColumn() {
        return messageTypeColumn;
    }

    /**
     * Returns the configured {@code eventIdentifierColumn} name.
     *
     * @return The configured {@code eventIdentifierColumn} name.
     */
    public String eventIdentifierColumn() {
        return eventIdentifierColumn;
    }

    /**
     * Returns the configured {@code timeStampColumn} name.
     *
     * @return The configured {@code timeStampColumn} name.
     */
    public String timeStampColumn() {
        return timeStampColumn;
    }

    /**
     * Returns the configured {@code payloadTypeColumn} name.
     *
     * @return The configured {@code payloadTypeColumn} name.
     */
    public String payloadTypeColumn() {
        return payloadTypeColumn;
    }

    /**
     * Returns the configured {@code payloadRevisionColumn} name.
     *
     * @return The configured {@code payloadRevisionColumn} name.
     */
    public String payloadRevisionColumn() {
        return payloadRevisionColumn;
    }

    /**
     * Returns the configured {@code payloadColumn} name.
     *
     * @return The configured {@code payloadColumn} name.
     */
    public String payloadColumn() {
        return payloadColumn;
    }

    /**
     * Returns the configured {@code metaDataColumn} name.
     *
     * @return The configured {@code metaDataColumn} name.
     */
    public String metaDataColumn() {
        return metaDataColumn;
    }

    /**
     * Returns the configured {@code aggregateTypeColumn} name.
     *
     * @return The configured {@code aggregateTypeColumn} name.
     */
    public String aggregateTypeColumn() {
        return aggregateTypeColumn;
    }

    /**
     * Returns the configured {@code aggregateIdentifierColumn} name.
     *
     * @return The configured {@code aggregateIdentifierColumn} name.
     */
    public String aggregateIdentifierColumn() {
        return aggregateIdentifierColumn;
    }

    /**
     * Returns the configured {@code sequenceNumberColumn} name.
     *
     * @return The configured {@code sequenceNumberColumn} name.
     */
    public String sequenceNumberColumn() {
        return sequenceNumberColumn;
    }

    /**
     * Returns the configured {@code tokenTypeColumn} name.
     *
     * @return The configured {@code tokenTypeColumn} name.
     */
    public String tokenTypeColumn() {
        return tokenTypeColumn;
    }

    /**
     * Returns the configured {@code tokenColumn} name.
     *
     * @return The configured {@code tokenColumn} name.
     */
    public String tokenColumn() {
        return tokenColumn;
    }

    /**
     * Returns the configured {@code enqueuedAtColumn} name.
     *
     * @return The configured {@code enqueuedAtColumn} name.
     */
    public String enqueuedAtColumn() {
        return enqueuedAtColumn;
    }

    /**
     * Returns the configured {@code lastTouchedColumn} name.
     *
     * @return The configured {@code lastTouchedColumn} name.
     */
    public String lastTouchedColumn() {
        return lastTouchedColumn;
    }

    /**
     * Returns the configured {@code processingStartedColumn} name.
     *
     * @return The configured {@code processingStartedColumn} name.
     */
    public String processingStartedColumn() {
        return processingStartedColumn;
    }

    /**
     * Returns the configured {@code causeTypeColumn} name.
     *
     * @return The configured {@code causeTypeColumn} name.
     */
    public String causeTypeColumn() {
        return causeTypeColumn;
    }

    /**
     * Returns the configured {@code causeMessageColumn} name.
     *
     * @return The configured {@code causeMessageColumn} name.
     */
    public String causeMessageColumn() {
        return causeMessageColumn;
    }

    /**
     * Returns the configured {@code diagnosticsColumn} name.
     *
     * @return The configured {@code diagnosticsColumn} name.
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
        private String messageTypeColumn = "messageType";
        private String eventIdentifierColumn = "eventIdentifier";
        private String timeStampColumn = "timeStamp";
        private String payloadTypeColumn = "payloadType";
        private String payloadRevisionColumn = "payloadRevision";
        private String payloadColumn = "payload";
        private String metaDataColumn = "metaData";
        private String aggregateTypeColumn = "type";
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
            assertNonBlank(deadLetterTable, "The DeadLetterEntryColumn should be not null or empty");
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
            assertNonBlank(deadLetterIdentifierColumn, "The deadLetterIdentifierColumn should be not null or empty");
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
            assertNonBlank(processingGroupColumn, "The processingGroupColumn should be not null or empty");
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
            assertNonBlank(sequenceIdentifierColumn, "The sequenceIdentifierColumn should be not null or empty");
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
            assertNonBlank(sequenceIndexColumn, "The sequenceIndexColumn should be not null or empty");
            this.sequenceIndexColumn = sequenceIndexColumn;
            return this;
        }

        /**
         * Sets the name of the {@code messageType} column. Defaults to {@code messageType}.
         *
         * @param messageTypeColumn The name for the {@code messageType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageTypeColumn(String messageTypeColumn) {
            assertNonBlank(messageTypeColumn, "The messageTypeColumn should be not null or empty");
            this.messageTypeColumn = messageTypeColumn;
            return this;
        }

        /**
         * Sets the name of the {@code eventIdentifier} column. Defaults to {@code eventIdentifier}.
         *
         * @param eventIdentifierColumn The name for the {@code eventIdentifier} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder eventIdentifierColumn(String eventIdentifierColumn) {
            assertNonBlank(eventIdentifierColumn, "The eventIdentifierColumn should be not null or empty");
            this.eventIdentifierColumn = eventIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the {@code timeStamp} column. Defaults to {@code timeStamp}.
         *
         * @param timeStampColumn The name for the {@code timeStamp} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder timeStampColumn(String timeStampColumn) {
            assertNonBlank(timeStampColumn, "The timeStampColumn should be not null or empty");
            this.timeStampColumn = timeStampColumn;
            return this;
        }

        /**
         * Sets the name of the {@code payloadType} column. Defaults to {@code payloadType}.
         *
         * @param payloadTypeColumn The name for the {@code payloadType} column.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder payloadTypeColumn(String payloadTypeColumn) {
            assertNonBlank(payloadTypeColumn, "The payloadTypeColumn should be not null or empty");
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
            assertNonBlank(payloadRevisionColumn, "The payloadRevisionColumn should be not null or empty");
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
            assertNonBlank(payloadColumn, "The payloadColumn should be not null or empty");
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
            assertNonBlank(metaDataColumn, "The metaDataColumn should be not null or empty");
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
            assertNonBlank(aggregateTypeColumn, "The aggregateTypeColumn should be not null or empty");
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
            assertNonBlank(aggregateIdentifierColumn, "The aggregateIdentifierColumn should be not null or empty");
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
            assertNonBlank(sequenceNumberColumn, "The sequenceNumberColumn should be not null or empty");
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
            assertNonBlank(tokenTypeColumn, "The tokenTypeColumn should be not null or empty");
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
            assertNonBlank(tokenColumn, "The tokenColumn should be not null or empty");
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
            assertNonBlank(enqueuedAtColumn, "The enqueuedAtColumn should be not null or empty");
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
            assertNonBlank(lastTouchedColumn, "The lastTouchedColumn should be not null or empty");
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
            assertNonBlank(processingStartedColumn, "The processingStartedColumn should be not null or empty");
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
            assertNonBlank(causeTypeColumn, "The causeTypeColumn should be not null or empty");
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
            assertNonBlank(causeMessageColumn, "The causeMessageColumn should be not null or empty");
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
            assertNonBlank(diagnosticsColumn, "The diagnosticsColumn should be not null or empty");
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
