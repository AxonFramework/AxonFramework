/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.jdbc;

/**
 * Schema of an event entry to be stored using Jdbc.
 *
 * @author Rene de Waele
 */
public class EventSchema {
    private final String domainEventTable, snapshotTable, globalIndexColumn, timestampColumn, eventIdentifierColumn,
            aggregateIdentifierColumn, sequenceNumberColumn, typeColumn, payloadTypeColumn, payloadRevisionColumn,
            payloadColumn, metaDataColumn;

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

    @SuppressWarnings("SqlResolve")
    protected static class Builder {
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

        public Builder withEventTable(String eventTable) {
            this.domainEventTable = eventTable;
            return this;
        }

        public Builder withSnapshotTable(String snapshotTable) {
            this.snapshotTable = snapshotTable;
            return this;
        }

        public Builder withGlobalIndexColumn(String globalIndexColumn) {
            this.globalIndexColumn = globalIndexColumn;
            return this;
        }

        public Builder withTimestampColumn(String timestampColumn) {
            this.timestampColumn = timestampColumn;
            return this;
        }

        public Builder withEventIdentifierColumn(String eventIdentifierColumn) {
            this.eventIdentifierColumn = eventIdentifierColumn;
            return this;
        }

        public Builder withAggregateIdentifierColumn(String aggregateIdentifierColumn) {
            this.aggregateIdentifierColumn = aggregateIdentifierColumn;
            return this;
        }

        public Builder withSequenceNumberColumn(String sequenceNumberColumn) {
            this.sequenceNumberColumn = sequenceNumberColumn;
            return this;
        }

        public Builder withTypeColumn(String typeColumn) {
            this.typeColumn = typeColumn;
            return this;
        }

        public Builder withPayloadTypeColumn(String payloadTypeColumn) {
            this.payloadTypeColumn = payloadTypeColumn;
            return this;
        }

        public Builder withPayloadRevisionColumn(String payloadRevisionColumn) {
            this.payloadRevisionColumn = payloadRevisionColumn;
            return this;
        }

        public Builder withPayloadColumn(String payloadColumn) {
            this.payloadColumn = payloadColumn;
            return this;
        }

        public Builder withMetaDataColumn(String metaDataColumn) {
            this.metaDataColumn = metaDataColumn;
            return this;
        }

        public EventSchema build() {
            return new EventSchema(this);
        }
    }
}
