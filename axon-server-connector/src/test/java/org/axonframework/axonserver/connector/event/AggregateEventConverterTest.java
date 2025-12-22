/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.axonserver.connector.event;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link AggregateEventConverter}.
 *
 * @author Mateusz Nowak
 */
class AggregateEventConverterTest {

    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final long EVENT_TIMESTAMP = Instant.now().toEpochMilli();
    private static final String PAYLOAD_TYPE = "com.example.OrderCreated";
    private static final String PAYLOAD_REVISION = "1.0";
    private static final byte[] PAYLOAD_DATA = "{\"orderId\":\"order-123\"}".getBytes();

    private AggregateEventConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AggregateEventConverter();
    }

    @Nested
    class SingletonInstance {

        @Test
        void singletonInstanceIsNotNull() {
            assertThat(AggregateEventConverter.INSTANCE).isNotNull();
        }

        @Test
        void singletonInstanceIsSameAsNewInstance() {
            // given
            Event event = createEvent(PAYLOAD_TYPE, PAYLOAD_REVISION, PAYLOAD_DATA);

            // when
            EventMessage resultFromSingleton = AggregateEventConverter.INSTANCE.apply(event);
            EventMessage resultFromNewInstance = testSubject.apply(event);

            // then
            assertThat(resultFromSingleton.identifier()).isEqualTo(resultFromNewInstance.identifier());
            assertThat(resultFromSingleton.type()).isEqualTo(resultFromNewInstance.type());
        }
    }

    @Nested
    class ConvertEvent {

        @Test
        void convertsEventWithAllFieldsPopulated() {
            // given
            Event event = createEvent(PAYLOAD_TYPE, PAYLOAD_REVISION, PAYLOAD_DATA);

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.identifier()).isEqualTo(EVENT_ID);
            assertThat(result.type().name()).isEqualTo(PAYLOAD_TYPE);
            assertThat(result.type().version()).isEqualTo(PAYLOAD_REVISION);
            assertThat(result.payloadAs(byte[].class)).isEqualTo(PAYLOAD_DATA);
            assertThat(result.timestamp().toEpochMilli()).isEqualTo(EVENT_TIMESTAMP);
        }

        @Test
        void usesDefaultVersionWhenRevisionIsEmpty() {
            // given
            Event event = createEvent(PAYLOAD_TYPE, "", PAYLOAD_DATA);

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.type().version()).isEqualTo(MessageType.DEFAULT_VERSION);
        }

        @Test
        void usesDefaultVersionWhenRevisionIsNotSet() {
            // given
            Event event = Event.newBuilder()
                               .setMessageIdentifier(EVENT_ID)
                               .setTimestamp(EVENT_TIMESTAMP)
                               .setPayload(SerializedObject.newBuilder()
                                                           .setType(PAYLOAD_TYPE)
                                                           // No revision set - will be empty
                                                           .setData(ByteString.copyFrom(PAYLOAD_DATA))
                                                           .build())
                               .build();

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.type().version()).isEqualTo(MessageType.DEFAULT_VERSION);
        }

        @Test
        void preservesPayloadAsRawBytes() {
            // given
            byte[] complexPayload = "{\"nested\":{\"field\":\"value\"},\"array\":[1,2,3]}".getBytes();
            Event event = createEvent(PAYLOAD_TYPE, PAYLOAD_REVISION, complexPayload);

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.payloadAs(byte[].class)).isEqualTo(complexPayload);
        }

        @Test
        void convertsMetadataCorrectly() {
            // given
            Event event = Event.newBuilder()
                               .setMessageIdentifier(EVENT_ID)
                               .setTimestamp(EVENT_TIMESTAMP)
                               .setPayload(SerializedObject.newBuilder()
                                                           .setType(PAYLOAD_TYPE)
                                                           .setRevision(PAYLOAD_REVISION)
                                                           .setData(ByteString.copyFrom(PAYLOAD_DATA))
                                                           .build())
                               .putMetaData("stringKey", MetaDataValue.newBuilder()
                                                                      .setTextValue("stringValue")
                                                                      .build())
                               .putMetaData("numberKey", MetaDataValue.newBuilder()
                                                                      .setNumberValue(42)
                                                                      .build())
                               .putMetaData("boolKey", MetaDataValue.newBuilder()
                                                                    .setBooleanValue(true)
                                                                    .build())
                               .build();

            // when
            EventMessage result = testSubject.apply(event);

            // then - MetadataConverter converts all values to String
            assertThat(result.metadata()).containsEntry("stringKey", "stringValue");
            assertThat(result.metadata()).containsEntry("numberKey", "42");
            assertThat(result.metadata()).containsEntry("boolKey", "true");
        }

        @Test
        void convertsTimestampToInstant() {
            // given
            long specificTimestamp = 1702656000000L; // 2023-12-15T16:00:00Z
            Event event = Event.newBuilder()
                               .setMessageIdentifier(EVENT_ID)
                               .setTimestamp(specificTimestamp)
                               .setPayload(SerializedObject.newBuilder()
                                                           .setType(PAYLOAD_TYPE)
                                                           .setRevision(PAYLOAD_REVISION)
                                                           .setData(ByteString.copyFrom(PAYLOAD_DATA))
                                                           .build())
                               .build();

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.timestamp()).isEqualTo(Instant.ofEpochMilli(specificTimestamp));
        }

        @Test
        void handlesEmptyMetadata() {
            // given
            Event event = createEvent(PAYLOAD_TYPE, PAYLOAD_REVISION, PAYLOAD_DATA);

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.metadata()).isEmpty();
        }

        @Test
        void handlesEmptyPayloadData() {
            // given
            Event event = createEvent(PAYLOAD_TYPE, PAYLOAD_REVISION, new byte[0]);

            // when
            EventMessage result = testSubject.apply(event);

            // then
            assertThat(result.payloadAs(byte[].class)).isEmpty();
        }
    }

    private Event createEvent(String payloadType, String revision, byte[] payloadData) {
        return Event.newBuilder()
                    .setMessageIdentifier(EVENT_ID)
                    .setTimestamp(EVENT_TIMESTAMP)
                    .setPayload(SerializedObject.newBuilder()
                                                .setType(payloadType)
                                                .setRevision(revision)
                                                .setData(ByteString.copyFrom(payloadData))
                                                .build())
                    .build();
    }
}
