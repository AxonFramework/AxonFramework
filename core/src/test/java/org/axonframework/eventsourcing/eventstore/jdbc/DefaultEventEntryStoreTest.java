package org.axonframework.eventsourcing.eventstore.jdbc;

/**
 * @author Kristian Rosenvold
 */
@SuppressWarnings("unchecked")
public class DefaultEventEntryStoreTest {

    //todo check what's here and add to JDBC storage test if applicable

//    private static final byte[] payloadBytes = "Hello World".getBytes();
//    private final String aggregateIdentifier = "agg1";
//    private String snap_aggregateType = "snap";
//    private DefaultEventEntryStore testSubject;
//    private Connection connection;
//	private JDBCDataSource dataSource;
//
//    private static SerializedMetaData<byte[]> getMetaData() {
//        return new SerializedMetaData<>("Meta is meta".getBytes(), byte[].class);
//    }
//
//    private static SimpleSerializedObject<byte[]> getPayload() {
//        return new SimpleSerializedObject<>(payloadBytes, byte[].class,
//                                            new SimpleSerializedType("java.lang.String", "3"));
//    }
//
//    private static SimpleSerializedObject<byte[]> getPayloadv4() {
//        return new SimpleSerializedObject<>(payloadBytes, byte[].class,
//                                            new SimpleSerializedType("java.lang.String", "4"));
//    }
//
//    @Before
//    public void createDatabase() throws SQLException {
//		dataSource = new JDBCDataSource();
//        dataSource.setUrl("jdbc:hsqldb:mem:test");
//
//        connection = dataSource.getConnection();
//        testSubject = new DefaultEventEntryStore(dataSource, new GenericEventSqlSchema());
//		testSubject.createSchema();
//    }
//
//    @After
//    public void shutDown() throws SQLException {
//		connection.createStatement().execute("SHUTDOWN");
//		connection.close();
//    }
//
//    @Test
//    public void fetchAggregateStreamShouldWork() throws SQLException {
//        deleteCurrentPersistentEvents();
//        DomainEventMessage first = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistEvent(first, getPayload(), getMetaData());
//        DomainEventMessage second = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistEvent(second, getPayload(), getMetaData());
//
//        final Iterator<? extends SerializedDomainEventData> actual = testSubject.fetchAggregateStream(
//                aggregateIdentifier,
//                1,
//                1);
//
//        checkSame(first, actual.next());
//        checkSame(second, actual.next());
//        assertFalse( actual.hasNext());
//    }
//
//    private void deleteCurrentPersistentEvents() throws SQLException {
//        connection.createStatement().execute("delete from DomainEventEntry");
//    }
//
//    private void deleteCurrentSnapshotEvents() throws SQLException {
//        connection.createStatement().execute("delete from SnapshotEventEntry");
//    }
//
//    @Test
//    public void fetchAggregateStreamWorks() throws SQLException {
//        deleteCurrentPersistentEvents();
//        DomainEventMessage dem = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistEvent(dem, getPayload(), getMetaData());
//        DomainEventMessage dem2 = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistEvent(dem2, getPayload(), getMetaData());
//
//        Iterator<? extends SerializedDomainEventData> stream = testSubject.fetchAggregateStream("agg1", 0, 1);
//        assertTrue(stream.hasNext());
//        SerializedDomainEventData next = stream.next();
//        assertEquals(dem.getSequenceNumber(), next.getSequenceNumber());
//        assertTrue(stream.hasNext());
//        SerializedDomainEventData next1 = stream.next();
//        assertEquals(dem2.getSequenceNumber(), next1.getSequenceNumber());
//        assertFalse(stream.hasNext());
//    }
//
//    @Test
//    public void persistSnapshots() throws SQLException {
//        deleteCurrentSnapshotEvents();
//        DomainEventMessage dem = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistSnapshot(dem, getPayload(), getMetaData());
//        DomainEventMessage expected = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistSnapshot(expected, getPayload(), getMetaData());
//
//        final SerializedDomainEventData actual = testSubject.loadLastSnapshotEvent("agg1");
//        checkSame(expected, actual);
//    }
//
//    @Test
//    public void pruneSnapshots() throws SQLException {
//        deleteCurrentSnapshotEvents();
//        DomainEventMessage dem = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistSnapshot(dem, getPayload(), getMetaData());
//        DomainEventMessage expected = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistSnapshot(expected, getPayload(), getMetaData());
//
//        testSubject.pruneSnapshots(expected, 1);
//        ResultSet resultSet = connection.prepareStatement("select count(*) from SnapshotEventEntry").executeQuery();
//        resultSet.next();
//        assertEquals(1, resultSet.getInt(1));
//
//    }
//
//    @Test
//    public void filteredFetchPayloadRevision() throws SQLException {
//        deleteCurrentPersistentEvents();
//        DomainEventMessage dem1 = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistEvent(dem1, getPayload(), getMetaData());
//        DomainEventMessage dem2 = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistEvent(dem2, getPayloadv4(), getMetaData());
//
//        JdbcCriteriaBuilder builder = new JdbcCriteriaBuilder();
//        JdbcCriteria criteria = (JdbcCriteria) builder.property("payloadrevision").lessThan("4");
//
//        StringBuilder query = new StringBuilder();
//        ParameterRegistry parameters = new ParameterRegistry();
//        criteria.parse("", query, parameters);
//
//        final List<Object> parameters1 = parameters.getParameters();
//        final Iterator<? extends SerializedDomainEventData> iterator = testSubject.fetchFiltered(query.toString(), parameters1, 1);
//
//        assertTrue( iterator.hasNext());
//        final SerializedDomainEventData ret1 = iterator.next();
//        assertEquals(122, ret1.getSequenceNumber());
//
//        assertFalse(iterator.hasNext());
//    }
//
//    @Test
//    public void filteredFetch() throws SQLException {
//        deleteCurrentPersistentEvents();
//        DomainEventMessage dem1 = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistEvent(dem1, getPayload(), getMetaData());
//        DomainEventMessage dem2 = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistEvent(dem2, getPayload(), getMetaData());
//
//        final Iterator<? extends SerializedDomainEventData> iterator = testSubject.fetchFiltered(
//                null, Collections.emptyList(), 1);
//
//        assertTrue(iterator.hasNext());
//        final SerializedDomainEventData ret1 = iterator.next();
//        assertEquals(122, ret1.getSequenceNumber());
//
//        assertTrue(iterator.hasNext());
//        final SerializedDomainEventData ret2 = iterator.next();
//        assertEquals(123, ret2.getSequenceNumber());
//
//        assertFalse(iterator.hasNext());
//    }
//
//    @Test
//    public void filteredFetchLargerBatchSize() throws SQLException {
//        deleteCurrentPersistentEvents();
//        DomainEventMessage dem1 = new GenericDomainEventMessage(type, aggregateIdentifier, 122, "apayload");
//        testSubject.persistEvent(dem1, getPayload(), getMetaData());
//        DomainEventMessage dem2 = new GenericDomainEventMessage(type, aggregateIdentifier, 123, "apayload2");
//        testSubject.persistEvent(dem2, getPayload(), getMetaData());
//
//        final Iterator<? extends SerializedDomainEventData> iterator = testSubject.fetchFiltered(
//                null, Collections.emptyList(), 100);
//
//        assertTrue(iterator.hasNext());
//        final SerializedDomainEventData ret1 = iterator.next();
//        assertEquals(122, ret1.getSequenceNumber());
//
//        assertTrue(iterator.hasNext());
//        final SerializedDomainEventData ret2 = iterator.next();
//        assertEquals(123, ret2.getSequenceNumber());
//
//        assertFalse(iterator.hasNext());
//    }
//
//    private void checkSame(DomainEventMessage expected, SerializedDomainEventData actual) {
//        assertNotNull(actual);
//        assertEquals(expected.getAggregateIdentifier(), actual.getAggregateIdentifier());
//        assertEquals(expected.getSequenceNumber(), actual.getSequenceNumber());
//        assertEquals(expected.getIdentifier(), actual.getEventIdentifier());
//        assertTrue(Arrays.equals(payloadBytes, (byte[]) actual.getPayload().getData()));
//        assertEquals("[B", actual.getPayload().getContentType().getName());
//    }
}
