/*
 * Copyright (c) 2010-2018. Axon Framework
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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import org.axonframework.axonserver.connector.utils.SerializerParameterResolver;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * Test class to verify the implementation of the {@link EventBuffer} class.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
@ExtendWith(SerializerParameterResolver.class)
class EventBufferTest {

    private static final Logger LOG = LoggerFactory.getLogger(EventBufferTest.class);

    private EventUpcaster stubUpcaster;

    private EventBuffer testSubject;

    private org.axonframework.serialization.SerializedObject<byte[]> serializedObject;

    private Serializer eventSerializer;

    @BeforeEach
    void setUp(Serializer serializer, TestInfo testInfo) {
        LOG.info("Running test {} with serializer {}.", testInfo.getDisplayName(), serializer.getClass());

        stubUpcaster = mock(EventUpcaster.class);
        //noinspection unchecked
        when(stubUpcaster.upcast(any()))
                .thenAnswer((Answer<Stream<IntermediateEventRepresentation>>) invocationOnMock ->
                        (Stream<IntermediateEventRepresentation>) invocationOnMock.getArguments()[0]
                );

        serializedObject = serializer.serialize("some object", byte[].class);

        eventSerializer = serializer;
        testSubject = new EventBuffer(stubUpcaster, serializer);
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testDataUpcastAndDeserialized() throws InterruptedException {
        assertFalse(testSubject.hasNextAvailable());
        testSubject.push(createEventData(1L));
        assertTrue(testSubject.hasNextAvailable());

        TrackedEventMessage<?> peeked =
                testSubject.peek().orElseThrow(() -> new AssertionError("Expected value to be available"));
        assertEquals(new GlobalSequenceTrackingToken(1L), peeked.trackingToken());
        assertTrue(peeked instanceof DomainEventMessage<?>);

        assertTrue(testSubject.hasNextAvailable());
        assertTrue(testSubject.hasNextAvailable(1, TimeUnit.SECONDS));
        testSubject.nextAvailable();
        assertFalse(testSubject.hasNextAvailable());
        assertFalse(testSubject.hasNextAvailable(10, TimeUnit.MILLISECONDS));

        //noinspection unchecked
        verify(stubUpcaster).upcast(isA(Stream.class));
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testConsumptionIsRecorded() {
        testSubject = new EventBuffer(stream -> stream.filter(i -> false), eventSerializer);

        testSubject.push(createEventData(1));
        testSubject.push(createEventData(2));
        testSubject.push(createEventData(3));

        AtomicInteger consumed = new AtomicInteger();
        testSubject.registerConsumeListener(consumed::addAndGet);

        testSubject.peek(); // this should consume 3 incoming messages
        assertEquals(3, consumed.get());
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testHasNextAvailableThrowsExceptionWhenStreamFailed() {
        RuntimeException expected = new RuntimeException("Some Exception");
        testSubject.fail(expected);

        assertThrows(RuntimeException.class, () ->
                testSubject.hasNextAvailable(0, TimeUnit.SECONDS));

        // a second attempt should still throw the exception
        assertThrows(RuntimeException.class, () ->
                testSubject.hasNextAvailable(0, TimeUnit.SECONDS));
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    @Timeout(value = 2)
    void testNextAvailableDoesNotBlockIndefinitelyIfTheStreamIsClosedExceptionally()
            throws InterruptedException {
        RuntimeException expected = new RuntimeException("Some Exception");
        AtomicReference<Exception> result = new AtomicReference<>();

        // Create and start "nextAvailable" thread
        Thread pollingThread = new Thread(() -> {
            try {
                testSubject.nextAvailable();
            } catch (Exception e) {
                result.set(e);
            }
        });
        pollingThread.start();
        // Sleep to give "pollingThread" time to enter event polling operation
        Thread.sleep(50);
        // Fail the EventBuffer, which should close the stream
        testSubject.fail(expected);
        // Wait for the pollingThread to be resolved due to the thrown RuntimeException
        pollingThread.join();

        assertEquals(expected, result.get());
    }

    private EventWithToken createEventData(long sequence) {
        SerializedObject payload = SerializedObject.newBuilder()
                                                   .setData(ByteString.copyFrom(serializedObject.getData()))
                                                   .setType(serializedObject.getType().getName())
                                                   .build();

        Event event = Event.newBuilder()
                           .setPayload(payload)
                           .setMessageIdentifier(UUID.randomUUID().toString())
                           .setAggregateType("Test")
                           .setAggregateSequenceNumber(sequence)
                           .setTimestamp(System.currentTimeMillis())
                           .setAggregateIdentifier("1235")
                           .build();

        return EventWithToken.newBuilder()
                             .setToken(sequence)
                             .setEvent(event)
                             .build();
    }
}
