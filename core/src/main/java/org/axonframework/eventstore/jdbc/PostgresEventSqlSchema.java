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
 * @author Jochen Munz
 */
public class PostgresEventSqlSchema<T> extends GenericEventSqlSchema<T> {

    public PostgresEventSqlSchema() {
    }

    public PostgresEventSqlSchema(Class<T> dataType) {
        super(dataType);
    }

    public PostgresEventSqlSchema(Class<T> dataType, SchemaConfiguration schemaConfiguration) {
        super(dataType, schemaConfiguration);
    }

    @Override
    public PreparedStatement sql_createSnapshotEventEntryTable(Connection connection) throws SQLException {
        final String sql = "create table " + schemaConfiguration.snapshotEntryTable() + " (" +
                "        aggregateIdentifier varchar(255) not null," +
                "        sequenceNumber bigint not null," +
                "        type varchar(255) not null," +
                "        eventIdentifier varchar(255) not null," +
                "        metaData bytea," +
                "        payload bytea not null," +
                "        payloadRevision varchar(255)," +
                "        payloadType varchar(255) not null," +
                "        timeStamp varchar(255) not null," +
                "        primary key (aggregateIdentifier, sequenceNumber, type)" +
                "    );";
        return connection.prepareStatement(sql);
    }

    @Override
    public PreparedStatement sql_createDomainEventEntryTable(Connection connection) throws SQLException {
        final String sql = "create table " + schemaConfiguration.domainEventEntryTable() + " (" +
                "        aggregateIdentifier varchar(255) not null," +
                "        sequenceNumber bigint not null," +
                "        type varchar(255) not null," +
                "        eventIdentifier varchar(255) not null," +
                "        metaData bytea," +
                "        payload bytea not null," +
                "        payloadRevision varchar(255)," +
                "        payloadType varchar(255) not null," +
                "        timeStamp varchar(255) not null," +
                "        primary key (aggregateIdentifier, sequenceNumber, type)" +
                "    );";
        return connection.prepareStatement(sql);
    }
}
