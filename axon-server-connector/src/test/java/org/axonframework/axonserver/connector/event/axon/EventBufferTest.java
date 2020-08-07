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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.event.impl.BufferedEventStream;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.grpc.stub.ClientCallStreamObserver;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.mockito.stubbing.Answer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class to verify the implementation of the {@link EventBuffer} class.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
class EventBufferTest {

    private EventUpcaster stubUpcaster;

    private EventBuffer testSubject;

    private org.axonframework.serialization.SerializedObject<byte[]> serializedObject;
    private BufferedEventStream eventStream;

    @BeforeEach
    void setUp() {
        stubUpcaster = mock(EventUpcaster.class);
        //noinspection unchecked
        when(stubUpcaster.upcast(any()))
                .thenAnswer((Answer<Stream<IntermediateEventRepresentation>>) invocationOnMock ->
                        (Stream<IntermediateEventRepresentation>) invocationOnMock.getArguments()[0]
                );

        XStreamSerializer serializer = TestSerializer.secureXStreamSerializer();
        serializedObject = serializer.serialize("some object", byte[].class);

        eventStream = new BufferedEventStream(0, 100, 1, false);
        //noinspection unchecked
        eventStream.beforeStart(mock(ClientCallStreamObserver.class));
        testSubject = new EventBuffer(eventStream, stubUpcaster, serializer, false);
    }

    @Test
    @Timeout(value = 450, unit = TimeUnit.MILLISECONDS)
    void testDataUpcastAndDeserialized() {
        assertFalse(testSubject.hasNextAvailable());
        eventStream.onNext(createEventData(1L));
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

    @Test
    void testHasNextAvailableThrowsExceptionWhenStreamFailed() {
        RuntimeException expected = new RuntimeException("Some Exception");
        eventStream.onError(expected);

        assertThrows(RuntimeException.class, () -> testSubject.hasNextAvailable(0, TimeUnit.SECONDS));

        // a second attempt should still throw the exception
        assertThrows(RuntimeException.class, () -> testSubject.hasNextAvailable(0, TimeUnit.SECONDS));
    }

    @SuppressWarnings("SameParameterValue")
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
