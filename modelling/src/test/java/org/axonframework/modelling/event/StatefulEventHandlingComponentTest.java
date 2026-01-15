/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.event;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the functionality of the {@link SimpleEventHandlingComponent} with command handlers that use
 * {@link StateManager}.
 */
class StatefulEventHandlingComponentTest {

    private final StateManager stateManager = SimpleStateManager
            .named("test")
            .register(String.class, Integer.class,
                      (id, ctx) -> CompletableFuture.completedFuture(Integer.parseInt(id)),
                      (id, entity, context) -> CompletableFuture.completedFuture(null));

    @Nested
    class StatefulEventHandlerTests {

        @Test
        void invokedRegisteredStatefulHandler() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();
            AtomicBoolean invoked = new AtomicBoolean();

            testSubject.subscribe(new QualifiedName("test-event"), (event, ctx) -> {
                var state = ctx.component(StateManager.class);
                state.loadEntity(Integer.class, "42", ctx).thenAccept(result -> {
                    assertEquals(42, result);
                }).join();
                invoked.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage event = new GenericEventMessage(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, messageProcessingContext(event)).asCompletableFuture().join();

            // then
            assertTrue(invoked.get());
        }

        @Test
        void canRegisterNonStatefulNormalHandler() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();
            AtomicBoolean invoked = new AtomicBoolean();

            testSubject.subscribe(new QualifiedName("test-event"), (event, ctx) -> {
                invoked.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage event = new GenericEventMessage(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, messageProcessingContext(event)).asCompletableFuture().join();

            // then
            assertTrue(invoked.get());
        }

        @Test
        void exceptionWhileHandlingEventResultsInFailedStream() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();
            testSubject.subscribe(new QualifiedName("test-event"), (event, ctx) -> {
                throw new RuntimeException("Faking an exception");
            });

            // when / then
            var exception = assertThrows(RuntimeException.class, () -> {
                GenericEventMessage event = new GenericEventMessage(new MessageType("test-event"),
                                                                              "my-payload");
                testSubject.handle(event, messageProcessingContext(event))
                           .asCompletableFuture()
                           .join();
            });

            assertEquals("Faking an exception", exception.getMessage());
        }
    }

    @Nested
    class SupportedEventsTests {

        @Test
        void registeredHandlersAreListedInSupportedEvents() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();

            testSubject.subscribe(new QualifiedName("test-event"),
                                  (event, ctx) -> MessageStream.empty().cast());
            testSubject.subscribe(new QualifiedName("test-event-2"),
                                  (event, ctx) -> MessageStream.empty().cast());

            // when
            Set<QualifiedName> supportedEvents = testSubject.supportedEvents();

            // then
            assertEquals(2, supportedEvents.size());
            assertTrue(supportedEvents.contains(new QualifiedName("test-event")));
            assertTrue(supportedEvents.contains(new QualifiedName("test-event-2")));
        }

        @Test
        void emptySupportedEventsWhenNoHandlersRegistered() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();

            // when
            Set<QualifiedName> supportedEvents = testSubject.supportedEvents();

            // then
            assertTrue(supportedEvents.isEmpty());
        }
    }

    @Nested
    class StateManagerIntegrationTests {

        @Test
        void stateManagerIsProvidedToStatefulHandler() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();
            AtomicBoolean stateManagerProvided = new AtomicBoolean();

            testSubject.subscribe(new QualifiedName("test-event"), (event, ctx) -> {
                var state = ctx.component(StateManager.class);
                assertSame(stateManager, state);
                stateManagerProvided.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage event = new GenericEventMessage(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, messageProcessingContext(event)).asCompletableFuture().join();

            // then
            assertTrue(stateManagerProvided.get());
        }

        @Test
        void canLoadEntityThroughStateManager() {
            // given
            EventHandlingComponent testSubject = new SimpleEventHandlingComponent();
            AtomicBoolean entityLoaded = new AtomicBoolean();

            testSubject.subscribe(new QualifiedName("test-event"), (event, ctx) -> {
                var state = ctx.component(StateManager.class);
                Integer loadedEntity = state.loadEntity(Integer.class, "123", ctx).join();
                assertEquals(123, loadedEntity);
                entityLoaded.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage event = new GenericEventMessage(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, messageProcessingContext(event)).asCompletableFuture().join();

            // then
            assertTrue(entityLoaded.get());
        }
    }

    @Nonnull
    private ProcessingContext messageProcessingContext(GenericEventMessage event) {
        return StubProcessingContext.withComponent(StateManager.class, stateManager).withMessage(event);
    }
}