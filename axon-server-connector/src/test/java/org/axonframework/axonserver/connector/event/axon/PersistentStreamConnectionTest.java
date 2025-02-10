/*
 * Copyright (c) 2010-2024. Axon Framework
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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.control.ControlChannel;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamCallbacks;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.event.PersistentStreamSegment;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PersistentStreamConnection}.
 */
class PersistentStreamConnectionTest {

    private static final String STREAM_NAME = "stream-name";
    private static final String STREAM_ID = "stream-id";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final PersistentStreamProperties properties =
            new PersistentStreamProperties(STREAM_NAME, 2, "Seq", Collections.emptyList(), "0", null);
    private final Map<String, MockPersistentStream> mockPersistentStreams = new ConcurrentHashMap<>();

    private PersistentStreamConnection testSubject;

    @BeforeEach
    void setup() {
        System.setProperty("disable-axoniq-console-message", "true");
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        AxonServerConnectionManager mockAxonServerConnectionManager = mock(AxonServerConnectionManager.class);
        AxonServerConnection mockAxonServerConnection = mock(AxonServerConnection.class);
        EventChannel mockEventChannel = mock(EventChannel.class);

        when(mockEventChannel.openPersistentStream(anyString(), anyInt(), anyInt(), any(), any()))
                .thenAnswer(invocationOnMock -> {
                    String streamId = invocationOnMock.getArgument(0);
                    mockPersistentStreams.put(streamId,
                                              new MockPersistentStream(invocationOnMock.getArgument(3)));
                    return mockPersistentStreams.get(streamId);
                });
        when(mockAxonServerConnection.eventChannel()).thenReturn(mockEventChannel);
        ControlChannel mockControlChannel = mock(ControlChannel.class);
        when(mockAxonServerConnection.controlChannel()).thenReturn(mockControlChannel);
        when(mockAxonServerConnectionManager.getConnection(anyString())).thenReturn(mockAxonServerConnection);
        configurer.registerComponent(AxonServerConnectionManager.class, c -> mockAxonServerConnectionManager);
        configurer.configureEventSerializer(c -> JacksonSerializer.defaultSerializer());
        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        configurer.registerComponent(AxonServerConfiguration.class, c -> axonServerConfiguration);
        int batchSize = 100;

        testSubject = new PersistentStreamConnection(STREAM_ID,
                                                     configurer.buildConfiguration(),
                                                     properties,
                                                     scheduler,
                                                     batchSize);
    }

    @Test
    void consumesMessagesAndSendsAcknowledgements() {
        List<EventMessage<?>> eventMessages = new LinkedList<>();
        testSubject.open(eventMessages::addAll);
        MockPersistentStream mockPersistentStream = mockPersistentStreams.get(STREAM_ID);
        mockPersistentStream.publish(0, eventWithToken(0, "AggregateId-1", 0));
        mockPersistentStream.publish(0, eventWithToken(1, "AggregateId-1", 1));
        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> eventMessages.size() == 2);
        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> mockPersistentStream.lastAcknowledged(0) == 1);

        mockPersistentStream.closeSegment(0);
    }

    @Test
    void retryFailedHandler() {
        List<EventMessage<?>> eventMessages = new LinkedList<>();
        AtomicInteger failureCountDown = new AtomicInteger(2);
        AtomicInteger attempts = new AtomicInteger();
        testSubject.open(m -> {
            attempts.incrementAndGet();
            if (failureCountDown.getAndDecrement() > 0) {
                throw new IllegalStateException("Cannot invoke handler");
            }
            eventMessages.addAll(m);
        });
        MockPersistentStream mockPersistentStream = mockPersistentStreams.get(STREAM_ID);
        mockPersistentStream.publish(0, eventWithToken(0, "AggregateId-1", 0));
        mockPersistentStream.publish(0, eventWithToken(1, "AggregateId-1", 1));
        await().atMost(Duration.ofSeconds(4))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> attempts.get() == 3);
        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> eventMessages.size() == 2);
        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> mockPersistentStream.lastAcknowledged(0) == 1);

        mockPersistentStream.closeSegment(0);
    }

    @Test
    void givenAlreadyOpenedStreamWhenOpenOneMoreTimeThenException() {
        // given
        testSubject.open((e) -> {
        });

        // when - then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            testSubject.open((e) -> {
            });
        });
        assertEquals("stream-id: Persistent Stream has already been opened.", exception.getMessage());
    }

    private static EventWithToken eventWithToken(int token,
                                                 @SuppressWarnings("SameParameterValue") String aggregateId,
                                                 int seqNr) {
        return EventWithToken.newBuilder()
                             .setToken(token)
                             .setEvent(Event.newBuilder()
                                            .setAggregateSequenceNumber(seqNr)
                                            .setAggregateType(aggregateId)
                                            .setMessageIdentifier(UUID.randomUUID().toString())
                                            .setPayload(SerializedObject.newBuilder().setType("string"))
                                            .setTimestamp(System.currentTimeMillis()))
                             .build();
    }

    private static class MockPersistentStream implements PersistentStream {

        private final PersistentStreamCallbacks callbacks;
        private final Map<Integer, MockPersistentStreamSegment> segments = new ConcurrentHashMap<>();

        public MockPersistentStream(PersistentStreamCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void close() {
            callbacks.onClosed();
        }

        private void publish(@SuppressWarnings("SameParameterValue") int segmentNumber,
                             EventWithToken eventWithToken) {
            // Suppressing warning since we're dealing with a mock implementation of the segment
            @SuppressWarnings("resource")
            MockPersistentStreamSegment segment = segments.computeIfAbsent(segmentNumber, i -> {
                MockPersistentStreamSegment mockPersistentStreamSegment = new MockPersistentStreamSegment(i);
                callbacks.onSegmentOpened().accept(mockPersistentStreamSegment);
                mockPersistentStreamSegment.onAvailable(
                        () -> callbacks.onAvailable().accept(mockPersistentStreamSegment)
                );
                return mockPersistentStreamSegment;
            });
            segment.publish(eventWithToken);
        }

        public void closeSegment(int segmentNumber) {
            MockPersistentStreamSegment segment = segments.remove(segmentNumber);
            if (segment != null) {
                callbacks.onSegmentClosed().accept(segment);
            }
        }

        public long lastAcknowledged(int segmentNumber) {
            MockPersistentStreamSegment segment = segments.get(segmentNumber);
            return segment == null ? -1 : segment.lastAcknowledged.get();
        }
    }

    private static class MockPersistentStreamSegment implements PersistentStreamSegment {

        private final ConcurrentLinkedDeque<PersistentStreamEvent> entries = new ConcurrentLinkedDeque<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final int segment;
        private Runnable onAvailable = () -> {
        };
        private final AtomicLong lastAcknowledged = new AtomicLong(-1);

        private MockPersistentStreamSegment(int segment) {
            this.segment = segment;
        }

        @Override
        public PersistentStreamEvent peek() {
            return entries.peek();
        }

        @Override
        public PersistentStreamEvent nextIfAvailable() {
            return entries.isEmpty() ? null : entries.removeFirst();
        }

        @Override
        public PersistentStreamEvent nextIfAvailable(long timeout, TimeUnit unit) throws InterruptedException {
            long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            PersistentStreamEvent event = nextIfAvailable();
            while (event == null && System.currentTimeMillis() < endTime && !closed.get()) {
                Thread.sleep(1);
                event = nextIfAvailable();
            }
            return event;
        }

        @Override
        public PersistentStreamEvent next() throws InterruptedException {
            PersistentStreamEvent event = nextIfAvailable();
            while (event == null && !closed.get()) {
                Thread.sleep(1);
                event = nextIfAvailable();
            }
            return event;
        }

        @Override
        public void onAvailable(Runnable callback) {
            this.onAvailable = callback;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public Optional<Throwable> getError() {
            return Optional.empty();
        }

        @Override
        public void onSegmentClosed(Runnable callback) {
            // Not required for testing
        }

        @Override
        public void acknowledge(long token) {
            lastAcknowledged.set(token);
        }

        @Override
        public void error(String error) {
            // Not required for testing
        }

        @Override
        public int segment() {
            return segment;
        }

        private void publish(EventWithToken eventWithToken) {
            entries.add(PersistentStreamEvent.newBuilder().setEvent(eventWithToken).build());
            onAvailable.run();
        }
    }
}