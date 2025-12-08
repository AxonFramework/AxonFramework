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

import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.MessageStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for {@link PersistentStreamEventConverter}.
 *
 * @author Mateusz Nowak
 */
class PersistentStreamEventConverterTest {

    private PersistentStreamEventConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new PersistentStreamEventConverter();
    }

    @Nested
    class Convert {

        @Test
        void convertsNonReplayEventToEntryWithTokenInContext() {
            // given
            String messageId = UUID.randomUUID().toString();
            long timestamp = Instant.now().toEpochMilli();
            long token = 42L;

            PersistentStreamEvent persistentStreamEvent = createPersistentStreamEvent(
                    messageId, "TestEvent", "1.0", token, timestamp, false
            );

            // when
            MessageStream.Entry<EventMessage> result = testSubject.convert(persistentStreamEvent);

            // then
            assertThat(result).isNotNull();
            EventMessage eventMessage = result.message();
            assertThat(eventMessage.identifier()).isEqualTo(messageId);
            assertThat(eventMessage.type().name()).isEqualTo("TestEvent");
            assertThat(eventMessage.type().version()).isEqualTo("1.0");
            assertThat(eventMessage.timestamp().toEpochMilli()).isEqualTo(timestamp);

            // Entry extends Context, so we get the resource directly from the entry
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            assertThat(trackingToken).isInstanceOf(GlobalSequenceTrackingToken.class);
            assertThat(((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex()).isEqualTo(token);
        }

        @Test
        void convertsReplayEventToEntryWithReplayTokenInContext() {
            // given
            String messageId = UUID.randomUUID().toString();
            long timestamp = Instant.now().toEpochMilli();
            long token = 100L;

            PersistentStreamEvent persistentStreamEvent = createPersistentStreamEvent(
                    messageId, "TestEvent", "1.0", token, timestamp, true
            );

            // when
            MessageStream.Entry<EventMessage> result = testSubject.convert(persistentStreamEvent);

            // then
            assertThat(result).isNotNull();
            assertThat(result.message().identifier()).isEqualTo(messageId);

            // Entry extends Context, so we get the resource directly from the entry
            TrackingToken trackingToken = result.getResource(TrackingToken.RESOURCE_KEY);
            assertThat(trackingToken).isInstanceOf(ReplayToken.class);

            ReplayToken replayToken = (ReplayToken) trackingToken;
            assertThat(replayToken.getCurrentToken())
                    .isInstanceOf(GlobalSequenceTrackingToken.class);
            GlobalSequenceTrackingToken currentToken = (GlobalSequenceTrackingToken) replayToken.getCurrentToken();
            assertThat(currentToken.getGlobalIndex()).isEqualTo(token);
        }

        @Test
        void preservesEventMetadata() {
            // given
            String messageId = UUID.randomUUID().toString();
            long timestamp = Instant.now().toEpochMilli();

            Event event = Event.newBuilder()
                    .setMessageIdentifier(messageId)
                    .setTimestamp(timestamp)
                    .setPayload(SerializedObject.newBuilder()
                            .setType("TestEvent")
                            .setRevision("2.0")
                            .build())
                    .putMetaData("key1", io.axoniq.axonserver.grpc.MetaDataValue.newBuilder()
                            .setTextValue("value1")
                            .build())
                    .putMetaData("key2", io.axoniq.axonserver.grpc.MetaDataValue.newBuilder()
                            .setNumberValue(42L)
                            .build())
                    .build();

            EventWithToken eventWithToken = EventWithToken.newBuilder()
                    .setToken(10L)
                    .setEvent(event)
                    .build();

            PersistentStreamEvent persistentStreamEvent = PersistentStreamEvent.newBuilder()
                    .setEvent(eventWithToken)
                    .setReplay(false)
                    .build();

            // when
            MessageStream.Entry<EventMessage> result = testSubject.convert(persistentStreamEvent);

            // then
            assertThat(result.message().metadata().get("key1")).isEqualTo("value1");
            assertThat(result.message().metadata().get("key2")).isEqualTo("42");
        }

        @Test
        void throwsNullPointerExceptionForNullInput() {
            // when / then
            assertThatThrownBy(() -> testSubject.convert(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("PersistentStreamEvent must not be null");
        }
    }

    @Nested
    class ConvertToEventMessage {

        @Test
        void extractsEventMessageWithoutTrackingToken() {
            // given
            String messageId = UUID.randomUUID().toString();
            long timestamp = Instant.now().toEpochMilli();

            PersistentStreamEvent persistentStreamEvent = createPersistentStreamEvent(
                    messageId, "TestEvent", "1.0", 50L, timestamp, false
            );

            // when
            EventMessage result = testSubject.convertToEventMessage(persistentStreamEvent);

            // then
            assertThat(result).isNotNull();
            assertThat(result.identifier()).isEqualTo(messageId);
            assertThat(result.type().name()).isEqualTo("TestEvent");
            assertThat(result.type().version()).isEqualTo("1.0");
            assertThat(result.timestamp().toEpochMilli()).isEqualTo(timestamp);
        }

        @Test
        void throwsNullPointerExceptionForNullInput() {
            // when / then
            assertThatThrownBy(() -> testSubject.convertToEventMessage(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("PersistentStreamEvent must not be null");
        }
    }

    @Nested
    class CreateTrackingToken {

        @Test
        void createsGlobalSequenceTrackingTokenForNonReplayEvent() {
            // given
            long token = 55L;
            PersistentStreamEvent persistentStreamEvent = createPersistentStreamEvent(
                    UUID.randomUUID().toString(), "TestEvent", "1.0", token, System.currentTimeMillis(), false
            );

            // when
            TrackingToken result = testSubject.createTrackingToken(persistentStreamEvent);

            // then
            assertThat(result).isInstanceOf(GlobalSequenceTrackingToken.class);
            GlobalSequenceTrackingToken globalToken = (GlobalSequenceTrackingToken) result;
            assertThat(globalToken.getGlobalIndex()).isEqualTo(token);
        }

        @Test
        void createsReplayTokenWithCorrectBoundsForReplayEvent() {
            // given
            long token = 75L;
            PersistentStreamEvent persistentStreamEvent = createPersistentStreamEvent(
                    UUID.randomUUID().toString(), "TestEvent", "1.0", token, System.currentTimeMillis(), true
            );

            // when
            TrackingToken result = testSubject.createTrackingToken(persistentStreamEvent);

            // then
            assertThat(result).isInstanceOf(ReplayToken.class);
            ReplayToken replayToken = (ReplayToken) result;

            GlobalSequenceTrackingToken currentToken = (GlobalSequenceTrackingToken) replayToken.getCurrentToken();
            assertThat(currentToken.getGlobalIndex()).isEqualTo(token);
        }

        @Test
        void throwsNullPointerExceptionForNullInput() {
            // when / then
            assertThatThrownBy(() -> testSubject.createTrackingToken(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("PersistentStreamEvent must not be null");
        }
    }

    private PersistentStreamEvent createPersistentStreamEvent(
            String messageId,
            String eventType,
            String revision,
            long token,
            long timestamp,
            boolean isReplay
    ) {
        Event event = Event.newBuilder()
                .setMessageIdentifier(messageId)
                .setTimestamp(timestamp)
                .setPayload(SerializedObject.newBuilder()
                        .setType(eventType)
                        .setRevision(revision)
                        .build())
                .build();

        EventWithToken eventWithToken = EventWithToken.newBuilder()
                .setToken(token)
                .setEvent(event)
                .build();

        return PersistentStreamEvent.newBuilder()
                .setEvent(eventWithToken)
                .setReplay(isReplay)
                .build();
    }
}
