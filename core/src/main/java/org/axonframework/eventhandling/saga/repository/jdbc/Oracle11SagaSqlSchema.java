package org.axonframework.eventhandling.saga.repository.jdbc;

import org.axonframework.common.jdbc.Oracle11Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Oracle 11 does not support AUTO_INCREMENT. A workaround is used in this class in order to create the same behavior.
 * Oracle 11 does not like semicolons at the end of a CREATE TABLE statement. These semicolons are dropped in this
 * class.
 */
public class Oracle11SagaSqlSchema extends GenericSagaSqlSchema {

    public Oracle11SagaSqlSchema(SchemaConfiguration schemaConfiguration) {
        super(schemaConfiguration);
    }

    @Override
    public PreparedStatement sql_createTableAssocValueEntry(Connection conn) throws SQLException {
        conn.prepareStatement("create table " + schemaConfiguration.assocValueEntryTable() + " (\n" +
                "        id number(38) not null,\n" +
                "        associationKey varchar(255),\n" +
                "        associationValue varchar(255),\n" +
                "        sagaId varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        primary key (id)\n" +
                "    )").executeUpdate();

        Oracle11Utils.simulateAutoIncrement(conn, schemaConfiguration.assocValueEntryTable(), "id");

        return Oracle11Utils.createNullStatement(conn);
    }

    @Override
    public PreparedStatement sql_createTableSagaEntry(final Connection conn) throws SQLException {
        return conn.prepareStatement("create table " + schemaConfiguration.sagaEntryTable() + " (\n" +
                "        sagaId varchar(255) not null,\n" +
                "        revision varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        serializedSaga blob,\n" +
                "        primary key (sagaId)\n" +
                "    )");
    }
}