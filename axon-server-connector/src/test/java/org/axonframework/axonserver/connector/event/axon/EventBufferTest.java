/*
 * Copyright (c) 2010-2023. Axon Framework
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
import io.axoniq.axonserver.grpc.control.ClientIdentification;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.grpc.stub.ClientCallStreamObserver;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.mockito.stubbing.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify the implementation of the {@link EventBuffer} class.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
class EventBufferTest {

    private final static XStreamSerializer SERIALIZER = TestSerializer.xStreamSerializer();

    private static final org.axonframework.serialization.SerializedObject<byte[]> SERIALIZED_OBJECT =
            SERIALIZER.serialize("some object", byte[].class);
    private static final EventWithToken TEST_EVENT_WITH_TOKEN =
            EventWithToken.newBuilder()
                          .setToken(1L)
                          .setEvent(Event.newBuilder()
                                         .setPayload(SerializedObject.newBuilder()
                                                                     .setData(ByteString.copyFrom(
                                                                             SERIALIZED_OBJECT.getData()
                                                                     ))
                                                                     .setType(SERIALIZED_OBJECT.getType().getName())
                                                                     .build())
                                         .setMessageIdentifier(UUID.randomUUID().toString())
                                         .setAggregateType("Test")
                                         .setAggregateSequenceNumber(1L)
                                         .setTimestamp(System.currentTimeMillis())
                                         .setAggregateIdentifier("1235")
                                         .build())
                          .build();

    private EventUpcaster stubUpcaster;
    private BufferedEventStream eventStream;

    private EventBuffer testSubject;

    @BeforeEach
    void setUp() {
        stubUpcaster = mock(EventUpcaster.class);
        //noinspection unchecked
        when(stubUpcaster.upcast(any()))
                .thenAnswer((Answer<Stream<IntermediateEventRepresentation>>) invocationOnMock ->
                        (Stream<IntermediateEventRepresentation>) invocationOnMock.getArguments()[0]
                );

        ClientIdentification clientId = ClientIdentification.newBuilder()
                                                            .setClientId("some-client-id")
                                                            .build();
        eventStream = new BufferedEventStream(clientId, 0, 100, 1, false);
        //noinspection unchecked
        eventStream.beforeStart(mock(ClientCallStreamObserver.class));
        testSubject = new EventBuffer(eventStream, stubUpcaster, SERIALIZER, false);
    }

    @Test
    @Timeout(value = 450, unit = TimeUnit.MILLISECONDS)
    void dataUpcastAndDeserialized() {
        assertFalse(testSubject.hasNextAvailable());
        eventStream.onNext(TEST_EVENT_WITH_TOKEN);
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
    void hasNextAvailableThrowsAxonServerExceptionWhenStreamFailed() {
        TestException testException = new TestException();
        eventStream.onError(testException);

        AxonServerException actual =
                assertThrows(AxonServerException.class, () -> testSubject.hasNextAvailable(0, TimeUnit.SECONDS));
        assertEquals(testException, actual.getCause());

        // a second attempt should still throw the exception
        assertThrows(AxonServerException.class, () -> testSubject.hasNextAvailable(0, TimeUnit.SECONDS));
    }

    private static class TestException extends Exception {

        private static final long serialVersionUID = 5181730247751626376L;
    }
}
