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
import org.junit.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Kristian Rosenvold
 */
@SuppressWarnings("unchecked")
public class DefaultEventEntryStoreTest {

    private static final byte[] payloadBytes = "Hello World".getBytes();
    private final String aggregateIdentifier = "agg1";
    private String aggregateType = "foz";
    private String snap_aggregateType = "snap";
    private DefaultEventEntryStore testSubject;
    private Connection connection;
	private JDBCDataSource dataSource;

    @Before
    public void createDatabase() throws SQLException {
		dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");

        connection = dataSource.getConnection();
        testSubject = new DefaultEventEntryStore(dataSource, new GenericEventSqlSchema());
		testSubject.createSchema();
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
        testSubject.persistEvent(aggregateType, first, getPayload(), getMetaData());
        DomainEventMessage second = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistEvent(aggregateType, second, getPayload(), getMetaData());

        final Iterator<? extends SerializedDomainEventData> actual = testSubject.fetchAggregateStream(aggregateType, aggregateIdentifier, 1, 1);

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
    public void fetchAggregateStreamWorks() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage dem = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        testSubject.persistEvent(aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage dem2 = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistEvent(aggregateType, dem2, getPayload(), getMetaData());

        Iterator<? extends SerializedDomainEventData> stream = testSubject.fetchAggregateStream(aggregateType, "agg1", 0, 1);
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
        testSubject.persistSnapshot(snap_aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage expected = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistSnapshot(snap_aggregateType, expected, getPayload(), getMetaData());

        final SerializedDomainEventData actual = testSubject.loadLastSnapshotEvent(snap_aggregateType, "agg1");
        checkSame(expected, actual);
    }

    @Test
    public void pruneSnapshots() throws SQLException {
        deleteCurrentSnapshotEvents();
        DomainEventMessage dem = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        testSubject.persistSnapshot(snap_aggregateType, dem, getPayload(), getMetaData());
        DomainEventMessage expected = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistSnapshot(snap_aggregateType, expected, getPayload(), getMetaData());

        testSubject.pruneSnapshots(snap_aggregateType, expected, 1);
        ResultSet resultSet = connection.prepareStatement("select count(*) from SnapshotEventEntry").executeQuery();
        resultSet.next();
        assertEquals(1, resultSet.getInt(1));

    }

    @Test
    public void filteredFetchPayloadRevision() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage dem1 = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        testSubject.persistEvent(aggregateType, dem1, getPayload(), getMetaData());
        DomainEventMessage dem2 = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistEvent(aggregateType, dem2, getPayloadv4(), getMetaData());

        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
        JdbcCriteria criteria = (JdbcCriteria) builder.property("payloadrevision").lessThan("4");

        StringBuilder query = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        criteria.parse("", query, parameters);

        final List<Object> parameters1 = parameters.getParameters();
        final Iterator<? extends SerializedDomainEventData> iterator = testSubject.fetchFiltered(query.toString(), parameters1, 1);

        assertTrue( iterator.hasNext());
        final SerializedDomainEventData ret1 = iterator.next();
        assertEquals(122, ret1.getSequenceNumber());

        assertFalse( iterator.hasNext());
    }

    @Test
    public void filteredFetch() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage dem1 = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        testSubject.persistEvent(aggregateType, dem1, getPayload(), getMetaData());
        DomainEventMessage dem2 = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistEvent(aggregateType, dem2, getPayload(), getMetaData());

        final Iterator<? extends SerializedDomainEventData> iterator = testSubject.fetchFiltered(
                null, Collections.emptyList(), 1);

        assertTrue(iterator.hasNext());
        final SerializedDomainEventData ret1 = iterator.next();
        assertEquals(122, ret1.getSequenceNumber());

        assertTrue(iterator.hasNext());
        final SerializedDomainEventData ret2 = iterator.next();
        assertEquals(123, ret2.getSequenceNumber());

        assertFalse(iterator.hasNext());
    }

    @Test
    public void filteredFetchLargerBatchSize() throws SQLException {
        deleteCurrentPersistentEvents();
        DomainEventMessage dem1 = new GenericDomainEventMessage(aggregateIdentifier, 122, "apayload");
        testSubject.persistEvent(aggregateType, dem1, getPayload(), getMetaData());
        DomainEventMessage dem2 = new GenericDomainEventMessage(aggregateIdentifier, 123, "apayload2");
        testSubject.persistEvent(aggregateType, dem2, getPayload(), getMetaData());

        final Iterator<? extends SerializedDomainEventData> iterator = testSubject.fetchFiltered(
                null, Collections.emptyList(), 100);

        assertTrue(iterator.hasNext());
        final SerializedDomainEventData ret1 = iterator.next();
        assertEquals(122, ret1.getSequenceNumber());

        assertTrue(iterator.hasNext());
        final SerializedDomainEventData ret2 = iterator.next();
        assertEquals(123, ret2.getSequenceNumber());

        assertFalse(iterator.hasNext());
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
        return new SerializedMetaData<>("Meta is meta".getBytes(), byte[].class);
    }

    private static SimpleSerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<>(payloadBytes, byte[].class,
                new SimpleSerializedType("java.lang.String", "3"));
    }
    private static SimpleSerializedObject<byte[]> getPayloadv4() {
        return new SimpleSerializedObject<>(payloadBytes, byte[].class,
                new SimpleSerializedType("java.lang.String", "4"));
    }
}
