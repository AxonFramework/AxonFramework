package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FilteringEventStorageEngineTest {

    private Predicate<EventMessage<?>> filter;
    private EventStorageEngine mockStorage;
    private FilteringEventStorageEngine testSubject;

    @Before
    public void setUp() {
        filter = m -> m.getPayload().toString().contains("accept");
        mockStorage = mock(EventStorageEngine.class);
        testSubject = new FilteringEventStorageEngine(mockStorage, filter);
    }

    @Test
    public void testEventsFromArrayMatchingAreForwarded() {
        EventMessage<String> event1 = GenericEventMessage.asEventMessage("accept");
        EventMessage<String> event2 = GenericEventMessage.asEventMessage("fail");
        EventMessage<String> event3 = GenericEventMessage.asEventMessage("accept");

        testSubject.appendEvents(event1, event2, event3);

        verify(mockStorage).appendEvents(asList(event1, event3));
    }

    @Test
    public void testEventsFromListMatchingAreForwarded() {
        EventMessage<String> event1 = GenericEventMessage.asEventMessage("accept");
        EventMessage<String> event2 = GenericEventMessage.asEventMessage("fail");
        EventMessage<String> event3 = GenericEventMessage.asEventMessage("accept");

        testSubject.appendEvents(asList(event1, event2, event3));

        verify(mockStorage).appendEvents(asList(event1, event3));
    }

    @Test
    public void testStoreSnapshotDelegated() {
        GenericDomainEventMessage<Object> snapshot = new GenericDomainEventMessage<>("type", "id", 0, "fail");
        testSubject.storeSnapshot(snapshot);

        verify(mockStorage).storeSnapshot(snapshot);
    }

    @Test
    public void testCreateTailTokenDelegated() {
        testSubject.createTailToken();

        verify(mockStorage).createTailToken();
    }

    @Test
    public void testCreateHeadTokenDelegated() {
        testSubject.createHeadToken();

        verify(mockStorage).createHeadToken();
    }

    @Test
    public void testCreateTokenAtDelegated() {
        Instant now = Instant.now();
        testSubject.createTokenAt(now);

        verify(mockStorage).createTokenAt(now);
    }
}
