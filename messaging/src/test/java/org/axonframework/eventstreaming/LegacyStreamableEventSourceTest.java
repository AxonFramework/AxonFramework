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

package org.axonframework.eventstreaming;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.utils.InMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for the {@link LegacyStreamableEventSource} class, which it's an adapter for the deprecated
 * {@link StreamableMessageSource}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class LegacyStreamableEventSourceTest {

    private InMemoryStreamableEventSource legacyEventSource = new InMemoryStreamableEventSource();
    private LegacyStreamableEventSource<TrackedEventMessage> testSubject = new LegacyStreamableEventSource<>(legacyEventSource);
    private ProcessingContext processingContext = mock(ProcessingContext.class);

    @Nested
    class ConstructorTest {

        @Test
        void doNotSupportEventCriteriaOtherThanAny() {
            assertThatThrownBy(() -> testSubject.open(
                    StreamingCondition.conditionFor(firstToken(), EventCriteria.havingTags("tag1", "tag2")),
                    processingContext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(
                            "Only AnyEvent criteria is supported in this legacy adapter, but received: TagFilteredEventCriteria[tags=[Tag[key=tag1, value=tag2]]]");
        }

        @Test
        void shouldThrowExceptionWithNullDelegate() {
            assertThrows(NullPointerException.class, () ->
                    new LegacyStreamableEventSource<>(null));
        }
    }

    @Nested
    class TrackingTokenTest {

        @Test
        void firstTokenShouldBeSameAsInDelegate() {
            var expected = legacyEventSource.createHeadToken();
            var actual = testSubject.firstToken(processingContext).join();
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void latestTokenShouldBeSameAsInDelegate() {
            var expected = legacyEventSource.createTailToken();
            var actual = testSubject.latestToken(processingContext).join();
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class MessageStreamTest {

        private MessageStream<TrackedEventMessage> messageStream;

        @BeforeEach
        void beforeEach() {
            messageStream = testSubject.open(StreamingCondition.startingFrom(firstToken()), processingContext);
        }

        @AfterEach
        void afterEach() {
            messageStream.close();
        }

        @Nested
        class NextTest {

            @Test
            void shouldReturnEmptyIfNoMessagesAvailable() {
                // when
                var result = messageStream.next();

                // then
                assertThat(result).isEmpty();
            }

            @Test
            void shouldReturnEventIfMessageAvailable() {
                // given
                GlobalSequenceTrackingToken testToken = tokenAt(1);
                var event1 = trackedEventMessage("event-1", testToken);
                legacyEventSource.publishMessage(event1);

                // when
                var result = messageStream.next();

                // then
                assertThat(result).isPresent();
                assertEvent(result.get().message(), event1);
                assertThat(TrackingToken.fromContext(result.get())).hasValue(testToken);
            }
        }

        @Nested
        class PeekTest {

            @Test
            void shouldReturnEmptyIfNoMessagesAvailable() {
                // when
                var result = messageStream.peek();

                // then
                assertThat(result).isEmpty();
            }

            @Test
            void shouldReturnEventIfMessageAvailable() {
                // given
                GlobalSequenceTrackingToken testToken = tokenAt(1);
                var event1 = trackedEventMessage("event-1", testToken);
                legacyEventSource.publishMessage(event1);

                // when
                var result = messageStream.peek();

                // then
                assertThat(result).isPresent();
                assertEvent(result.get().message(), event1);
                assertThat(TrackingToken.fromContext(result.get())).hasValue(testToken);
            }

            @Test
            void shouldNotAdvanceTeStream() {
                // given
                GlobalSequenceTrackingToken testToken1 = tokenAt(1);
                GlobalSequenceTrackingToken testToken2 = tokenAt(1);
                var event1 = trackedEventMessage("event-1", testToken1);
                var event2 = trackedEventMessage("event-2", testToken2);
                legacyEventSource.publishMessage(event1);
                legacyEventSource.publishMessage(event2);

                // when
                var next = messageStream.next();

                // then
                assertThat(next).isPresent();
                assertEvent(next.get().message(), event1);

                // when
                var result1 = messageStream.peek();
                var result2 = messageStream.peek();
                var result3 = messageStream.peek();

                // then
                assertThat(result1).isEqualTo(result2);
                assertThat(result1).isEqualTo(result3);
            }
        }

        @Nested
        class CompletedTest {

            @Test
            void shouldNotConsiderCompletedIfMessagesAvailable() {
                // given
                GlobalSequenceTrackingToken testToken = tokenAt(1);
                var event1 = trackedEventMessage("event-1", testToken);
                legacyEventSource.publishMessage(event1);

                // when
                var result = messageStream.isCompleted();

                // then
                assertThat(result).isFalse();
            }

            @Test
            void shouldConsiderCompletedIfNoMessagesAvailable() {
                // when
                var result = messageStream.isCompleted();

                // then
                assertThat(result).isTrue();
            }
        }

        private static void assertEvent(EventMessage actual, EventMessage expected) {
            assertEquals(expected.identifier(), actual.identifier());
            assertEquals(expected.payload(), actual.payload());
            assertEquals(expected.timestamp(), actual.timestamp());
            assertEquals(expected.metadata(), actual.metadata());
        }
    }

    private static GlobalSequenceTrackingToken firstToken() {
        return tokenAt(0L);
    }

    @Nonnull
    private static GlobalSequenceTrackingToken tokenAt(long globalIndex) {
        return new GlobalSequenceTrackingToken(globalIndex);
    }

    private TrackedEventMessage trackedEventMessage(String payload, TrackingToken token) {
        return new GenericTrackedEventMessage(token, EventTestUtils.asEventMessage(payload));
    }
}