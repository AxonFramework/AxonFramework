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
 * @author Rene de Waele
 */
public class EventSchemaConfiguration {
    private final String domainEventTable, snapshotTable, globalIndexColumn, timestampColumn, eventIdentifierColumn,
            aggregateIdentifierColumn, sequenceNumberColumn, typeColumn, payloadTypeColumn, payloadRevisionColumn,
            payloadColumn, metaDataColumn;

    public EventSchemaConfiguration() {
        this(builder());
    }

    private EventSchemaConfiguration(Builder builder) {
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

    public static Builder builder() {
        return new Builder();
    }

    public String domainEventTable() {
        return domainEventTable;
    }

    public String snapshotTable() {
        return snapshotTable;
    }

    public String globalIndexColumn() {
        return globalIndexColumn;
    }

    public String timestampColumn() {
        return timestampColumn;
    }

    public String eventIdentifierColumn() {
        return eventIdentifierColumn;
    }

    public String aggregateIdentifierColumn() {
        return aggregateIdentifierColumn;
    }

    public String sequenceNumberColumn() {
        return sequenceNumberColumn;
    }

    public String typeColumn() {
        return typeColumn;
    }

    public String payloadTypeColumn() {
        return payloadTypeColumn;
    }

    public String payloadRevisionColumn() {
        return payloadRevisionColumn;
    }

    public String payloadColumn() {
        return payloadColumn;
    }

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

        public EventSchemaConfiguration build() {
            return new EventSchemaConfiguration(this);
        }
    }
}
