package org.axonframework.eventstore.jdbc;

import java.util.regex.Pattern;

/**
 * SchemaConfiguration allows specification of custom storage locations for domain event
 * and snapshot event entries.
 * <p/>
 * Usage depends on the underlying database - in general the form of "schema.tablename"
 * should be sufficient.
 *
 * @author Jochen Munz
 */
public class SchemaConfiguration {

    public static final String DEFAULT_DOMAINEVENT_TABLE = "DomainEventEntry";
    public static final String DEFAULT_SNAPSHOTEVENT_TABLE = "SnapshotEventEntry";

    public static final String DEFAULT_EVENT_IDENTIFIER_COLUMN = "eventIdentifier";
    public static final String DEFAULT_AGGREGATE_IDENTIFIER_COLUMN = "aggregateIdentifier";
    public static final String DEFAULT_SEQUENCE_NUMBER_COLUMN = "sequenceNumber";
    public static final String DEFAULT_TYPE_COLUMN = "type";
    public static final String DEFAULT_TIME_STAMP_COLUMN = "timeStamp";
    public static final String DEFAULT_PAYLOAD_TYPE_COLUMN = "payloadType";
    public static final String DEFAULT_PAYLOAD_REVISION_COLUMN = "payloadRevision";
    public static final String DEFAULT_PAYLOAD_COLUMN = "payload";
    public static final String DEFAULT_METADATA_COLUMN = "metaData";

    private String eventEntryTable = DEFAULT_DOMAINEVENT_TABLE;
    private String snapshotEntryTable = DEFAULT_SNAPSHOTEVENT_TABLE;

    private String eventIdentifierColumn = DEFAULT_EVENT_IDENTIFIER_COLUMN;
    private String typeColumn = DEFAULT_TYPE_COLUMN;
    private String aggregateIdentifierColumn = DEFAULT_AGGREGATE_IDENTIFIER_COLUMN;
    private String sequenceNumberColumn = DEFAULT_SEQUENCE_NUMBER_COLUMN;
    private String timeStampColumn = DEFAULT_TIME_STAMP_COLUMN;
    private String payloadTypeColumn = DEFAULT_PAYLOAD_TYPE_COLUMN;
    private String payloadRevisionColumn = DEFAULT_PAYLOAD_REVISION_COLUMN;
    private String payloadColumn = DEFAULT_PAYLOAD_COLUMN;
    private String metaDataColumn = DEFAULT_METADATA_COLUMN;
    private boolean isDefault = true;

    public static class Builder {
        private final SchemaConfiguration instance = new SchemaConfiguration();

        private Pattern PATTERN_UNDERSCORIFY = Pattern.compile("(.)(\\p{Upper})");
        private String doUnderscorify(String str) {
            return PATTERN_UNDERSCORIFY.matcher(str).replaceAll("$1_$2").toLowerCase();
        }

        public Builder setEventEntryTable(String eventEntryTable) {
            instance.eventEntryTable = eventEntryTable;
            return this;
        }

        public Builder setSnapshotEntryTable(String snapshotEntryTable) {
            instance.snapshotEntryTable = snapshotEntryTable;
            return this;
        }

        public Builder setEventIdentifierColumn(String eventIdentifierColumn) {
            instance.eventIdentifierColumn = eventIdentifierColumn;
            return this;
        }

        public Builder setAggregateIdentifierColumn(String aggregateIdentifierColumn) {
            instance.aggregateIdentifierColumn = aggregateIdentifierColumn;
            return this;
        }

        public Builder setSequenceNumberColumn(String sequenceNumberColumn) {
            instance.sequenceNumberColumn = sequenceNumberColumn;
            return this;
        }

        public Builder setTimeStampColumn(String timeStampColumn) {
            instance.timeStampColumn = timeStampColumn;
            return this;
        }

        public Builder setPayloadTypeColumn(String payloadTypeColumn) {
            instance.payloadTypeColumn = payloadTypeColumn;
            return this;
        }

        public Builder setPayloadRevisionColumn(String payloadRevisionColumn) {
            instance.payloadRevisionColumn = payloadRevisionColumn;
            return this;
        }

        public Builder setPayloadColumn(String payloadColumn) {
            instance.payloadColumn = payloadColumn;
            return this;
        }

        public Builder setMetaDataColumn(String metaDataColumn) {
            instance.metaDataColumn = metaDataColumn;
            return this;
        }

        public Builder setTypeColumn(String typeColumn) {
            instance.metaDataColumn = typeColumn;
            return this;
        }

        public Builder underscorify() {
            instance.eventEntryTable = doUnderscorify(instance.eventEntryTable);
            instance.snapshotEntryTable = doUnderscorify(instance.snapshotEntryTable);
            instance.eventIdentifierColumn = doUnderscorify(instance.eventIdentifierColumn);
            instance.typeColumn = doUnderscorify(instance.typeColumn);
            instance.aggregateIdentifierColumn = doUnderscorify(instance.aggregateIdentifierColumn);
            instance.sequenceNumberColumn = doUnderscorify(instance.sequenceNumberColumn);
            instance.timeStampColumn = doUnderscorify(instance.timeStampColumn);
            instance.payloadTypeColumn = doUnderscorify(instance.payloadTypeColumn);
            instance.payloadRevisionColumn = doUnderscorify(instance.payloadRevisionColumn);
            instance.payloadColumn = doUnderscorify(instance.payloadColumn);
            instance.metaDataColumn = doUnderscorify(instance.metaDataColumn);
            return this;
        }

        public SchemaConfiguration build() {
            instance.isDefault = false;
            return instance;
        }
    }

    /**
     * Initialize SchemaConfiguration with default values.
     */
    public SchemaConfiguration() {
        this(DEFAULT_DOMAINEVENT_TABLE, DEFAULT_SNAPSHOTEVENT_TABLE);
    }

    /**
     * Initialize SchemaConfiguration with custom locations for event entry tables.
     *
     * @param eventEntryTable
     * @param snapshotEntryTable
     */
    public SchemaConfiguration(String eventEntryTable, String snapshotEntryTable) {
        this.eventEntryTable = eventEntryTable;
        this.snapshotEntryTable = snapshotEntryTable;
    }

    public String domainEventEntryTable() {
        return eventEntryTable;
    }

    public String snapshotEntryTable() {
        return snapshotEntryTable;
    }

    public String eventIdentifierColumn() {
        return eventIdentifierColumn;
    }

    public String typeColumn() {
        return typeColumn;
    }

    public String aggregateIdentifierColumn() {
        return aggregateIdentifierColumn;
    }

    public String sequenceNumberColumn() {
        return sequenceNumberColumn;
    }

    public String timeStampColumn() {
        return timeStampColumn;
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

    public boolean isDefault() {
        return isDefault;
    }
}