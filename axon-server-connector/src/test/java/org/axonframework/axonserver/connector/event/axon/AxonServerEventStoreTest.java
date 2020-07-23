/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.PlatformService;
import org.axonframework.axonserver.connector.event.EventStoreImpl;
import org.axonframework.axonserver.connector.event.StubServer;
import org.axonframework.axonserver.connector.util.TcpUtil;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AxonServerEventStoreTest {

    private StubServer server;
    private AxonServerEventStore testSubject;
    private EventUpcaster upcasterChain;
    private AxonServerConnectionManager axonServerConnectionManager;
    private EventStoreImpl eventStore;

    @BeforeEach
    void setUp() throws Exception {
        int freePort = TcpUtil.findFreePort();
        eventStore = spy(new EventStoreImpl());
        server = new StubServer(freePort, new PlatformService(freePort), eventStore);
        server.start();
        upcasterChain = mock(EventUpcaster.class);
        when(upcasterChain.upcast(any())).thenAnswer(i -> i.getArgument(0));
        AxonServerConfiguration config = AxonServerConfiguration.builder()
                                                                .forceReadFromLeader(false)
                                                                .servers("localhost:" + server.getPort())
                                                                .componentName("JUNIT")
                                                                .flowControl(2, 1, 1)
                                                                .build();
        axonServerConnectionManager = AxonServerConnectionManager.builder()
                                   .axonServerConfiguration(config)
                                   .build();
        testSubject = AxonServerEventStore.builder()
                                          .configuration(config)
                                          .platformConnectionManager(axonServerConnectionManager)
                                          .upcasterChain(upcasterChain)
                                          .eventSerializer(JacksonSerializer.defaultSerializer())
                                          .snapshotSerializer(TestSerializer.secureXStreamSerializer())
                                          .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        axonServerConnectionManager.shutdown();
        server.shutdown();
    }

    @Test
    void testPublishAndConsumeEvents() throws Exception {
        UnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        testSubject.publish(GenericEventMessage.asEventMessage("Test1"),
                            GenericEventMessage.asEventMessage("Test2"),
                            GenericEventMessage.asEventMessage("Test3"));
        uow.commit();

        TrackingEventStream stream = testSubject.openStream(null);

        List<String> received = new ArrayList<>();
        while (stream.hasNextAvailable(100, TimeUnit.MILLISECONDS)) {
            received.add(stream.nextAvailable().getPayload().toString());
        }
        stream.close();

        assertEquals(Arrays.asList("Test1", "Test2", "Test3"), received);
    }

    @Test
    void testLoadEventsWithMultiUpcaster() {
        reset(upcasterChain);
        when(upcasterChain.upcast(any())).thenAnswer(invocation -> {
            Stream<IntermediateEventRepresentation> si = invocation.getArgument(0);
            return si.flatMap(i -> Stream.of(i, i));
        });
        testSubject.publish(new GenericDomainEventMessage<>("aggregateType", "aggregateId", 0, "Test1"),
                            new GenericDomainEventMessage<>("aggregateType", "aggregateId", 1, "Test2"),
                            new GenericDomainEventMessage<>("aggregateType", "aggregateId", 2, "Test3"));

        DomainEventStream actual = testSubject.readEvents("aggregateId");
        assertTrue(actual.hasNext());
        assertEquals("Test1", actual.next().getPayload());
        assertEquals("Test1", actual.next().getPayload());
        assertEquals("Test2", actual.next().getPayload());
        assertEquals("Test2", actual.next().getPayload());
        assertEquals("Test3", actual.next().getPayload());
        assertEquals("Test3", actual.next().getPayload());
        assertFalse(actual.hasNext());
    }

    @Test
    void testLoadSnapshotAndEventsWithMultiUpcaster() throws InterruptedException {
        reset(upcasterChain);
        when(upcasterChain.upcast(any())).thenAnswer(invocation -> {
            Stream<IntermediateEventRepresentation> si = invocation.getArgument(0);
            return si.flatMap(i -> Stream.of(i, i));
        });
        testSubject.publish(new GenericDomainEventMessage<>("aggregateType", "aggregateId", 0, "Test1"),
                            new GenericDomainEventMessage<>("aggregateType", "aggregateId", 1, "Test2"),
                            new GenericDomainEventMessage<>("aggregateType", "aggregateId", 2, "Test3"));
        testSubject.storeSnapshot(new GenericDomainEventMessage<>("aggregateType", "aggregateId", 1, "Snapshot1"));

        // snapshot storage is async, so we need to make sure the first event is the snapshot
        assertWithin(2, TimeUnit.SECONDS, () -> {
            DomainEventStream events = testSubject.readEvents("aggregateId");
            assertTrue(events.hasNext());
            assertEquals("Snapshot1", events.next().getPayload());
        });

        DomainEventStream actual = testSubject.readEvents("aggregateId");
        assertTrue(actual.hasNext());
        assertEquals("Snapshot1", actual.next().getPayload());
        assertEquals("Snapshot1", actual.next().getPayload());
        assertEquals("Test3", actual.next().getPayload());
        assertEquals("Test3", actual.next().getPayload());
        assertFalse(actual.hasNext());
    }

    @Test
    void testLastSequenceNumberFor() {
        assertThrows(EventStoreException.class, () -> testSubject.lastSequenceNumberFor("Agg1"));
    }

    @Test
    void testCreateStreamableMessageSourceForContext() {
        assertNotNull(testSubject.createStreamableMessageSourceForContext("some-context"));
    }

    @Test
    void testUsingLocalEventStoreOnOpeningStream() {
        testSubject.publish(new GenericDomainEventMessage<>("aggregateType", "aggregateId", 0, "Test1"));
        testSubject.openStream(null);
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, eventStore.getEventsRequests().size()));
        assertFalse(eventStore.getEventsRequests().get(0).getForceReadFromLeader());
    }

    @Disabled("No supported in new connector, yet.")
    @Test
    void testUsingLocalEventStoreOnQueryingEvents() {
        testSubject.publish(new GenericDomainEventMessage<>("aggregateType", "aggregateId", 0, "Test1"));
        testSubject.query("", true);
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertEquals(1, eventStore.getQueryEventsRequests().size()));
        assertFalse(eventStore.getQueryEventsRequests().get(0).getForceReadFromLeader());
    }
}
