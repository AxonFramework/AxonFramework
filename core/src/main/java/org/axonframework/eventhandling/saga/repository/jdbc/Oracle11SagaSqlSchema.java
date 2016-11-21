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

    /**
     * Initialize a Oracle11SagaSqlSchema using the given {@code sagaSchema}.
     *
     * @param sagaSchema the saga schema configuration
     */
    public Oracle11SagaSqlSchema(SagaSchema sagaSchema) {
        super(sagaSchema);
    }

    @Override
    public PreparedStatement sql_createTableAssocValueEntry(Connection conn) throws SQLException {
        conn.prepareStatement("create table " + sagaSchema().associationValueEntryTable() + " (\n" +
                "        id number(38) not null,\n" +
                "        associationKey varchar(255),\n" +
                "        associationValue varchar(255),\n" +
                "        sagaId varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        primary key (id)\n" +
                "    )").executeUpdate();

        Oracle11Utils.simulateAutoIncrement(conn, sagaSchema().associationValueEntryTable(), "id");

        return Oracle11Utils.createNullStatement(conn);
    }

    @Override
    public PreparedStatement sql_createTableSagaEntry(final Connection conn) throws SQLException {
        return conn.prepareStatement("create table " + sagaSchema().sagaEntryTable() + " (\n" +
                "        sagaId varchar(255) not null,\n" +
                "        revision varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        serializedSaga blob,\n" +
                "        primary key (sagaId)\n" +
                "    )");
    }
}
