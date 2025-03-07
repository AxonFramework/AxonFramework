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

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SimpleEventSourcingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleEventSourcingComponentTest {

    private SimpleEventSourcingComponent eventSourcingComponent;
    private final ProcessingContext processingContext = ProcessingContext.NONE;

    @BeforeEach
    void beforeEach() {
        eventSourcingComponent = new SimpleEventSourcingComponent();
    }

    @Test
    void supportedEvents() {
        // given
        IEventSourcingHandler stringHandler = (event, ctx) -> just("string-sourced_" + event.getPayload());
        IEventSourcingHandler integerHandler = (event, ctx) -> just("int-sourced_" + event.getPayload());
        eventSourcingComponent.subscribe(
                new QualifiedName(Integer.class), stringHandler);
        eventSourcingComponent.subscribe(
                new QualifiedName(String.class), integerHandler);

        // when
        Set<QualifiedName> actual = eventSourcingComponent.supportedEvents();

        // then
        Set<QualifiedName> expected = new HashSet<>(Set.of(
                new QualifiedName(Integer.class),
                new QualifiedName(String.class)
        ));
        assertEquals(expected, actual);
    }

    @Nested
    class BasicEventHandling {

        @Test
        void returnsFailedStreamWhenNoHandlerRegistered() {
            // given
            var event = aMessage(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertTrue(result.error().isPresent());
            var exception = result.error().get();
            assertInstanceOf(IllegalArgumentException.class, exception);
            assertTrue(exception.getMessage().contains("No handler found for event"));
        }

        @Test
        void handlesEventWithRegisteredHandler() {
            // given
            var event = aMessage(0);
            var handledCount = new AtomicInteger(0);

            IEventSourcingHandler handler = (e, ctx) -> {
                handledCount.incrementAndGet();
                return just("Handled");
            };

            eventSourcingComponent.subscribe(event.type().qualifiedName(), handler);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals(1, handledCount.get());
            assertEquals("Handled", sourcedState(result));
        }

        @Test
        void handlesSequenceOfEvents() {
            // given
            AtomicReference<String> state = new AtomicReference<>("initial");

            IEventSourcingHandler handler = (event, ctx) -> {
                String newState = state.get() + "-" + event.getPayload();
                state.set(newState);
                return just(newState);
            };

            eventSourcingComponent.subscribe(new QualifiedName(Integer.class), handler);

            // when
            var result1 = eventSourcingComponent.source(aMessage(0), processingContext);
            var result2 = eventSourcingComponent.source(aMessage(1), processingContext);
            var result3 = eventSourcingComponent.source(aMessage(2), processingContext);

            // then
            assertSuccessfulStream(result1);
            assertSuccessfulStream(result2);
            assertSuccessfulStream(result3);
            assertEquals("initial-0-1-2", state.get());
            assertEquals("initial-0-1-2", sourcedState(result3));
        }

        @Test
        void supportsSubscribingMultipleHandlers() {
            // given
            var qualifiedName = new QualifiedName(Integer.class);
            AtomicInteger firstCounter = new AtomicInteger(0);
            AtomicInteger secondCounter = new AtomicInteger(0);

            // first handler subscription
            IEventSourcingHandler firstHandler = (event, ctx) -> {
                firstCounter.incrementAndGet();
                return just("first");
            };
            eventSourcingComponent.subscribe(qualifiedName, firstHandler);

            // second handler subscription (replaces the first)
            IEventSourcingHandler secondHandler = (event, ctx) -> {
                secondCounter.incrementAndGet();
                return just("second");
            };
            eventSourcingComponent.subscribe(qualifiedName, secondHandler);

            var event = aMessage(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals(0, firstCounter.get());
            assertEquals(1, secondCounter.get());
            assertEquals("second", sourcedState(result));
        }

        private <T> T sourcedState(MessageStream.Single<? extends Message<?>> stream) {
            //noinspection unchecked
            return (T) stream.asCompletableFuture().join().message().getPayload();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void returnsFailedMessageStreamIfExceptionThrownInsideEventHandler() {
            // given
            IEventSourcingHandler errorThrowingHandler = (event, ctx) -> {
                throw new RuntimeException("Simulated error for event: " + event.getPayload());
            };

            eventSourcingComponent.subscribe(new QualifiedName(Integer.class), errorThrowingHandler);
            var event = aMessage(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertTrue(result.error().isPresent());
            var exception = result.error().get();
            assertInstanceOf(RuntimeException.class, exception);
            assertEquals("Simulated error for event: 0", exception.getMessage());
        }

        @Test
        void rejectsNullEvent() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventSourcingComponent.source(null, processingContext),
                         "EventMessage may not be null");
        }

        @Test
        void rejectsNullProcessingContext() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventSourcingComponent.source(aMessage(0), null),
                         "Processing Context may not be null");
        }
    }

    @Nested
    class SubscriptionHandling {

        @Test
        void convertsEventHandlerToEventSourcingHandler() {
            // given
            var handledCount = new AtomicInteger(0);

            EventHandler notEventSourcingHandler = (event, ctx) -> {
                handledCount.incrementAndGet();
                return MessageStream.empty();
            };

            eventSourcingComponent.subscribe(new QualifiedName(Integer.class), notEventSourcingHandler);
            var event = aMessage(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertInstanceOf(MessageStream.Empty.class, result);
            assertEquals(1, handledCount.get());
        }

        @Test
        void supportsSubscribingWithMultipleEventNames() {
            // given
            var handledCount = new AtomicInteger(0);

            IEventSourcingHandler handler = (event, ctx) -> {
                handledCount.incrementAndGet();
                return just("handled-" + event.getPayload());
            };

            eventSourcingComponent.subscribe(Set.of(new QualifiedName(Integer.class), new QualifiedName(String.class)),
                                             handler);

            var intEvent = aMessage(42);
            var stringEvent = aMessage("42");

            // when
            var intResult = eventSourcingComponent.source(intEvent, processingContext);
            var stringResult = eventSourcingComponent.source(stringEvent, processingContext);

            // then
            assertSuccessfulStream(intResult);
            assertSuccessfulStream(stringResult);
            assertEquals(2, handledCount.get());
            assertEquals("handled-42", sourcedState(intResult));
            assertEquals("handled-42", sourcedState(stringResult));
        }

        private <T> T sourcedState(MessageStream.Single<? extends Message<?>> stream) {
            return (T) stream.asCompletableFuture().join().message().getPayload();
        }
    }

    private static void assertSuccessfulStream(MessageStream<?> result) {
        assertTrue(result.error().isEmpty(),
                   "Expected successful stream but got error: " +
                           (result.error().isPresent() ? result.error().get().getMessage() : ""));
    }

    private static <T> EventMessage<T> aMessage(T payload) {
        return new GenericEventMessage<>(
                new MessageType(new QualifiedName(payload.getClass())),
                payload
        );
    }

    private static <T> MessageStream.Single<Message<T>> just(T payload) {
        return MessageStream.just(aMessage(payload));
    }
}