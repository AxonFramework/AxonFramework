package org.axonframework.eventstore.jdbc;

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

    private final String eventEntryTable;
    private final String snapshotEntryTable;

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
}
