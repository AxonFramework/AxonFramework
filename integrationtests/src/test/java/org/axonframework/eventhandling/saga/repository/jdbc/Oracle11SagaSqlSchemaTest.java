package org.axonframework.eventhandling.saga.repository.jdbc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.junit.Assume.assumeNoException;

public class Oracle11SagaSqlSchemaTest {

    private Oracle11SagaSqlSchema testSubject;
    private Connection connection;
    private SagaSchema sagaSchema;

    @Before
    public void setUp() throws Exception {
        sagaSchema = new SagaSchema();
        testSubject = new Oracle11SagaSqlSchema(sagaSchema);
        try {
            connection = DriverManager.getConnection("jdbc:oracle:thin:@//localhost:1521/xe", "system", "oracle");
        } catch (SQLException e) {
            assumeNoException("Ignoring test. Machine does not have a local Oracle 11 instance running", e);
        }
    }

    @After
    public void tearDown() throws Exception {
        closeQuietly(connection);
    }

    @Test
    public void testSql_createTableAssocValueEntry() throws Exception {
        // test passes if no exception is thrown
        testSubject.sql_createTableAssocValueEntry(connection)
                .execute();
        connection.prepareStatement("SELECT * FROM " + sagaSchema.associationValueEntryTable())
                .execute();

        connection.prepareStatement("DROP TABLE " + sagaSchema.associationValueEntryTable())
                .execute();
        connection.prepareStatement("DROP SEQUENCE " + sagaSchema.associationValueEntryTable() + "_seq")
                .execute();
    }

    @Test
    public void testSql_createTableSagaEntry() throws Exception {
        // test passes if no exception is thrown
        testSubject.sql_createTableSagaEntry(connection)
                .execute();
        connection.prepareStatement("SELECT * FROM " + sagaSchema.sagaEntryTable())
                .execute();

        connection.prepareStatement("DROP TABLE " + sagaSchema.sagaEntryTable())
                .execute();
    }
}
