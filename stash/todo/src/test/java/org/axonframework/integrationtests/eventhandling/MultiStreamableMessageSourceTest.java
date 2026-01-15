/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.integrationtests.eventhandling;

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.eventhandling.GenericTrackedEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.MultiStreamableMessageSource;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.MultiSourceTrackingToken;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.StreamableMessageSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiStreamableMessageSource}.
 *
 * @author Greg Woods
 */
@Tags({
        @Tag("flaky"),
})
@Disabled("TODO #3435")
class MultiStreamableMessageSourceTest {

    private MultiStreamableMessageSource testSubject;

    private EventStore eventStoreA;
    private EventStore eventStoreB;

    @BeforeEach
    void setUp() {
        eventStoreA = new StorageEngineBackedEventStore(new InMemoryEventStorageEngine(), new SimpleEventBus(), new AnnotationBasedTagResolver());
        eventStoreB = new StorageEngineBackedEventStore(new InMemoryEventStorageEngine(), new SimpleEventBus(), new AnnotationBasedTagResolver());

        testSubject = MultiStreamableMessageSource.builder()
//                                                  .addMessageSource("eventStoreA", eventStoreA)
//                                                  .addMessageSource("eventStoreB", eventStoreB)
                                                  .longPollingSource("eventStoreA")
                                                  .build();
    }

    @Test
    void simplePublishAndConsume() throws InterruptedException {
        EventMessage publishedEvent = EventTestUtils.asEventMessage("Event1");

//        eventStoreA.publish(publishedEvent);

        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTailToken());

        assertTrue(singleEventStream.hasNextAvailable());
        assertEquals(publishedEvent.payload(), singleEventStream.nextAvailable().payload());

        singleEventStream.close();
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Test
    void connectionsAreClosedWhenOpeningFails() {
        StreamableMessageSource<TrackedEventMessage> source1 = mock(StreamableMessageSource.class);
        StreamableMessageSource<TrackedEventMessage> source2 = mock(StreamableMessageSource.class);
        testSubject = MultiStreamableMessageSource.builder()
                                                  .addMessageSource("source1", source1)
                                                  .addMessageSource("source2", source2)
                                                  .build();
        BlockingStream<TrackedEventMessage> mockStream = mock(BlockingStream.class);
        when(source1.openStream(any())).thenReturn(mockStream);
        when(source2.openStream(any())).thenThrow(new RuntimeException());

        assertThrows(RuntimeException.class, () -> testSubject.openStream(null));

        verify(mockStream).close();
        verify(source1).openStream(null);
        verify(source2).openStream(null);
    }

    @Test
    void simplePublishAndConsumeDomainEventMessage() throws InterruptedException {
        EventMessage publishedEvent = new GenericDomainEventMessage(
                "Aggregate", "id", 0, new MessageType("event"), "Event1"
        );

//        eventStoreA.publish(publishedEvent);
        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTailToken());

        assertTrue(singleEventStream.hasNextAvailable());
        TrackedEventMessage actual = singleEventStream.nextAvailable();

        assertEquals(publishedEvent.payload(), actual.payload());
        assertTrue(actual instanceof DomainEventMessage);

        singleEventStream.close();
    }

    @SuppressWarnings("resource")
    @Test
    void peekingLastMessageKeepsItAvailable() throws InterruptedException {
        EventMessage publishedEvent1 = EventTestUtils.asEventMessage("Event1");

//        eventStoreA.publish(publishedEvent1);

        BlockingStream<TrackedEventMessage> stream = testSubject.openStream(null);
        assertEquals("Event1", stream.peek().map(Message::payload).map(Object::toString).orElse("None"));
        assertTrue(stream.hasNextAvailable());
        assertTrue(stream.hasNextAvailable(10, TimeUnit.SECONDS));
    }

    @SuppressWarnings("resource")
    @Test
    void openStreamWithWrongToken() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.openStream(new GlobalSequenceTrackingToken(0L)));
    }

    @Test
    void openStreamWithNullTokenReturnsFirstEvent() throws InterruptedException {
        EventMessage message = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(message);

        BlockingStream<TrackedEventMessage> actual = testSubject.openStream(null);
        assertNotNull(actual);
        TrackedEventMessage trackedEventMessage = actual.nextAvailable();
        assertEquals(message.identifier(), trackedEventMessage.identifier());
        assertEquals(message.payload(), trackedEventMessage.payload());
    }

    @Test
    void longPoll() throws InterruptedException {
        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTokenAt(Instant.now()));

        long beforePollTime = System.currentTimeMillis();
        assertFalse(singleEventStream.hasNextAvailable(100, TimeUnit.MILLISECONDS));
        long pollTime = System.currentTimeMillis() - beforePollTime;
        // allow for some deviation in polling time
        assertTrue(pollTime > 80, "Poll time too short: " + pollTime + "ms");
        assertTrue(pollTime < 120, "Poll time too long: " + pollTime + "ms");

        singleEventStream.close();
    }

    @Test
    void longPollMessageImmediatelyAvailable() throws InterruptedException {
        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTokenAt(Instant.now()));

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event1");
//        eventStoreB.publish(pubToStreamB);

        long beforePollTime = System.currentTimeMillis();
        boolean hasNextAvailable = singleEventStream.hasNextAvailable(100, TimeUnit.MILLISECONDS);
        long afterPollTime = System.currentTimeMillis();
        assertTrue(hasNextAvailable);
        assertTrue(afterPollTime - beforePollTime < 10);

        singleEventStream.close();
    }

    @Test
    void multiPublishAndConsume() throws InterruptedException {
        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event2");
//        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTokenAt(recentTimeStamp()));

        assertTrue(singleEventStream.hasNextAvailable());

        //order published must be same as order consumed
        assertEquals(pubToStreamA.payload(), singleEventStream.nextAvailable().payload());
        assertEquals(pubToStreamB.payload(), singleEventStream.nextAvailable().payload());
        assertFalse(singleEventStream.hasNextAvailable());

        singleEventStream.close();
    }

    @Test
    void peek() throws InterruptedException {
        EventMessage publishedEvent = EventTestUtils.asEventMessage("Event1");

//        eventStoreA.publish(publishedEvent);

        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTokenAt(recentTimeStamp()));

        assertTrue(singleEventStream.peek().isPresent());
        assertEquals(publishedEvent.payload(), singleEventStream.peek().get().payload());

        //message is still consumable
        assertEquals(publishedEvent.payload(), singleEventStream.nextAvailable().payload());

        singleEventStream.close();
    }

    @Test
    void peekWithMultipleStreams() throws InterruptedException {
        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event2");
//        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage> singleEventStream =
                testSubject.openStream(testSubject.createTokenAt(recentTimeStamp()));

        assertTrue(singleEventStream.peek().isPresent());
        TrackedEventMessage peekedMessageA = singleEventStream.peek().get();
        MultiSourceTrackingToken tokenA = (MultiSourceTrackingToken) peekedMessageA.trackingToken();
        assertEquals(pubToStreamA.payload(), peekedMessageA.payload());

        //message is still consumable and consumed message equal to peeked
        assertEquals(peekedMessageA.payload(), singleEventStream.nextAvailable().payload());

        //peek and consume another
        assertTrue(singleEventStream.peek().isPresent());
        TrackedEventMessage peekedMessageB = singleEventStream.peek().get();
        MultiSourceTrackingToken tokenB = (MultiSourceTrackingToken) peekedMessageB.trackingToken();
        assertEquals(pubToStreamB.payload(), peekedMessageB.payload());

        assertEquals(peekedMessageB.payload(), singleEventStream.nextAvailable().payload());

        //consuming from second stream doesn't alter token from first stream
        assertEquals(tokenA.getTokenForStream("eventStoreA"), tokenB.getTokenForStream("eventStoreA"));

        singleEventStream.close();
    }

    /**
     * Create a timestamp a bit prior to {@link Instant#now()}. This can for example be used on
     * {@link StreamableMessageSource#createTokenAt(Instant)} right after the insertion of some events, so that the
     * created token will take in these new events. Simply using {@link Instant#now()} allows for a window of
     * opportunity which misses these recent events.
     *
     * @return a timestamp a bit prior to {@link Instant#now()}
     */
    private static Instant recentTimeStamp() {
        return Instant.now().minusMillis(1000);
    }

    @Test
    void createLatestToken() {
        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event2");
//        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken tailToken = testSubject.createTailToken();

        OptionalLong storeAPosition = tailToken.getTokenForStream("eventStoreA").position();
        assertTrue(storeAPosition.isPresent());
        assertEquals(-1L, storeAPosition.getAsLong());
        OptionalLong storeBPosition = tailToken.getTokenForStream("eventStoreB").position();
        assertTrue(storeBPosition.isPresent());
        assertEquals(-1L, storeBPosition.getAsLong());
    }

    @Test
    void createFirstToken() {
        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event2");
//        eventStoreB.publish(pubToStreamB);
//        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken headToken = testSubject.createHeadToken();

        OptionalLong storeAPosition = headToken.getTokenForStream("eventStoreA").position();
        assertTrue(storeAPosition.isPresent());
        assertEquals(0L, storeAPosition.getAsLong());
        OptionalLong storeBPosition = headToken.getTokenForStream("eventStoreB").position();
        assertTrue(storeBPosition.isPresent());
        assertEquals(1L, storeBPosition.getAsLong());
    }

    @Test
    void createTokenAt() throws InterruptedException {
        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);
//        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event2");
//        eventStoreB.publish(pubToStreamB);

        // Token should track events in eventStoreB and skip those in eventStoreA
        MultiSourceTrackingToken createdAtToken = testSubject.createTokenAt(Instant.now().minusMillis(10));

        // storeA's token resembles an storeA head token since the created token at timestamp is after all its events
//        assertEquals(eventStoreA.createHeadToken(), createdAtToken.getTokenForStream("eventStoreA"));
        OptionalLong storeBPosition = createdAtToken.getTokenForStream("eventStoreB").position();
        assertTrue(storeBPosition.isPresent());
        assertEquals(-1L, storeBPosition.getAsLong());
    }

    @Test
    void createTokenSince() throws InterruptedException {
        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);
//        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event2");
//        eventStoreB.publish(pubToStreamB);

        // Token should track events in eventStoreB and skip those in eventStoreA
        MultiSourceTrackingToken createdSinceToken = testSubject.createTokenSince(Duration.ofMillis(10));

        // storeA's token resembles an storeA head token since the created token at timestamp is after all its events
//        assertEquals(eventStoreA.createHeadToken(), createdSinceToken.getTokenForStream("eventStoreA"));
        OptionalLong storeBPosition = createdSinceToken.getTokenForStream("eventStoreB").position();
        assertTrue(storeBPosition.isPresent());
        assertEquals(-1L, storeBPosition.getAsLong());
    }

    @SuppressWarnings("resource")
    @Test
    void configuredDifferentComparator() throws InterruptedException {
        Comparator<Map.Entry<String, TrackedEventMessage>> eventStoreAPriority =
                Comparator.comparing((Map.Entry<String, TrackedEventMessage> e) -> !e.getKey().equals("eventStoreA"))
                          .thenComparing(e -> e.getValue().timestamp());

        EventStore eventStoreC = new StorageEngineBackedEventStore(new InMemoryEventStorageEngine(),
                                                      new SimpleEventBus(),
                                                      new AnnotationBasedTagResolver());

        MultiStreamableMessageSource prioritySourceTestSubject =
                MultiStreamableMessageSource.builder()
//                                            .addMessageSource("eventStoreA", eventStoreA)
//                                            .addMessageSource("eventStoreB", eventStoreB)
//                                            .addMessageSource("eventStoreC", eventStoreC)
                                            .trackedEventComparator(eventStoreAPriority)
                                            .build();

        EventMessage pubToStreamA = EventTestUtils.asEventMessage("Event1");
//        eventStoreA.publish(pubToStreamA);
//        eventStoreA.publish(pubToStreamA);
//        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamC = EventTestUtils.asEventMessage("Event2");
//        eventStoreC.publish(pubToStreamC);

        Thread.sleep(5);

        EventMessage pubToStreamB = EventTestUtils.asEventMessage("Event3");
//        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage> singleEventStream =
                prioritySourceTestSubject.openStream(prioritySourceTestSubject.createTailToken());

        singleEventStream.nextAvailable();
        singleEventStream.nextAvailable();
        singleEventStream.nextAvailable();
        assertEquals(pubToStreamC.payload(), singleEventStream.nextAvailable().payload());
        assertEquals(pubToStreamB.payload(), singleEventStream.nextAvailable().payload());
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Test
    void skipMessagesWithPayloadTypeOfInvokesAllConfiguredStreams() {
        TrackedEventMessage testEvent = new GenericTrackedEventMessage(
                new GlobalSequenceTrackingToken(1), EventTestUtils.asEventMessage("some-payload")
        );

        StreamableMessageSource<TrackedEventMessage> sourceOne = mock(StreamableMessageSource.class);
        BlockingStream<TrackedEventMessage> streamOne = mock(BlockingStream.class);
        when(sourceOne.openStream(any())).thenReturn(streamOne);

        StreamableMessageSource<TrackedEventMessage> sourceTwo = mock(StreamableMessageSource.class);
        BlockingStream<TrackedEventMessage> streamTwo = mock(BlockingStream.class);
        when(sourceTwo.openStream(any())).thenReturn(streamTwo);

        StreamableMessageSource<TrackedEventMessage> sourceThree = mock(StreamableMessageSource.class);
        BlockingStream<TrackedEventMessage> streamThree = mock(BlockingStream.class);
        when(sourceThree.openStream(any())).thenReturn(streamThree);

        MultiStreamableMessageSource multiStream =
                MultiStreamableMessageSource.builder()
                                            .addMessageSource("one", sourceOne)
                                            .addMessageSource("two", sourceTwo)
                                            .addMessageSource("three", sourceThree)
                                            .build();
        BlockingStream<TrackedEventMessage> testSubject = multiStream.openStream(null);

        testSubject.skipMessagesWithPayloadTypeOf(testEvent);

        verify(streamOne).skipMessagesWithPayloadTypeOf(testEvent);
        verify(streamTwo).skipMessagesWithPayloadTypeOf(testEvent);
        verify(streamThree).skipMessagesWithPayloadTypeOf(testEvent);
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Test
    void setOnAvailableCallbackReturnsTrueIfAllStreamsReturnTrue() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Runnable testCallback = () -> invoked.set(true);

        CallbackSupportingBlockingStream streamOne = spy(new CallbackSupportingBlockingStream());
        StreamableMessageSource<TrackedEventMessage> sourceOne = mock(StreamableMessageSource.class);
        when(sourceOne.openStream(any())).thenReturn(streamOne);

        BlockingStream<TrackedEventMessage> streamTwo = mock(BlockingStream.class);
        when(streamTwo.setOnAvailableCallback(any())).thenReturn(true);
        StreamableMessageSource<TrackedEventMessage> sourceTwo = mock(StreamableMessageSource.class);
        when(sourceTwo.openStream(any())).thenReturn(streamTwo);

        BlockingStream<TrackedEventMessage> streamThree = mock(BlockingStream.class);
        when(streamThree.setOnAvailableCallback(any())).thenReturn(true);
        StreamableMessageSource<TrackedEventMessage> sourceThree = mock(StreamableMessageSource.class);
        when(sourceThree.openStream(any())).thenReturn(streamThree);

        MultiStreamableMessageSource multiStream =
                MultiStreamableMessageSource.builder()
                                            .addMessageSource("one", sourceOne)
                                            .addMessageSource("two", sourceTwo)
                                            .addMessageSource("three", sourceThree)
                                            .build();
        BlockingStream<TrackedEventMessage> testSubject = multiStream.openStream(null);

        assertTrue(testSubject.setOnAvailableCallback(testCallback));

        verify(streamOne).setOnAvailableCallback(testCallback);
        verify(streamTwo).setOnAvailableCallback(testCallback);
        verify(streamThree).setOnAvailableCallback(testCallback);

        streamOne.invokeCallback();
        assertTrue(invoked.get());
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Test
    void setOnAvailableCallbackReturnsFalseIfOneStreamsReturnsFalse() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Runnable testCallback = () -> invoked.set(true);

        CallbackSupportingBlockingStream streamOne = spy(new CallbackSupportingBlockingStream());
        StreamableMessageSource<TrackedEventMessage> sourceOne = mock(StreamableMessageSource.class);
        when(sourceOne.openStream(any())).thenReturn(streamOne);

        // Stream two does not support callbacks
        BlockingStream<TrackedEventMessage> streamTwo = mock(BlockingStream.class);
        when(streamTwo.setOnAvailableCallback(any())).thenReturn(false);
        StreamableMessageSource<TrackedEventMessage> sourceTwo = mock(StreamableMessageSource.class);
        when(sourceTwo.openStream(any())).thenReturn(streamTwo);

        BlockingStream<TrackedEventMessage> streamThree = mock(BlockingStream.class);
        when(streamThree.setOnAvailableCallback(any())).thenReturn(true);
        StreamableMessageSource<TrackedEventMessage> sourceThree = mock(StreamableMessageSource.class);
        when(sourceThree.openStream(any())).thenReturn(streamThree);

        MultiStreamableMessageSource multiStream =
                MultiStreamableMessageSource.builder()
                                            .addMessageSource("one", sourceOne)
                                            .addMessageSource("two", sourceTwo)
                                            .addMessageSource("three", sourceThree)
                                            .build();
        BlockingStream<TrackedEventMessage> testSubject = multiStream.openStream(null);

        assertFalse(testSubject.setOnAvailableCallback(testCallback));

        verify(streamOne).setOnAvailableCallback(testCallback);
        verify(streamTwo).setOnAvailableCallback(testCallback);
        verify(streamThree).setOnAvailableCallback(testCallback);

        // "invoked" results in true, as the callback is not removed if one of the streams does not support it.
        streamOne.invokeCallback();
        assertTrue(invoked.get());
    }

    private static class CallbackSupportingBlockingStream implements BlockingStream<TrackedEventMessage> {

        private Runnable callback;

        @Override
        public Optional<TrackedEventMessage> peek() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("RedundantThrows")
        @Override
        public TrackedEventMessage nextAvailable() throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setOnAvailableCallback(Runnable callback) {
            this.callback = callback;
            return true;
        }

        private void invokeCallback() {
            callback.run();
        }
    }
}
