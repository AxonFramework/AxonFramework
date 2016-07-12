package org.axonframework.eventhandling.saga.repository.jdbc;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

@Ignore("These tests require that Oracle 11 is running")
public class Oracle11SagaSqlSchemaTest {

    private Oracle11SagaSqlSchema testSubject;
    private Connection connection;
    private SchemaConfiguration schemaConfiguration;

    @Before
    public void setUp() throws Exception {
        schemaConfiguration = new SchemaConfiguration();
        testSubject = new Oracle11SagaSqlSchema(schemaConfiguration);
        connection = DriverManager.getConnection("jdbc:oracle:thin:@//localhost:1521/xe", "axon", "axon");
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void testSql_createTableAssocValueEntry() throws Exception {
        // test passes if no exception is thrown
        testSubject.sql_createTableAssocValueEntry(connection)
                .execute();
        connection.prepareStatement("SELECT * FROM " + schemaConfiguration.assocValueEntryTable())
                .execute();

        connection.prepareStatement("DROP TABLE " + schemaConfiguration.assocValueEntryTable())
                .execute();
        connection.prepareStatement("DROP SEQUENCE " + schemaConfiguration.assocValueEntryTable() + "_seq")
                .execute();
    }

    @Test
    public void testSql_createTableSagaEntry() throws Exception {
        // test passes if no exception is thrown
        testSubject.sql_createTableSagaEntry(connection)
                .execute();
        connection.prepareStatement("SELECT * FROM " + schemaConfiguration.sagaEntryTable())
                .execute();

        connection.prepareStatement("DROP TABLE " + schemaConfiguration.sagaEntryTable())
                .execute();
    }
}
