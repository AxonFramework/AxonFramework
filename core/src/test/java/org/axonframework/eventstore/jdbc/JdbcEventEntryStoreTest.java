package org.axonframework.eventstore.jdbc;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventstore.jdbc.criteria.JdbcCriteria;
import org.axonframework.eventstore.jdbc.criteria.JdbcCriteriaBuilder;
import org.axonframework.eventstore.jdbc.criteria.ParameterRegistry;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Kristian Rosenvold
 */
@SuppressWarnings("unchecked")
public class JdbcEventEntryStoreTest {

    private static final byte[] payloadBytes = "Hello World".getBytes();
    private final String aggregateIdentifier = "agg1";
    private String aggregateType = "foz";
    private String snap_aggregateType = "snap";
    private JdbcEventEntryStore es;
    private Connection connection;

    @Before
    public void createDatabase() throws SQLException {
        JDBCDataSource dataSource = new org.hsqldb.jdbc.JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");


        connection = dataSource.getConnection();
        es = new JdbcEventEntryStore(dataSource, new GenericEventSqlSchema());
		es.createSchema();
    }

    @After
    public void shutDown() throws SQLException {
		connection.createStatement().execute("SHUTDOWN");
		connection.close();
    }

    @Test
    public void fetchAggregateStreamShouldWork() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage first = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        es.persistEvent(aggregateType, first, getPayload(), getMetaData());
        DomainEventMessage second = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        es.persistEvent(aggregateType, second, getPayload(), getMetaData());

        final Iterator<? extends SerializedDomainEventData> actual = es.fetchAggregateStream(aggregateType, aggregateIdentifier, 1, 1);

        checkSame(first, actual.next());
        checkSame(second, actual.next());
        assertFalse( actual.hasNext());
    }

    private void deleteCurrentPersistentEvents() throws SQLException {
        connection.createStatement().execute("delete from DomainEventEntry");
    }

    private void deleteCurrentSnapshotEvents() throws SQLException {
        connection.createStatement().execute("delete from SnapshotEventEntry");
    }

    @Test
    public void fetchAggragateStreamWorks() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage dem = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        es.persistEvent(aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage dem2 = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        es.persistEvent(aggregateType, dem2, getPayload(), getMetaData());

        Iterator<? extends SerializedDomainEventData> stream = es.fetchAggregateStream(aggregateType, "agg1", 0, 1);
        assertTrue(stream.hasNext());
        SerializedDomainEventData next = stream.next();
        assertEquals(dem.getSequenceNumber(), next.getSequenceNumber());
        assertTrue(stream.hasNext());
        SerializedDomainEventData next1 = stream.next();
        assertEquals(dem2.getSequenceNumber(), next1.getSequenceNumber());
        assertFalse(stream.hasNext());
    }

    @Test
    public void persistSnapshots() throws SQLException {
        deleteCurrentSnapshotEvents();
        DomainEventMessage dem = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        es.persistSnapshot(snap_aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage expected = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        es.persistSnapshot(snap_aggregateType, expected, getPayload(), getMetaData());

        final SerializedDomainEventData actual = es.loadLastSnapshotEvent(snap_aggregateType, "agg1");
        checkSame(expected, actual);
    }

    @Test
    public void pruneSnapshots() throws SQLException {
        deleteCurrentSnapshotEvents();
        DomainEventMessage dem = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        es.persistSnapshot(snap_aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage expected = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        es.persistSnapshot(snap_aggregateType, expected, getPayload(), getMetaData());

        es.pruneSnapshots(snap_aggregateType, expected, 1);
        ResultSet resultSet = connection.prepareStatement("select count(*) from SnapshotEventEntry").executeQuery();
        resultSet.next();
        assertEquals(1, resultSet.getInt(1));

    }

    @Test
    public void filteredFetch() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage dem = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        es.persistEvent(aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage dem2 = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        es.persistEvent(aggregateType, dem2, getPayloadv4(), getMetaData());

        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("payloadrevision").lessThan("4");

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("e", query, parameters);

        final List<Object> parameters1 = parameters.getParameters();
        final Iterator<? extends SerializedDomainEventData> iterator = es.fetchFiltered(query.toString(), parameters1, 1);
        assertTrue( iterator.hasNext());
        final SerializedDomainEventData next = iterator.next();
        assertEquals(122, next.getSequenceNumber());
    }

    private void checkSame(DomainEventMessage expected, SerializedDomainEventData actual) {
        assertNotNull(actual);
        assertEquals(expected.getAggregateIdentifier(), actual.getAggregateIdentifier());
        assertEquals(expected.getSequenceNumber(), actual.getSequenceNumber());
        assertEquals(expected.getIdentifier(), actual.getEventIdentifier());
        assertTrue(Arrays.equals(payloadBytes, (byte[]) actual.getPayload().getData()));
        assertEquals("[B", actual.getPayload().getContentType().getName());
    }

    private static SerializedMetaData<byte[]> getMetaData() {
        return new SerializedMetaData<byte[]>("Meta is meta".getBytes(), byte[].class);
    }

    private static SimpleSerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<byte[]>(payloadBytes, byte[].class,
                new SimpleSerializedType("java.lang.String", "3"));
    }
    private static SimpleSerializedObject<byte[]> getPayloadv4() {
        return new SimpleSerializedObject<byte[]>(payloadBytes, byte[].class,
                new SimpleSerializedType("java.lang.String", "4"));
    }
}
