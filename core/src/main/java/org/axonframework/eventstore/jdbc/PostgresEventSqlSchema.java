package org.axonframework.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * SQL schema supporting postgres databases.
 * <p/>
 * The difference to the GenericEventSqlSchema is the use of postgres' <code>bytea</code> data type
 * for storing the serialized payload and metaData. A human-readable representation of this data can
 * be accessed by using postgres encode(column, 'escape') function.
 *
 * @param <T> The type used when storing serialized data
 * @author Jochen Munz
 * @since 2.4
 */
public class PostgresEventSqlSchema<T> extends GenericEventSqlSchema<T> {

    /**
     * Initialize a Postgres Schema using default settings.
     * <p/>
     * Serialized data is stored as byte arrays. Data is stored in a default SchemaConfiguration.
     */
    public PostgresEventSqlSchema() {
    }

    /**
     * Initialize a Postgres Schema.
     * <p/>
     * Serialized data is stored using the given <code>dataType</code>. Data is stored in a default
     * SchemaConfiguration.
     *
     * @param dataType The type to use when storing serialized data
     */
    public PostgresEventSqlSchema(Class<T> dataType) {
        super(dataType);
    }

    /**
     * Initialize a Postgres Schema.
     * <p/>
     * Serialized data is stored using the given <code>dataType</code>. Data is stored according to the given
     * SchemaConfiguration.
     *
     * @param dataType            The type to use when storing serialized data
     * @param schemaConfiguration The configuration for this schema
     */
    public PostgresEventSqlSchema(Class<T> dataType, SchemaConfiguration schemaConfiguration) {
        super(dataType, schemaConfiguration);
    }

    @Override
    public PreparedStatement sql_createSnapshotEventEntryTable(Connection connection) throws SQLException {
        final String sql = "create table " + getSchemaConfiguration().snapshotEntryTable() + " (" +
                "        aggregateIdentifier varchar(255) not null," +
                "        sequenceNumber bigint not null," +
                "        eventIdentifier varchar(255) not null," +
                "        metaData bytea," +
                "        payload bytea not null," +
                "        payloadRevision varchar(255)," +
                "        payloadType varchar(255) not null," +
                "        timeStamp varchar(255) not null," +
                "        primary key (aggregateIdentifier, sequenceNumber)" +
                "    );";
        return connection.prepareStatement(sql);
    }

    @Override
    public PreparedStatement sql_createDomainEventEntryTable(Connection connection) throws SQLException {
        final String sql = "create table " + getSchemaConfiguration().domainEventEntryTable() + " (" +
                "        aggregateIdentifier varchar(255) not null," +
                "        sequenceNumber bigint not null," +
                "        eventIdentifier varchar(255) not null," +
                "        metaData bytea," +
                "        payload bytea not null," +
                "        payloadRevision varchar(255)," +
                "        payloadType varchar(255) not null," +
                "        timeStamp varchar(255) not null," +
                "        primary key (aggregateIdentifier, sequenceNumber)" +
                "    );";
        return connection.prepareStatement(sql);
    }
}
