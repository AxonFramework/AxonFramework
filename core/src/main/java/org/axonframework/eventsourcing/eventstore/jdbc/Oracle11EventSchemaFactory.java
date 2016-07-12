package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.common.jdbc.Oracle11Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Oracle 11 doesn't support the data type BIGINT, so NUMBER(19) is used as a substitute instead. Also Oracle doesn't
 * seem to like colons in create table statements, so those have been removed.
 */
public class Oracle11EventSchemaFactory extends AbstractEventSchemaFactory {

    @Override
    public PreparedStatement createDomainEventTable(Connection connection, EventSchema configuration) throws SQLException {
        String sql = "CREATE TABLE " + configuration.domainEventTable() + " (\n" +
                configuration.globalIndexColumn() + " NUMBER(19) " + autoIncrement() + " NOT NULL,\n" +
                configuration.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.sequenceNumberColumn() + " NUMBER(19) NOT NULL,\n" +
                configuration.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.metaDataColumn() + " " + payloadType() + ",\n" +
                configuration.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                configuration.payloadRevisionColumn() + " VARCHAR(255),\n" +
                configuration.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + configuration.globalIndexColumn() + "),\n" +
                "UNIQUE (" + configuration.aggregateIdentifierColumn() + ", " +
                configuration.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + configuration.eventIdentifierColumn() + ")\n" +
                ")";
        connection.prepareStatement(sql)
                .execute();

        Oracle11Utils.simulateAutoIncrement(connection, configuration.domainEventTable(), configuration.globalIndexColumn());

        return Oracle11Utils.createNullStatement(connection);
    }

    @Override
    public PreparedStatement createSnapshotEventTable(Connection connection, EventSchema configuration) throws SQLException {
        String sql = "CREATE TABLE " + configuration.snapshotTable() + " (\n" +
                configuration.aggregateIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.sequenceNumberColumn() + " NUMBER(19) NOT NULL,\n" +
                configuration.typeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.metaDataColumn() + " " + payloadType() + ",\n" +
                configuration.payloadColumn() + " " + payloadType() + " NOT NULL,\n" +
                configuration.payloadRevisionColumn() + " VARCHAR(255),\n" +
                configuration.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                configuration.timestampColumn() + " VARCHAR(255) NOT NULL,\n" +
                "PRIMARY KEY (" + configuration.aggregateIdentifierColumn() + ", " +
                configuration.sequenceNumberColumn() + "),\n" +
                "UNIQUE (" + configuration.eventIdentifierColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    @Override
    protected String autoIncrement() {
        return "";
    }

    @Override
    protected String payloadType() {
        return "BLOB";
    }
}
