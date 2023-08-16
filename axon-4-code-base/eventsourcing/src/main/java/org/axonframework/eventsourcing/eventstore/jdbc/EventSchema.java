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

package org.axonframework.eventsourcing.eventstore.jdbc;

import java.util.function.Function;

/**
 * Schema of an event entry to be stored using Jdbc.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class EventSchema {

    private final String domainEventTable, snapshotTable, globalIndexColumn, timestampColumn, eventIdentifierColumn,
            aggregateIdentifierColumn, sequenceNumberColumn, typeColumn, payloadTypeColumn, payloadRevisionColumn,
            payloadColumn, metaDataColumn;

    private final Function<EventSchema, String> domainEventFields, trackedEventFields;

    /**
     * Initializes the default Event Schema
     */
    public EventSchema() {
        this(builder());
    }

    private EventSchema(Builder builder) {
        domainEventTable = builder.domainEventTable;
        snapshotTable = builder.snapshotTable;
        globalIndexColumn = builder.globalIndexColumn;
        timestampColumn = builder.timestampColumn;
        eventIdentifierColumn = builder.eventIdentifierColumn;
        aggregateIdentifierColumn = builder.aggregateIdentifierColumn;
        sequenceNumberColumn = builder.sequenceNumberColumn;
        typeColumn = builder.typeColumn;
        payloadTypeColumn = builder.payloadTypeColumn;
        payloadRevisionColumn = builder.payloadRevisionColumn;
        payloadColumn = builder.payloadColumn;
        metaDataColumn = builder.metaDataColumn;
        domainEventFields = builder.domainEventFields;
        trackedEventFields = builder.trackedEventFields;
    }

    /**
     * Returns a new {@link Builder} initialized with default settings.
     *
     * @return a new builder for the event schema
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the name of the domain event table.
     *
     * @return the name of the domain event table
     */
    public String domainEventTable() {
        return domainEventTable;
    }

    /**
     * Returns the name of the snapshot event table.
     *
     * @return the name of the snapshot event table
     */
    public String snapshotTable() {
        return snapshotTable;
    }

    /**
     * Get the name of the column containing the global index of the event.
     *
     * @return the name of the column containing the global index
     */
    public String globalIndexColumn() {
        return globalIndexColumn;
    }

    /**
     * Get the name of the column containing the timestamp of the event.
     *
     * @return the name of the column containing the timestamp
     */
    public String timestampColumn() {
        return timestampColumn;
    }

    /**
     * Get the name of the column containing the identifier of the event.
     *
     * @return the name of the column containing the event identifier
     */
    public String eventIdentifierColumn() {
        return eventIdentifierColumn;
    }

    /**
     * Get the name of the column containing the aggregate identifier of the event.
     *
     * @return the name of the column containing the aggregate identifier
     */
    public String aggregateIdentifierColumn() {
        return aggregateIdentifierColumn;
    }

    /**
     * Get the name of the column containing the aggregate sequence number of the event.
     *
     * @return the name of the column containing the aggregate sequence number
     */
    public String sequenceNumberColumn() {
        return sequenceNumberColumn;
    }

    /**
     * Get the name of the column containing the aggregate type of the event.
     *
     * @return the name of the column containing the aggregate type
     */
    public String typeColumn() {
        return typeColumn;
    }

    /**
     * Get the name of the column containing the event payload type.
     *
     * @return the name of the column containing the payload type
     */
    public String payloadTypeColumn() {
        return payloadTypeColumn;
    }

    /**
     * Get the name of the column containing the revision number of the serialized payload.
     *
     * @return the name of the column containing the revision number of the serialized payload
     */
    public String payloadRevisionColumn() {
        return payloadRevisionColumn;
    }

    /**
     * Get the name of the column containing the serialized payload of the event.
     *
     * @return the name of the column containing the serialized payload
     */
    public String payloadColumn() {
        return payloadColumn;
    }

    /**
     * Get the name of the column containing the serialized metadata of the event.
     *
     * @return the name of the column containing the serialized metadata
     */
    public String metaDataColumn() {
        return metaDataColumn;
    }

    /**
     * Get a comma separated list of domain event column names to select from an event or snapshot entry.
     *
     * @return comma separated domain event column names.
     */
    public String domainEventFields() {
        return domainEventFields.apply(this);
    }

    /**
     * Get a comma separated list of tracked domain event column names to select from an event entry.
     *
     * @return comma separated tracked domain event column names.
     */
    public String trackedEventFields() {
        return trackedEventFields.apply(this);
    }

    /**
     * Builder for an {@link EventSchema} that gets initialized with default values.
     */
    @SuppressWarnings("SqlResolve")
    public static class Builder {

        private String domainEventTable = "DomainEventEntry";
        private String snapshotTable = "SnapshotEventEntry";
        private String globalIndexColumn = "globalIndex";
        private String timestampColumn = "timeStamp";
        private String eventIdentifierColumn = "eventIdentifier";
        private String aggregateIdentifierColumn = "aggregateIdentifier";
        private String sequenceNumberColumn = "sequenceNumber";
        private String typeColumn = "type";
        private String payloadTypeColumn = "payloadType";
        private String payloadRevisionColumn = "payloadRevision";
        private String payloadColumn = "payload";
        private String metaDataColumn = "metaData";

        private Function<EventSchema, String> domainEventFields = eventSchema ->
                String.join(", ",
                            eventSchema.eventIdentifierColumn(),
                            eventSchema.aggregateIdentifierColumn(),
                            eventSchema.sequenceNumberColumn(),
                            eventSchema.typeColumn(),
                            eventSchema.timestampColumn(),
                            eventSchema.payloadTypeColumn(),
                            eventSchema.payloadRevisionColumn(),
                            eventSchema.payloadColumn(),
                            eventSchema.metaDataColumn());

        private Function<EventSchema, String> trackedEventFields = eventSchema ->
                eventSchema.globalIndexColumn() + ", " + eventSchema.domainEventFields();

        /**
         * Sets the name of the domain events table. Defaults to 'DomainEventEntry'.
         *
         * @param eventTable the event table name
         * @return the modified Builder instance
         */
        public Builder eventTable(String eventTable) {
            this.domainEventTable = eventTable;
            return this;
        }

        /**
         * Sets the name of the snapshot events table. Defaults to 'SnapshotEventEntry'.
         *
         * @param snapshotTable the snapshot table name
         * @return the modified Builder instance
         */
        public Builder snapshotTable(String snapshotTable) {
            this.snapshotTable = snapshotTable;
            return this;
        }

        /**
         * Sets the name of the global index column. Defaults to 'globalIndex'.
         *
         * @param globalIndexColumn the name of the global index column.
         * @return the modified Builder instance
         */
        public Builder globalIndexColumn(String globalIndexColumn) {
            this.globalIndexColumn = globalIndexColumn;
            return this;
        }

        /**
         * Sets the name of the timestamp column. Defaults to 'timeStamp'.
         *
         * @param timestampColumn the name of the timestamp column.
         * @return the modified Builder instance
         */
        public Builder timestampColumn(String timestampColumn) {
            this.timestampColumn = timestampColumn;
            return this;
        }

        /**
         * Sets the name of the event identifier column. Defaults to 'eventIdentifier'.
         *
         * @param eventIdentifierColumn the name of the event identifier column.
         * @return the modified Builder instance
         */
        public Builder eventIdentifierColumn(String eventIdentifierColumn) {
            this.eventIdentifierColumn = eventIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the event identifier column. Defaults to 'aggregateIdentifier'.
         *
         * @param aggregateIdentifierColumn the name of the aggregate identifier column.
         * @return the modified Builder instance
         */
        public Builder aggregateIdentifierColumn(String aggregateIdentifierColumn) {
            this.aggregateIdentifierColumn = aggregateIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the event identifier column. Defaults to 'sequenceNumber'.
         *
         * @param sequenceNumberColumn the name of the sequence number column.
         * @return the modified Builder instance
         */
        public Builder sequenceNumberColumn(String sequenceNumberColumn) {
            this.sequenceNumberColumn = sequenceNumberColumn;
            return this;
        }

        /**
         * Sets the name of the aggregate type column. Defaults to 'type'.
         *
         * @param typeColumn the name of the aggregate type column.
         * @return the modified Builder instance
         */
        public Builder typeColumn(String typeColumn) {
            this.typeColumn = typeColumn;
            return this;
        }

        /**
         * Sets the name of the event payload type column. Defaults to 'payloadType'.
         *
         * @param payloadTypeColumn the name of the payload type column.
         * @return the modified Builder instance
         */
        public Builder payloadTypeColumn(String payloadTypeColumn) {
            this.payloadTypeColumn = payloadTypeColumn;
            return this;
        }

        /**
         * Sets the name of the event payload revision column. Defaults to 'payloadRevision'.
         *
         * @param payloadRevisionColumn the name of the payload revision column.
         * @return the modified Builder instance
         */
        public Builder payloadRevisionColumn(String payloadRevisionColumn) {
            this.payloadRevisionColumn = payloadRevisionColumn;
            return this;
        }

        /**
         * Sets the name of the event payload column. Defaults to 'payload'.
         *
         * @param payloadColumn the name of the payload column.
         * @return the modified Builder instance
         */
        public Builder payloadColumn(String payloadColumn) {
            this.payloadColumn = payloadColumn;
            return this;
        }

        /**
         * Sets the name of the event metadata column. Defaults to 'metaData'.
         *
         * @param metaDataColumn the name of the metadata column.
         * @return the modified Builder instance
         */
        public Builder metaDataColumn(String metaDataColumn) {
            this.metaDataColumn = metaDataColumn;
            return this;
        }

        /**
         * Set a comma separated list of domain event column names to select from an event or snapshot entry. Defaults
         * to:
         * <p/>
         * {@code "[eventIdentifierColumn], [aggregateIdentifierColumn], [sequenceNumberColumn], [typeColumn],
         * [timestampColumn], [payloadTypeColumn], [payloadRevisionColumn], [payloadColumn], [metaDataColumn]" }
         * <p/>
         *
         * @return the modified Builder instance
         */
        public Builder domainEventFields(Function<EventSchema, String> domainEventFields) {
            this.domainEventFields = domainEventFields;
            return this;
        }

        /**
         * Set a comma separated list of tracked domain event column names to select from an event entry. Defaults to:
         * <p/>
         * {@code "[globalIndexColumn], [domainEventFields]" }
         * <p/>
         *
         * @return the modified Builder instance
         */
        public Builder trackedEventFields(Function<EventSchema, String> trackedEventFields) {
            this.trackedEventFields = trackedEventFields;
            return this;
        }

        /**
         * Builds a new {@link EventSchema} from builder values.
         *
         * @return new EventSchema
         */
        public EventSchema build() {
            return new EventSchema(this);
        }
    }
}
