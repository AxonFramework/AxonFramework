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


import org.axonframework.domain.*;
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

import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.sql.Statement;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
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
        final DefaultEventEntryStore<String> eventEntryStore1 = new DefaultEventEntryStore<>(() -> conn);
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
        setClock(Clock.systemDefaultZone());
    }

    @Test(expected = UnknownSerializedTypeException.class)
    public void testUnknownSerializedTypeCausesException() throws SQLException {
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        final PreparedStatement preparedStatement = conn.prepareStatement(
                "UPDATE DomainEventEntry e SET e.payloadType = ?");
        preparedStatement.setString(1, "unknown");
        preparedStatement.executeUpdate();
        testSubject.readEvents(aggregate1.getIdentifier());
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
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<ResultSet> resultSets = new ArrayList<>();
        doAnswer(new StatementAnswer(resultSets, statements)).when(conn).prepareStatement(anyString());

        try {
            testSubject.readEvents("noSuchId");
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
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        assertEquals((long) aggregate1.getRegisteredEventCount(), queryLong());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(singletonList(
                new GenericDomainEventMessage<>(aggregate2.getIdentifier(),
                                                0,
                                                new Object(),
                                                Collections.singletonMap("key", (Object) "Value"))));

        DomainEventStream events = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> actualEvents = new ArrayList<>();
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            event.getPayload();
            event.getMetaData();
            actualEvents.add(event);
        }
        assertEquals(aggregate1.getRegisteredEventCount(), actualEvents.size());

        /// we make sure persisted events have the same MetaData alteration logic
        DomainEventStream other = testSubject.readEvents(aggregate2.getIdentifier());
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
                .thenAnswer(invocation -> {
                    SerializedObject serializedObject = (SerializedObject) invocation.getArguments()[0];
                    return asList(serializedObject, serializedObject);
                });

        testSubject.appendEvents(aggregate1.getRegisteredEvents());

        testSubject.setUpcasterChain(mockUpcasterChain);
        assertEquals((long) aggregate1.getRegisteredEventCount(), queryLong());

        // we store some more events to make sure only correct events are retrieved
        testSubject.appendEvents(singletonList(
                new GenericDomainEventMessage<>(aggregate2.getIdentifier(), 0, new Object(),
                                                Collections.singletonMap("key", "Value"))));

        DomainEventStream events = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage<?>> actualEvents = new ArrayList<>();
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
        List<DomainEventMessage<?>> domainEvents = new ArrayList<>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<>(aggregateIdentifier, (long) t,
                                                             "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents(domainEvents);

        DomainEventStream events = testSubject.readEvents(aggregateIdentifier);
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
        List<DomainEventMessage<?>> domainEvents = new ArrayList<>(110);
        String aggregateIdentifier = "id";
        for (int t = 0; t < 110; t++) {
            domainEvents.add(new GenericDomainEventMessage<>(aggregateIdentifier, (long) t,
                                                             "Mock contents", MetaData.emptyInstance()));
        }
        testSubject.appendEvents(domainEvents);
        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(aggregateIdentifier, (long) 30,
                                                                        "Mock contents",
                                                                        MetaData.emptyInstance()
        ));

        DomainEventStream events = testSubject.readEvents(aggregateIdentifier);
        long t = 30L;
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            assertEquals(t, event.getSequenceNumber());
            t++;
        }
        assertEquals(110L, t);
    }

    @Test
    public void testLoadWithSnapshotEvent() {
        testSubject.appendEvents(aggregate1.getRegisteredEvents());
        testSubject.appendSnapshotEvent(aggregate1.createSnapshotEvent());
        aggregate1.getRegisteredEvents().clear();
        aggregate1.changeState();
        testSubject.appendEvents(aggregate1.getRegisteredEvents());

        DomainEventStream actualEventStream = testSubject.readEvents(aggregate1.getIdentifier());
        List<DomainEventMessage> domainEvents = new ArrayList<>();
        while (actualEventStream.hasNext()) {
            DomainEventMessage next = actualEventStream.next();
            domainEvents.add(next);
            assertEquals(aggregate1.getIdentifier(), next.getAggregateIdentifier());
        }

        assertEquals(2, domainEvents.size());
    }

    @Test
    public void testInsertDuplicateSnapshot() throws Exception {
        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>("id1", 1, "test"));
        try {
            testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>("id1", 1, "test"));
            fail("Expected concurrency exception");
        } catch (ConcurrencyException e) {
            assertTrue(e.getMessage().contains("snapshot"));
        }
    }


    @Test(expected = EventStreamNotFoundException.class)
    public void testLoadNonExistent() {
        testSubject.readEvents(UUID.randomUUID().toString());
    }

    @Test
    public void testVisitAllEvents() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents(createDomainEvents(77));
        testSubject.appendEvents(createDomainEvents(23));

        testSubject.visitEvents(eventVisitor);
        verify(eventVisitor, times(100)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitAllEvents_IncludesUnknownEventType() throws Exception {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        testSubject.appendEvents(createDomainEvents(10));
        final GenericDomainEventMessage eventMessage = new GenericDomainEventMessage<>("test", 0, "test");
        testSubject.appendEvents(singletonList(eventMessage));
        testSubject.appendEvents(createDomainEvents(10));
        // we upcast the event to two instances, one of which is an unknown class
        testSubject.setUpcasterChain(new LazyUpcasterChain(singletonList(new StubUpcaster())));
        testSubject.visitEvents(eventVisitor);

        verify(eventVisitor, times(21)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitEvents_AfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        setClock(ZonedDateTime.of(2011, 12, 18, 12, 59, 59, 999000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(11));
        ZonedDateTime onePM = ZonedDateTime.of(2011, 12, 18, 13, 0, 0, 0, ZoneOffset.UTC);
        setClock(onePM);
        testSubject.appendEvents(createDomainEvents(12));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(13));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(14));
        setClock(Clock.systemDefaultZone());

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThan(onePM), eventVisitor);
        verify(eventVisitor, times(13 + 14)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitEvents_BetweenTimestamps() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        setClock(ZonedDateTime.of(2011, 12, 18, 12, 59, 59, 999000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(11));
        ZonedDateTime onePM = ZonedDateTime.of(2011, 12, 18, 13, 0, 0, 0, ZoneOffset.UTC);
        setClock(onePM);
        testSubject.appendEvents(createDomainEvents(12));
        ZonedDateTime twoPM = ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC);
        setClock(twoPM);
        testSubject.appendEvents(createDomainEvents(13));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(14));
        setClock(Clock.systemDefaultZone());

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM)
                                               .and(criteriaBuilder.property("timeStamp").lessThanEquals(twoPM)),
                                eventVisitor);
        verify(eventVisitor, times(12 + 13)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test
    public void testVisitEvents_OnOrAfterTimestamp() {
        EventVisitor eventVisitor = mock(EventVisitor.class);
        setClock(ZonedDateTime.of(2011, 12, 18, 12, 59, 59, 999000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(11));
        ZonedDateTime onePM = ZonedDateTime.of(2011, 12, 18, 13, 0, 0, 0, ZoneOffset.UTC);
        setClock(onePM);
        testSubject.appendEvents(createDomainEvents(12));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 0, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(13));
        setClock(ZonedDateTime.of(2011, 12, 18, 14, 0, 0, 1000000, ZoneOffset.UTC));
        testSubject.appendEvents(createDomainEvents(14));
        setClock(Clock.systemDefaultZone());

        CriteriaBuilder criteriaBuilder = testSubject.newCriteriaBuilder();
        testSubject.visitEvents(criteriaBuilder.property("timeStamp").greaterThanEquals(onePM), eventVisitor);
        verify(eventVisitor, times(12 + 13 + 14)).doWithEvent(isA(DomainEventMessage.class));
    }

    @Test(expected = ConcurrencyException.class)
    public void testStoreDuplicateEvent_WithSqlExceptionTranslator() {
        testSubject.appendEvents(singletonList(new GenericDomainEventMessage<>("123", 0L, "Mock contents",
                                                                               MetaData.emptyInstance())));
        testSubject.appendEvents(singletonList(new GenericDomainEventMessage<>("123", 0L, "Mock contents",
                                                                               MetaData.emptyInstance())));
    }

    @DirtiesContext
    @Test
    public void testStoreDuplicateEvent_NoSqlExceptionTranslator() {
        testSubject.setPersistenceExceptionResolver(null);
        try {
            testSubject.appendEvents(singletonList(new GenericDomainEventMessage<>("123", (long) 0, "Mock contents",
                                                                                   MetaData.emptyInstance())));
            testSubject.appendEvents(singletonList(new GenericDomainEventMessage<>("123", (long) 0, "Mock contents",
                                                                                   MetaData.emptyInstance())));
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
    public void testCustomEventEntryStore() {
        EventEntryStore<String> eventEntryStore = mock(EventEntryStore.class);
        when(eventEntryStore.getDataType()).thenReturn(String.class);
        testSubject = new JdbcEventStore(eventEntryStore);
        testSubject.appendEvents(asList(
                new GenericDomainEventMessage<>(UUID.randomUUID().toString(), (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(UUID.randomUUID().toString(), (long) 0,
                                                "Mock contents", MetaData.emptyInstance())));
        verify(eventEntryStore, times(2)).persistEvent(isA(DomainEventMessage.class),
                                                       Matchers.<SerializedObject>any(),
                                                       Matchers.<SerializedObject>any());

        reset(eventEntryStore);
        GenericDomainEventMessage<String> eventMessage = new GenericDomainEventMessage<>(
                UUID.randomUUID().toString(), 0L, "Mock contents", MetaData.emptyInstance());
        when(eventEntryStore.fetchAggregateStream(any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList(singletonList(new DomainEventEntry(
                        eventMessage,
                        mockSerializedObject("Mock contents".getBytes()),
                        mockSerializedObject("Mock contents".getBytes())))).iterator());
        when(eventEntryStore.loadLastSnapshotEvent(any()))
                .thenReturn(null);

        testSubject.readEvents("1");

        verify(eventEntryStore).fetchAggregateStream("1", 0, 100);
        verify(eventEntryStore).loadLastSnapshotEvent("1");
    }

    @Test
    public void testReadPartialStream_WithoutEnd() {
        final String aggregateIdentifier = UUID.randomUUID().toString();
        testSubject.appendEvents(asList(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 4,
                                                "Mock contents", MetaData.emptyInstance())));
        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(aggregateIdentifier,
                                                                        (long) 3,
                                                                        "Mock contents",
                                                                        MetaData.emptyInstance()));

        DomainEventStream actual = testSubject.readEvents(aggregateIdentifier, 2);
        for (int i = 2; i <= 4; i++) {
            assertTrue(actual.hasNext());
            assertEquals(i, actual.next().getSequenceNumber());
        }
        assertFalse(actual.hasNext());
    }

    @Test
    public void testReadPartialStream_WithEnd() {
        final String aggregateIdentifier = UUID.randomUUID().toString();
        testSubject.appendEvents(asList(
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 3,
                                                "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>(aggregateIdentifier, (long) 4,
                                                "Mock contents", MetaData.emptyInstance())));

        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(aggregateIdentifier,
                                                                        (long) 3,
                                                                        "Mock contents",
                                                                        MetaData.emptyInstance()));

        DomainEventStream actual = testSubject.readEvents(aggregateIdentifier, 2, 3);
        for (int i = 2; i <= 3; i++) {
            assertTrue(actual.hasNext());
            assertEquals(i, actual.next().getSequenceNumber());
        }
        assertFalse(actual.hasNext());
    }

    private SerializedObject<byte[]> mockSerializedObject(byte[] bytes) {
        return new SimpleSerializedObject<>(bytes, byte[].class, "java.lang.String", "0");
    }

    private List<DomainEventMessage<?>> createDomainEvents(int numberOfEvents) {
        List<DomainEventMessage<?>> events = new ArrayList<>(numberOfEvents);
        final String aggregateIdentifier = UUID.randomUUID().toString();
        for (int t = 0; t < numberOfEvents; t++) {
            events.add(new GenericDomainEventMessage<>(aggregateIdentifier, t,
                                                       new StubStateChangedEvent(), MetaData.emptyInstance()
            ));
        }
        return events;
    }

    private static class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private static final long serialVersionUID = -3656612830058057848L;
        private transient List<DomainEventMessage<?>> registeredEvents;
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
        protected <T> void registerEventMessage(EventMessage<T> message) {
            super.registerEventMessage(message);
            getRegisteredEvents().add((DomainEventMessage<?>) message);
        }

        public List<DomainEventMessage<?>> getRegisteredEvents() {
            if (registeredEvents == null) {
                registeredEvents = new ArrayList<>();
            }
            return registeredEvents;
        }

        public int getRegisteredEventCount() {
            return registeredEvents == null ? 0 : registeredEvents.size();
        }

        @Override
        public String getIdentifier() {
            return identifier.toString();
        }

        @SuppressWarnings("UnusedDeclaration")
        @EventHandler
        public void handleStateChange(StubStateChangedEvent event) {
        }

        public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
            return new GenericDomainEventMessage<>(getIdentifier(), getVersion(),
                                                   new StubStateChangedEvent(),
                                                   MetaData.emptyInstance()
            );
        }
    }

    private static class StubStateChangedEvent {

        private StubStateChangedEvent() {
        }
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
                    new SimpleSerializedObject<>("data1", String.class, expectedTypes.get(0)),
                    new SimpleSerializedObject<>(intermediateRepresentation.getData(), byte[].class,
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
    private void setClock(ZonedDateTime zonedDateTime) {
        setClock(Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.getZone()));
    }
    private void setClock(Clock clock) {
        GenericEventMessage.clock = clock;
    }
}
