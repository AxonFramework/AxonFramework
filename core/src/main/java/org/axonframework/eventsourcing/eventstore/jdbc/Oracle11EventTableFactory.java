package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.common.jdbc.Oracle11Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Oracle 11 doesn't support the data type BIGINT, so NUMBER(19) is used as a substitute instead. Also Oracle doesn't
 * seem to like colons in create table statements, so those have been removed.
 */
public class Oracle11EventTableFactory extends AbstractEventTableFactory {

    @Override
    public PreparedStatement createDomainEventTable(Connection connection, EventSchema schema) throws SQLException {
        String sql = "CREATE TABLE " + schema.domainEventTable() + " (\n" +
                schema.globalIndexColumn() + " NUMBER(19) NOT NULL,\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceNumberColumn() + " NUMBER(19) NOT NULL,\n" +
                schema.typeColumn() + " VARCHAR(255),\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.metaDataColumn() + " " + payloadType() + ",\n" +
                schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + schema.globalIndexColumn() + "),\n" +
                "UNIQUE (" + schema.aggregateIdentifierColumn() + ", " +
                schema.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                ")";
        connection.prepareStatement(sql)
                .execute();

        Oracle11Utils.simulateAutoIncrement(connection, schema.domainEventTable(), schema.globalIndexColumn());

        return Oracle11Utils.createNullStatement(connection);
    }

    @Override
    public PreparedStatement createSnapshotEventTable(Connection connection, EventSchema schema) throws SQLException {
        String sql = "CREATE TABLE " + schema.snapshotTable() + " (\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceNumberColumn() + " NUMBER(19) NOT NULL,\n" +
                schema.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.metaDataColumn() + " " + payloadType() + ",\n" +
                schema.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + schema.aggregateIdentifierColumn() + ", " +
                schema.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + schema.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    @Override
    protected String idColumnType() {
        return ""; // ignored
    }

    @Override
    protected String payloadType() {
        return "BLOB";
    }
}
