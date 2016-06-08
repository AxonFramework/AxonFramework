package org.axonframework.eventhandling.saga.repository.jdbc;

/**
 * SchemaConfiguration allows specification of custom storage locations for the saga repositories.
 * <p/>
 * Usage depends on the underlying database - in general the form of "schema.tablename" should be sufficient.
 *
 * @author Jochen Munz
 * @since 2.4
 */
public class SchemaConfiguration {

    public static final String DEFAULT_SAGA_ENTRY_TABLE = "SagaEntry";
    public static final String DEFAULT_ASSOC_VALUE_ENTRY_TABLE = "AssociationValueEntry";

    private final String sagaEntryTable;

    private final String assocValueEntryTable;

    /**
     * Initialize SchemaConfiguration with default values.
     */
    public SchemaConfiguration() {
        this(DEFAULT_SAGA_ENTRY_TABLE, DEFAULT_ASSOC_VALUE_ENTRY_TABLE);
    }

    /**
     * Initialize SchemaConfiguration with custom locations for event entry tables.
     *
     * @param sagaEntryTable
     * @param assocValueEntryTable
     */
    public SchemaConfiguration(String sagaEntryTable, String assocValueEntryTable) {
        this.sagaEntryTable = sagaEntryTable;
        this.assocValueEntryTable = assocValueEntryTable;
    }

    public String assocValueEntryTable() {
        return assocValueEntryTable;
    }

    public String sagaEntryTable() {
        return sagaEntryTable;
    }
}
