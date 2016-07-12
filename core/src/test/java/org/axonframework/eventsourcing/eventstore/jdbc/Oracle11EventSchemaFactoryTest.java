package org.axonframework.eventsourcing.eventstore.jdbc;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

@Ignore("These tests require that Oracle 11 is running")
public class Oracle11EventSchemaFactoryTest {

    private Oracle11EventSchemaFactory testSubject;
    private Connection connection;
    private EventSchema eventSchema;

    @Before
    public void setUp() throws Exception {
        testSubject = new Oracle11EventSchemaFactory();
        eventSchema = new EventSchema();
        connection = DriverManager.getConnection("jdbc:oracle:thin:@//localhost:1521/xe", "axon", "axon");
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void testCreateDomainEventTable() throws Exception {
        // test passes if no exception is thrown
        testSubject.createDomainEventTable(connection, eventSchema)
                .execute();
        connection.prepareStatement("SELECT * FROM " + eventSchema.domainEventTable())
                .execute();

        connection.prepareStatement("DROP TABLE " + eventSchema.domainEventTable())
                .execute();
        connection.prepareStatement("DROP SEQUENCE " + eventSchema.domainEventTable() + "_seq")
                .execute();
    }

    @Test
    public void testCreateSnapshotEventTable() throws Exception {
        // test passes if no exception is thrown
        testSubject.createSnapshotEventTable(connection, eventSchema)
                .execute();
        connection.prepareStatement("SELECT * FROM " + eventSchema.snapshotTable())
                .execute();

        connection.prepareStatement("DROP TABLE " + eventSchema.snapshotTable())
                .execute();
    }
}
