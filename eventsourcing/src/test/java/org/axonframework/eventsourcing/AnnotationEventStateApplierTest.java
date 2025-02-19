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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationEventStateApplier}.
 *
 * @author Mateusz Nowak
 */
class AnnotationEventStateApplierTest {

    private AnnotationEventStateApplier<AggregateState> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationEventStateApplier<>(AggregateState.class);
    }

    @Nested
    @DisplayName("Basic Event Handling")
    class BasicEventHandling {

        @Test
        void handlesSimpleEvents() {
            // given
            AggregateState state = new AggregateState();

            // when
            AggregateState result = testSubject.apply(state, domainEvent(0));

            // then
            assertEquals("null-0", result.getState());
        }

        @Test
        void handlesSequenceOfEvents() {
            // given
            AggregateState state = new AggregateState();

            // when
            state = testSubject.apply(state, domainEvent(0));
            state = testSubject.apply(state, domainEvent(1));
            state = testSubject.apply(state, domainEvent(2));

            // then
            assertEquals("null-0-1-2", state.getState());
            assertEquals(3, state.getHandledCount());
        }
    }

    @Nested
    @DisplayName("Parameter Resolution")
    class ParameterResolution {

        @Test
        void handlesEventWithMetaData() {
            // given
            AggregateState state = new AggregateState();
            DomainEventMessage<?> event = new GenericDomainEventMessage<>(
                    "test", "id", 0,
                    new MessageType("event"), 0,
                    MetaData.with("version", "1.0").and("source", "test")
            );

            // when
            AggregateState result = testSubject.apply(state, event);

            // then
            assertEquals("null-0-v1.0", result.getState());
            assertEquals("test", result.getLastSource());
        }

        @Test
        void handlesEventWithMessage() {
            // given
            AggregateState state = new AggregateState();
            DomainEventMessage<?> event = new GenericDomainEventMessage<>(
                    "test", "id", 0,
                    new MessageType("event"), 0,
                    MetaData.with("key", "value")
            );

            // when
            AggregateState result = testSubject.apply(state, event);

            // then
            assertNotNull(result.getLastMessage());
            assertEquals(event.getIdentifier(), result.getLastMessage().getIdentifier());
            assertEquals("null-0-value", result.getState());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void throwsEventApplicationExceptionOnError() {
            // given
            var stateApplier = new AnnotationEventStateApplier<>(ErrorThrowingState.class);
            ErrorThrowingState state = new ErrorThrowingState();
            EventMessage<?> event = domainEvent(0);

            // when/then
            EventApplicationException exception = assertThrows(EventApplicationException.class,
                                                               () -> stateApplier.apply(state, event));
            assertTrue(exception.getMessage().contains("Number"));
        }

        @Test
        void rejectsNullModel() {
            // given
            EventMessage<?> event = domainEvent(0);

            // when/then
            assertThrows(NullPointerException.class,
                         () -> testSubject.apply(null, event),
                         "Model may not be null");
        }

        @Test
        void rejectsNullEvent() {
            // given
            AggregateState state = new AggregateState();

            // when/then
            assertThrows(NullPointerException.class,
                         () -> testSubject.apply(state, null),
                         "Event Message may not be null");
        }
    }

    private static DomainEventMessage<?> domainEvent(int seq) {
        return new GenericDomainEventMessage<>("test", "id", seq, new MessageType("event"), seq);
    }

    private static class AggregateState {

        private String state = "null";
        private int handledCount = 0;
        private String lastSource;
        private Message<?> lastMessage;

        @EventSourcingHandler
        public void handleNumber(Number event) {
            this.state = state + "-" + event;
            this.handledCount++;
        }

        @EventSourcingHandler
        public void handleWithMetaData(Number event,
                                       @MetaDataValue("version") String version,
                                       @MetaDataValue("source") String source) {
            this.state = state + "-" + event + "-v" + version;
            this.lastSource = source;
            this.handledCount++;
        }

        @EventSourcingHandler
        public void handleWithMessage(Number event, Message<?> message, @MetaDataValue("key") String key) {
            this.state = state + "-" + event + "-" + key;
            this.lastMessage = message;
            this.handledCount++;
        }

        public String getState() {
            return state;
        }

        public int getHandledCount() {
            return handledCount;
        }

        public String getLastSource() {
            return lastSource;
        }

        public Message<?> getLastMessage() {
            return lastMessage;
        }
    }

    private static class ErrorThrowingState {

        @EventSourcingHandler
        public void handle(Number event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }
}