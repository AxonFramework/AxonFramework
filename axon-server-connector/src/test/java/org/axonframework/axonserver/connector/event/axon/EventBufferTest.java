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
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.mockito.stubbing.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

public class EventBufferTest {
    private EventUpcaster stubUpcaster;
    private EventBuffer testSubject;

    private XStreamSerializer serializer;
    private org.axonframework.serialization.SerializedObject<byte[]> serializedObject;

    @Before
    public void setUp() {
        stubUpcaster = mock(EventUpcaster.class);
        when(stubUpcaster.upcast(any())).thenAnswer((Answer<Stream<IntermediateEventRepresentation>>) invocationOnMock -> (Stream<IntermediateEventRepresentation>) invocationOnMock.getArguments()[0]);
        serializer = XStreamSerializer.builder().build();

        serializedObject = serializer.serialize("some object", byte[].class);
    }

    @Test
    public void testDataUpcastAndDeserialized() throws InterruptedException {
        testSubject = new EventBuffer(stubUpcaster, serializer);

        assertFalse(testSubject.hasNextAvailable());
        testSubject.push(createEventData(1L));
        assertTrue(testSubject.hasNextAvailable());

        TrackedEventMessage<?> peeked = testSubject.peek().orElseThrow(() -> new AssertionError("Expected value to be available"));
        assertEquals(new GlobalSequenceTrackingToken(1L), peeked.trackingToken());
        assertTrue(peeked instanceof DomainEventMessage<?>);

        assertTrue(testSubject.hasNextAvailable());
        assertTrue(testSubject.hasNextAvailable(1, TimeUnit.SECONDS));
        testSubject.nextAvailable();
        assertFalse(testSubject.hasNextAvailable());
        assertFalse(testSubject.hasNextAvailable(10, TimeUnit.MILLISECONDS));

        verify(stubUpcaster).upcast(isA(Stream.class));
    }

    @Test
    public void testConsumptionIsRecorded() {
        stubUpcaster = stream -> stream.filter(i -> false);
        testSubject = new EventBuffer(stubUpcaster, serializer);

        testSubject.push(createEventData(1));
        testSubject.push(createEventData(2));
        testSubject.push(createEventData(3));

        AtomicInteger consumed = new AtomicInteger();
        testSubject.registerConsumeListener(consumed::addAndGet);

        testSubject.peek(); // this should consume 3 incoming messages
        assertEquals(3, consumed.get());
    }

    private EventWithToken createEventData(long sequence) {
        return EventWithToken.newBuilder()
                             .setToken(sequence)
                             .setEvent(Event.newBuilder()
                                            .setPayload(SerializedObject.newBuilder()
                                                                        .setData(ByteString.copyFrom(serializedObject.getData()))
                                                                        .setType(serializedObject.getType().getName()))
                                            .setMessageIdentifier(UUID.randomUUID().toString())
                                            .setAggregateType("Test")
                                            .setAggregateSequenceNumber(sequence)
                                            .setTimestamp(System.currentTimeMillis())
                                            .setAggregateIdentifier("1235"))
                             .build();
    }


}
