/*
 * Copyright (c) 2010-2013. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.jdbc;


import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.axonframework.serializer.UnknownSerializedTypeException;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.hsqldb.jdbc.JDBCDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


/**
 * @author Allard Buijze
 * @author Kristian Rosenvold
 */
public class JdbcEventStoreTest {

    private JdbcEventStore testSubject;
    private StubAggregateRoot aggregate1;
    private StubAggregateRoot aggregate2;
    private Connection conn;

    @Before
    public void setUp() throws SQLException {
        JDBCDataSource dataSource = new org.hsqldb.jdbc.JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:test");
        this.conn = spy(dataSource.getConnection());
        doNothing().when(conn).close();
        final GenericEventSqlSchema sqldef = new GenericEventSqlSchema();
        final DefaultEventEntryStore eventEntryStore1 = new DefaultEventEntryStore(new ConnectionProvider() {
            @Override
            public Connection getConnection() throws SQLException {
                return conn;
            }
        }, sqldef);
        eventEntryStore1.createSchema();
        testSubject = new JdbcEventStore(eventEntryStore1);

        aggregate1 = new StubAggregateRoot(UUID.randomUUID());
        for (int t = 0; t < 10; t++) {
            aggregate1.changeState();
        }

        aggregate2 = new StubAggregateRoot();
        aggregate2.changeState();
        aggregate2.changeState();
        aggregate2.changeState();
        conn.prepareStatement("DELETE FROM DomainEventEntry").executeUpdate();
        conn.prepareStatement("DELETE FROM SnapshotEventEntry").executeUpdate();
    }

    @After
    public void tearDown() throws SQLException {
        reset(conn);
        conn.createStatement().execute("SHUTDOWN");
        conn.close();
        // just to make sure
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStoreAndLoadEvents_BadIdentifierType() {
        testSubject.appendEvents("type", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(new BadIdentifierType(), 1, new Object())));
    }

    @Test(expected = UnknownSerializedTypeException.class)
    public void testUnknownSerializedTypeCausesException() throws SQLException {
        testSubject.appendEvents("type", aggregate1.getUncommittedEvents());
        final PreparedStatement preparedStatement = conn.prepareStatement("UPDATE DomainEventEntry e SET e.payloadType = ?");
        preparedStatement.setString(1, "unknown");
        preparedStatement.executeUpdate();
        testSubject.readEvents("type", aggregate1.getIdentifier());
    }

    private long queryLong() throws SQLException {
        final PreparedStatement preparedStatement = conn.prepareStatement("SELECT count(*) FROM DomainEventEntry e");
        final ResultSet resultSet = preparedStatement.executeQuery();
        resultSet.next();
        return resultSet.getLong(1);
    }

    /* see AXON-321: http://issues.axonframework.org/youtrack/issue/AXON-321 */
    @Test
    public void testResourcesProperlyClosedWhenAggregateStreamIsNotFound() throws SQLException {
        reset(conn);
        doNothing().when(conn).close();
        final List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        final List<ResultSet> resultSets = new ArrayList<ResultSet>();
        doAnswer(new StatementAnswer(resultSets, statements)).when(conn).prepareStatement(anyString());

        try {
            testSubject.readEvents("notExist", "noSuchId");
            fail("Expected EventStreamNotFoundException");
        } catch (EventStreamNotFoundException e) {
            verify(conn, atLeastOnce()).prepareStatement(anyString());
            for (Statement statement : statements) {
                verify(statement).close();
            }
            for (ResultSet resultSet : resultSets) {
                verify(resultSet).close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStoreAndLoadEvents() throws SQLException {
        assertNotNull(testSubject);
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        assertEquals((long) aggregate1.getUncommittedEventCount(),queryLong());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(aggregate2.getIdentifier(),
                        0,
                        new Object(),
                        Collections.singletonMap("key", (Object) "Value"))));

        DomainEventStream events = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<DomainEventMessage>();
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            event.getPayload();
            event.getMetaData();
            actualEvents.add(event);
        }
        assertEquals(aggregate1.getUncommittedEventCount(), actualEvents.size());

        /// we make sure persisted events have the same MetaData alteration logic
        DomainEventStream other = testSubject.readEvents("test", aggregate2.getIdentifier());
        assertTrue(other.hasNext());
        DomainEventMessage messageWithMetaData = other.next();
        DomainEventMessage altered = messageWithMetaData.withMetaData(Collections.singletonMap("key2",
                (Object) "value"));
        DomainEventMessage combined = messageWithMetaData.andMetaData(Collections.singletonMap("key2",
                (Object) "value"));
        assertTrue(altered.getMetaData().containsKey("key2"));
        altered.getPayload();
        assertFalse(altered.getMetaData().containsKey("key"));
        assertTrue(altered.getMetaData().containsKey("key2"));
        assertTrue(combined.getMetaData().containsKey("key"));
        assertTrue(combined.getMetaData().containsKey("key2"));
        assertNotNull(messageWithMetaData.getPayload());
        assertNotNull(messageWithMetaData.getMetaData());
        assertFalse(messageWithMetaData.getMetaData().isEmpty());
    }

    @Test
    public void testStoreAndLoadEvents_WithUpcaster() throws SQLException {
        assertNotNull(testSubject);
        UpcasterChain mockUpcasterChain = mock(UpcasterChain.class);
        when(mockUpcasterChain.upcast(isA(SerializedObject.class), isA(UpcastingContext.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                        return Arrays.asList(serializedObject, serializedObject);
                    }
                });

        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());

        testSubject.setUpcasterChain(mockUpcasterChain);
        assertEquals((long) aggregate1.getUncommittedEventCount(),queryLong());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<Object>(aggregate2.getIdentifier(),
                        0,
                        new Object(),
                        Collections.singletonMap("key", (Object) "Value"))));

        DomainEventStream events = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<DomainEventMessage>();
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            event.getPayload();
            event.getMetaData();
            actualEvents.add(event);
        }

        assertEquals(20, actualEvents.size());
        for (int t = 0; t < 20; t = t + 2) {
            assertEquals(actualEvents.get(t).getSequenceNumber(), actualEvents.get(t + 1).getSequenceNumber());
            assertEquals(actualEvents.get(t).getAggregateIdentifier(),
                    actualEvents.get(t + 1).getAggregateIdentifier());
            assertEquals(actualEvents.get(t).getMetaData(), actualEvents.get(t + 1).getMetaData());
            assertNotNull(actualEvents.get(t).getPayload());
            assertNotNull(actualEvents.get(t + 1).getPayload());
        }
    }

    @Test
    public void testLoad_LargeAmountOfEvents() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                    "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));

        DomainEventStream events = testSubject.readEvents("test", aggregateIdentifier);
        long t = 0L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            assertEquals(t, event.getSequenceNumber());
            t++;
        }
        assertEquals(110L, t);
    }

    @Test
    public void testLoad_LargeAmountOfEventsInSmallBatches() {
        testSubject.setBatchSize(10);
        testLoad_LargeAmountOfEvents();
    }


    @Test
    public void testLoad_LargeAmountOfEventsWithSnapshot() {
        List<DomainEventMessage<String>> domainEvents = new ArrayList<DomainEventMessage<String>>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<String>(aggregateIdentifier, (long) t,
                    "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents("test", new SimpleDomainEventStream(domainEvents));
        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 30,
                "Mock contents",
                MetaData.emptyInstance()
        ));

        DomainEventStream events = testSubject.readEvents("test", aggregateIdentifier);
        long t = 30L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            assertEquals(t, event.getSequenceNumber());
            t++;
        }
        assertEquals(110L, t);
    }

    @Test
    @Transactional
    public void testLoadWithSnapshotEvent() {
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();
        testSubject.appendSnapshotEvent("test", aggregate1.createSnapshotEvent());
        aggregate1.changeState();
        testSubject.appendEvents("test", aggregate1.getUncommittedEvents());
        aggregate1.commitEvents();

        DomainEventStream actualEventStream = testSubject.readEvents("test", aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<DomainEventMessage>();
        while (actualEventStream.hasNext()) {
            DomainEventMessage next = actualEventStream.next();
            domainEvents.add(next);
            assertEquals(aggregate1.getIdentifier(), next.getAggregateIdentifier());
        }

        assertEquals(2, domainEvents.size());
    }

    @Transactional
    @Test
    public void testInsertDuplicateSnapshot() throws Exception {
        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>("id1", 1, "test"));
        try {
            testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>("id1", 1, "test"));
            fail("Expected concurrency exception");
        } catch (ConcurrencyException e) {
            assertTrue(e.getMessage().contains("snapshot"));
        }
    }


    @Test(expected = EventStreamNotFoundException.class)
    @Transactional
    public void testLoadNonExistent() {
        testSubject.readEvents("Stub", UUID.randomUUID());
    }

    @Test
    @Transactional
    public void testVisitAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(77)));
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(23)));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    @Transactional
    public void testVisitAllEvents_IncludesUnknownEventType() throws Exception {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(10)));
        final GenericDomainEventMessage eventMessage = new GenericDomainEventMessage<String>("test", 0, "test");
        testSubject.appendEvents("test", new SimpleDomainEventStream(eventMessage));
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(10)));
        // we upcast the event to two instances, one of which is an unknown class
        testSubject.setUpcasterChain(new LazyUpcasterChain(Arrays.<Upcaster>asList(new StubUpcaster())));
        testSubject.visitEvents(eventVisitor);

        verify(eventVisitor, times(21)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    @Transactional
    public void testVisitEvents_AfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThan(onePM), eventVisitor);
        verify(eventVisitor, times(13 + 14)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    @Transactional
    public void testVisitEvents_BetweenTimestamps() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
        DateTime twoPM = new DateTime(2011, 12, 18, 14, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(twoPM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM)
                .and(criteriaBuilder.property("timeStamp").lessThanEquals(twoPM)),
                eventVisitor);
        verify(eventVisitor, times(12 + 13)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    @Transactional
    public void testVisitEvents_OnOrAfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 12, 59, 59, 999).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(11)));
        DateTime onePM = new DateTime(2011, 12, 18, 13, 0, 0, 0);
        DateTimeUtils.setCurrentMillisFixed(onePM.getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(12)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 0).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(13)));
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2011, 12, 18, 14, 0, 0, 1).getMillis());
        testSubject.appendEvents("test", new SimpleDomainEventStream(createDomainEvents(14)));
        DateTimeUtils.setCurrentMillisSystem();

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM), eventVisitor);
        verify(eventVisitor, times(12 + 13 + 14)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test(expected = ConcurrencyException.class)
    @Transactional
    public void testStoreDuplicateEvent_WithSqlExceptionTranslator() {
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>("123", 0L,
                        "Mock contents", MetaData.emptyInstance())));
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>("123", 0L,
                        "Mock contents", MetaData.emptyInstance())));
    }

    @DirtiesContext
    @Test
    @Transactional
    public void testStoreDuplicateEvent_NoSqlExceptionTranslator() {
        testSubject.setPersistenceExceptionResolver(null);
        try {
            testSubject.appendEvents("test", new SimpleDomainEventStream(
                    new GenericDomainEventMessage<String>("123", (long) 0,
                            "Mock contents", MetaData.emptyInstance())));
            testSubject.appendEvents("test", new SimpleDomainEventStream(
                    new GenericDomainEventMessage<String>("123", (long) 0,
                            "Mock contents", MetaData.emptyInstance())));
        } catch (ConcurrencyException ex) {
            fail("Didn't expect exception to be translated");
        } catch (Exception ex) {
            assertTrue("Got the right exception, "
                               + "but the message doesn't seem to mention 'persist an event': " + ex.getMessage(),
                       ex.getMessage().toLowerCase().contains("persist an event")
            );
        }
    }


    @SuppressWarnings({"PrimitiveArrayArgumentToVariableArgMethod", "unchecked"})
    @DirtiesContext
    @Test
    @Transactional
    public void testCustomEventEntryStore() {
        EventEntryStore<String> eventEntryStore = mock(EventEntryStore.class);
        when(eventEntryStore.getDataType()).thenReturn(String.class);
        testSubject = new JdbcEventStore(eventEntryStore);
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(UUID.randomUUID(), (long) 0,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(UUID.randomUUID(), (long) 0,
                        "Mock contents", MetaData.emptyInstance())));
        verify(eventEntryStore, times(2)).persistEvent(eq("test"), isA(DomainEventMessage.class),
                Matchers.<SerializedObject>any(),
                Matchers.<SerializedObject>any());

        reset(eventEntryStore);
        GenericDomainEventMessage<String> eventMessage = new GenericDomainEventMessage<String>(
                UUID.randomUUID(), 0L, "Mock contents", MetaData.emptyInstance());
        when(eventEntryStore.fetchAggregateStream(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList(Arrays.asList(new DomainEventEntry(
                        "Mock", eventMessage,
                        mockSerializedObject("Mock contents".getBytes()),
                        mockSerializedObject("Mock contents".getBytes())))).iterator());
        when(eventEntryStore.loadLastSnapshotEvent(anyString(), any()))
                .thenReturn(null);

        testSubject.readEvents("test", "1");

        verify(eventEntryStore).fetchAggregateStream("test", "1", 0, 100);
        verify(eventEntryStore).loadLastSnapshotEvent("test", "1");
    }

    @Test
    @Transactional
    public void testReadPartialStream_WithoutEnd() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 4,
                        "Mock contents", MetaData.emptyInstance())));
        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>(aggregateIdentifier,
                (long) 3,
                "Mock contents",
                MetaData.emptyInstance()));

        DomainEventStream actual = testSubject.readEvents("test", aggregateIdentifier, 2);
        for (int i = 2; i <= 4; i++) {
            assertTrue(actual.hasNext());
            assertEquals(i, actual.next().getSequenceNumber());
        }
        assertFalse(actual.hasNext());
    }

    @Test
    @Transactional
    public void testReadPartialStream_WithEnd() {
        final UUID aggregateIdentifier = UUID.randomUUID();
        testSubject.appendEvents("test", new SimpleDomainEventStream(
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 0,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 1,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 2,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 3,
                        "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<String>(aggregateIdentifier, (long) 4,
                        "Mock contents", MetaData.emptyInstance())));

        testSubject.appendSnapshotEvent("test", new GenericDomainEventMessage<String>(aggregateIdentifier,
                (long) 3,
                "Mock contents",
                MetaData.emptyInstance()));

        DomainEventStream actual = testSubject.readEvents("test", aggregateIdentifier, 2, 3);
        for (int i = 2; i <= 3; i++) {
            assertTrue(actual.hasNext());
            assertEquals(i, actual.next().getSequenceNumber());
        }
        assertFalse(actual.hasNext());
    }

    private SerializedObject<byte[]> mockSerializedObject(byte[] bytes) {
        return new SimpleSerializedObject<byte[]>(bytes, byte[].class, "java.lang.String", "0");
    }

    private List<DomainEventMessage<StubStateChangedEvent>> createDomainEvents(int numberOfEvents) {
        List<DomainEventMessage<StubStateChangedEvent>> events = new ArrayList<DomainEventMessage<StubStateChangedEvent>>();
        final Object aggregateIdentifier = UUID.randomUUID();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new GenericDomainEventMessage<StubStateChangedEvent>(
                    aggregateIdentifier,
                    t,
                    new StubStateChangedEvent(), MetaData.emptyInstance()
            ));
        }
        return events;
    }

    private static class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private static final long serialVersionUID = -3656612830058057848L;
        private final Object identifier;

        private StubAggregateRoot() {
            this(UUID.randomUUID());
        }

        private StubAggregateRoot(Object identifier) {
            this.identifier = identifier;
        }

        public void changeState() {
            apply(new StubStateChangedEvent());
        }

        @Override
        public Object getIdentifier() {
            return identifier;
        }

        @SuppressWarnings("UnusedDeclaration")
        @EventHandler
        public void handleStateChange(StubStateChangedEvent event) {
        }

        public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
            return new GenericDomainEventMessage<StubStateChangedEvent>(getIdentifier(), getVersion(),
                    new StubStateChangedEvent(),
                    MetaData.emptyInstance()
            );
        }
    }

    private static class StubStateChangedEvent {

        private StubStateChangedEvent() {
        }
    }

    private static class BadIdentifierType {

    }

    private static class StubUpcaster implements Upcaster<byte[]> {

        @Override
        public boolean canUpcast(SerializedType serializedType) {
            return "java.lang.String".equals(serializedType.getName());
        }

        @Override
        public Class<byte[]> expectedRepresentationType() {
            return byte[].class;
        }

        @Override
        public List<SerializedObject<?>> upcast(SerializedObject<byte[]> intermediateRepresentation,
                                                List<SerializedType> expectedTypes, UpcastingContext context) {
            return Arrays.<SerializedObject<?>>asList(
                    new SimpleSerializedObject<String>("data1", String.class, expectedTypes.get(0)),
                    new SimpleSerializedObject<byte[]>(intermediateRepresentation.getData(), byte[].class,
                            expectedTypes.get(1)));
        }

        @Override
        public List<SerializedType> upcast(SerializedType serializedType) {
            return Arrays.<SerializedType>asList(new SimpleSerializedType("unknownType1", "2"),
                    new SimpleSerializedType(StubStateChangedEvent.class.getName(), "2"));
        }
    }

    private static class StatementAnswer implements Answer<Statement> {

        private final List<ResultSet> resultSets;
        private final List<PreparedStatement> statements;

        public StatementAnswer(List<ResultSet> resultSets, List<PreparedStatement> statements) {
            this.resultSets = resultSets;
            this.statements = statements;
        }

        @Override
        public Statement answer(InvocationOnMock invocation) throws Throwable {
            final PreparedStatement spy = (PreparedStatement) spy(invocation.callRealMethod());
            doAnswer(new ResultSetAnswer()).when(spy).executeQuery(anyString());
            doAnswer(new ResultSetAnswer()).when(spy).executeQuery();
            statements.add(spy);
            return spy;
        }

        private class ResultSetAnswer implements Answer<ResultSet> {

            @Override
            public ResultSet answer(InvocationOnMock invocation) throws Throwable {
                final ResultSet resultSet = (ResultSet) spy(invocation.callRealMethod());
                resultSets.add(resultSet);
                return resultSet;
            }
        }
    }
}
