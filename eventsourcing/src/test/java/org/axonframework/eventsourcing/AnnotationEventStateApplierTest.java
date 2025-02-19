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
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationEventStateApplier}.
 *
 * @author Mateusz Nowak
 */
class AnnotationEventStateApplierTest {

    private static final EventStateApplier<TestState> eventStateApplier = new AnnotationEventStateApplier<>(TestState.class);

    @Nested
    class BasicEventHandling {

        // todo: should support records and returning new state from event handlers in the future
        @Test
        void mutatesStateIfClass() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            eventStateApplier.apply(state, event);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSimpleEvents() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // given
            var state = new TestState();

            // when
            state = eventStateApplier.apply(state, domainEvent(0));
            state = eventStateApplier.apply(state, domainEvent(1));
            state = eventStateApplier.apply(state, domainEvent(2));

            // then
            assertEquals("null-0-1-2", state.handledPayloads);
            assertEquals(3, state.handledCount);
        }
    }

    @Nested
    class ParameterResolution {

        @Test
        void resolvesMetadata() {
            // given
            var state = new TestState();
            var event = domainEvent(0, "sampleValue");

            // when
            var result = eventStateApplier.apply(state, event);

            // then
            assertEquals("null-0", result.handledPayloads);
            assertEquals("null-sampleValue", result.handledMetadata);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void throwsEventApplicationExceptionOnError() {
            // given
            var eventStateApplier = new AnnotationEventStateApplier<>(ErrorThrowingState.class);
            var state = new ErrorThrowingState();
            var event = domainEvent(0);

            // when/then
            var exception = assertThrows(EventApplicationException.class,
                                         () -> eventStateApplier.apply(state, event));
            assertTrue(exception.getMessage().contains("Failed to apply event [java.lang.Integer]"));
        }

        @Test
        void rejectsNullModel() {
            // given
            var event = domainEvent(0);

            // when/then
            assertThrows(NullPointerException.class,
                         () -> eventStateApplier.apply(null, event),
                         "Model may not be null");
        }

        @Test
        void rejectsNullEvent() {
            // given
            var state = new TestState();

            // when/then
            assertThrows(NullPointerException.class,
                         () -> eventStateApplier.apply(state, null),
                         "Event Message may not be null");
        }
    }

    private static DomainEventMessage<?> domainEvent(int seq) {
        return domainEvent(seq, null);
    }

    private static DomainEventMessage<?> domainEvent(int seq, String sampleMetaData) {
        return new GenericDomainEventMessage<>(
                "test",
                "id",
                seq,
                new MessageType("event"),
                seq, sampleMetaData == null ? MetaData.emptyInstance() : MetaData.with("sampleKey", sampleMetaData)
        );
    }

    private static class TestState {

        private String handledPayloads = "null";
        private String handledMetadata = "null";
        private int handledCount = 0;

        @EventSourcingHandler
        public void handlePayload(Number payload) {
            this.handledPayloads = handledMetadata + "-" + payload;
            this.handledCount++;
        }

        @EventSourcingHandler
        public void handlePayloadWithMetadata(Number payload, @MetaDataValue("sampleKey") String metadata) {
            this.handledPayloads = handledPayloads + "-" + payload;
            this.handledMetadata = handledMetadata + "-" + metadata;
            this.handledCount++;
        }

        @EventSourcingHandler
        public void handleMessage(Message<Number> message) {
            var payload = message.getPayload();
            var metadata = message.getMetaData().get("sampleKey");
            this.handledPayloads = handledPayloads + "-" + payload;
            this.handledMetadata = handledMetadata + "-" + metadata;
            this.handledCount++;
        }
    }

    private static class ErrorThrowingState {

        @EventSourcingHandler
        public void handle(Number event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }
}