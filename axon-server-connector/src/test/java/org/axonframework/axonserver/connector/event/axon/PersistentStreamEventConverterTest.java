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

package org.axonframework.axonserver.connector.event.axon;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import org.axonframework.axonserver.connector.event.AggregateEventConverter;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class validating the {@link PersistentStreamEventConverter}.
 *
 * @author Mateusz Nowak
 */
class PersistentStreamEventConverterTest {

    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final long EVENT_TIMESTAMP = Instant.now().toEpochMilli();
    private static final String PAYLOAD_TYPE = "com.example.OrderCreated";
    private static final String PAYLOAD_REVISION = "1.0";
    private static final byte[] PAYLOAD_DATA = "{\"orderId\":\"order-123\"}".getBytes();

    private static final String AGGREGATE_ID = "aggregate-123";
    private static final String AGGREGATE_TYPE = "OrderAggregate";
    private static final long AGGREGATE_SEQ = 5L;

    private PersistentStreamEventConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new PersistentStreamEventConverter(AggregateEventConverter.INSTANCE);
    }

    @Nested
    class Construction {

        @Test
        void throwsNullPointerExceptionWhenMessageConverterIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new PersistentStreamEventConverter(null))
                    .withMessage("messageConverter must not be null");
        }

        @Test
        void constructsWithCustomMessageConverter() {
            // given
            EventMessage mockMessage = createMockEventMessage();

            // when
            PersistentStreamEventConverter converter = new PersistentStreamEventConverter(event -> mockMessage);
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(100L, false);

            // then
            MessageStream.Entry<EventMessage> result = converter.apply(persistentEvent);
            assertThat(result.message()).isSameAs(mockMessage);
        }
    }

    @Nested
    class TrackingTokenCreation {

        @Test
        void createsGlobalSequenceTrackingTokenForNonReplayEvent() {
            // given
            long tokenValue = 42L;
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(tokenValue, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            assertThat(trackingToken).isInstanceOf(GlobalSequenceTrackingToken.class);
            assertThat(((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex()).isEqualTo(tokenValue);
        }

        @Test
        void createsReplayTokenForReplayEvent() {
            // given
            long tokenValue = 100L;
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(tokenValue, true);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            assertThat(trackingToken).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(trackingToken)).isTrue();
        }

        @Test
        void replayTokenContainsCurrentPositionAsGlobalSequenceToken() {
            // given
            long tokenValue = 50L;
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(tokenValue, true);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            ReplayToken replayToken = (ReplayToken) trackingToken;

            // The current token inside ReplayToken should be the global sequence token
            TrackingToken currentToken = replayToken.getCurrentToken();
            assertThat(currentToken).isInstanceOf(GlobalSequenceTrackingToken.class);
            assertThat(((GlobalSequenceTrackingToken) currentToken).getGlobalIndex()).isEqualTo(tokenValue);
        }

        @Test
        void handlesZeroTokenValue() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(0L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            assertThat(((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex()).isEqualTo(0L);
        }

        @Test
        void handlesLargeTokenValue() {
            // given
            long largeToken = Long.MAX_VALUE - 1;
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(largeToken, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            assertThat(((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex()).isEqualTo(largeToken);
        }
    }

    @Nested
    class ContextBuilding {

        @Test
        void contextContainsTrackingToken() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.containsResource(TrackingToken.RESOURCE_KEY)).isTrue();
            assertThat(result.getResource(TrackingToken.RESOURCE_KEY)).isNotNull();
        }

        @Test
        void contextContainsAggregateResourcesWhenAggregateIdentifierIsPresent() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEventWithAggregate(
                    10L, false, AGGREGATE_ID, AGGREGATE_TYPE, AGGREGATE_SEQ
            );

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY))
                    .isEqualTo(AGGREGATE_ID);
            assertThat(result.getResource(LegacyResources.AGGREGATE_TYPE_KEY))
                    .isEqualTo(AGGREGATE_TYPE);
            assertThat(result.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY))
                    .isEqualTo(AGGREGATE_SEQ);
        }

        @Test
        void contextDoesNotContainAggregateResourcesWhenAggregateIdentifierIsEmpty() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEventWithAggregate(
                    10L, false, "", AGGREGATE_TYPE, AGGREGATE_SEQ
            );

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.containsResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY)).isFalse();
            assertThat(result.containsResource(LegacyResources.AGGREGATE_TYPE_KEY)).isFalse();
            assertThat(result.containsResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY)).isFalse();
        }

        @Test
        void contextDoesNotContainAggregateResourcesWhenAggregateIdentifierIsNotSet() {
            // given - event without aggregate identifier set (uses default empty string)
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.containsResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY)).isFalse();
            assertThat(result.containsResource(LegacyResources.AGGREGATE_TYPE_KEY)).isFalse();
            assertThat(result.containsResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY)).isFalse();
        }

        @Test
        void contextPreservesAggregateSequenceNumberAsZero() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEventWithAggregate(
                    10L, false, AGGREGATE_ID, AGGREGATE_TYPE, 0L
            );

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY))
                    .isEqualTo(0L);
        }
    }

    @Nested
    class MessageConversion {

        @Test
        void delegatesToMessageConverter() {
            // given
            Event event = createEvent();
            PersistentStreamEvent persistentEvent = wrapInPersistentStreamEvent(event, 10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.message().identifier()).isEqualTo(EVENT_ID);
        }

        @Test
        void preservesEventMetadataThroughConversion() {
            // given
            Event event = Event.newBuilder()
                               .setMessageIdentifier(EVENT_ID)
                               .setTimestamp(EVENT_TIMESTAMP)
                               .setPayload(createSerializedPayload())
                               .putMetaData("correlationId", MetaDataValue.newBuilder()
                                                                          .setTextValue("corr-123")
                                                                          .build())
                               .putMetaData("traceId", MetaDataValue.newBuilder()
                                                                    .setTextValue("trace-456")
                                                                    .build())
                               .build();
            PersistentStreamEvent persistentEvent = wrapInPersistentStreamEvent(event, 10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.message().metadata())
                    .containsEntry("correlationId", "corr-123")
                    .containsEntry("traceId", "trace-456");
        }

        @Test
        void preservesEventPayload() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.message().payloadAs(byte[].class)).isEqualTo(PAYLOAD_DATA);
        }

        @Test
        void preservesEventTimestamp() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.message().timestamp().toEpochMilli()).isEqualTo(EVENT_TIMESTAMP);
        }

        @Test
        void preservesEventType() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.message().type().name()).isEqualTo(PAYLOAD_TYPE);
            assertThat(result.message().type().version()).isEqualTo(PAYLOAD_REVISION);
        }
    }

    @Nested
    class EntryStructure {

        @Test
        void returnsSimpleEntryWithMessageAndContext() {
            // given
            PersistentStreamEvent persistentEvent = createPersistentStreamEvent(10L, false);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result).isNotNull();
            assertThat(result.message()).isNotNull();
            // Entry extends Context, so it has resource methods
            assertThat(result.containsResource(TrackingToken.RESOURCE_KEY)).isTrue();
        }

        @Test
        void entryMessageMatchesConvertedEvent() {
            // given
            Event event = createEvent();
            PersistentStreamEvent persistentEvent = wrapInPersistentStreamEvent(event, 10L, false);

            EventMessage expectedMessage = AggregateEventConverter.INSTANCE.apply(event);

            // when
            MessageStream.Entry<EventMessage> result = testSubject.apply(persistentEvent);

            // then
            assertThat(result.message().identifier()).isEqualTo(expectedMessage.identifier());
            assertThat(result.message().type()).isEqualTo(expectedMessage.type());
            assertThat(result.message().timestamp()).isEqualTo(expectedMessage.timestamp());
        }
    }

    // Helper methods

    private PersistentStreamEvent createPersistentStreamEvent(long token, boolean replay) {
        Event event = createEvent();
        return wrapInPersistentStreamEvent(event, token, replay);
    }

    private PersistentStreamEvent createPersistentStreamEventWithAggregate(
            long token, boolean replay, String aggregateId, String aggregateType, long aggregateSeq
    ) {
        Event event = Event.newBuilder()
                           .setMessageIdentifier(EVENT_ID)
                           .setTimestamp(EVENT_TIMESTAMP)
                           .setPayload(createSerializedPayload())
                           .setAggregateIdentifier(aggregateId)
                           .setAggregateType(aggregateType)
                           .setAggregateSequenceNumber(aggregateSeq)
                           .build();
        return wrapInPersistentStreamEvent(event, token, replay);
    }

    private PersistentStreamEvent wrapInPersistentStreamEvent(Event event, long token, boolean replay) {
        EventWithToken eventWithToken = EventWithToken.newBuilder()
                                                      .setToken(token)
                                                      .setEvent(event)
                                                      .build();
        return PersistentStreamEvent.newBuilder()
                                    .setEvent(eventWithToken)
                                    .setReplay(replay)
                                    .build();
    }

    private Event createEvent() {
        return Event.newBuilder()
                    .setMessageIdentifier(EVENT_ID)
                    .setTimestamp(EVENT_TIMESTAMP)
                    .setPayload(createSerializedPayload())
                    .build();
    }

    private SerializedObject createSerializedPayload() {
        return SerializedObject.newBuilder()
                               .setType(PAYLOAD_TYPE)
                               .setRevision(PAYLOAD_REVISION)
                               .setData(ByteString.copyFrom(PAYLOAD_DATA))
                               .build();
    }

    private EventMessage createMockEventMessage() {
        Event event = createEvent();
        return AggregateEventConverter.INSTANCE.apply(event);
    }
}
