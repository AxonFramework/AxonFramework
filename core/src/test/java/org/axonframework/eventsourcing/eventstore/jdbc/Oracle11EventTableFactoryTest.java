package org.axonframework.eventsourcing.eventstore.jdbc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.axonframework.common.io.IOUtils.closeQuietly;
import static org.junit.Assume.assumeNoException;

public class Oracle11EventTableFactoryTest {

    private Oracle11EventTableFactory testSubject;
    private Connection connection;
    private EventSchema eventSchema;

    @Before
    public void setUp() throws Exception {
        testSubject = new Oracle11EventTableFactory();
        eventSchema = new EventSchema();
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
