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

package org.axonframework.modelling.event;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

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
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);
            AtomicBoolean invoked = new AtomicBoolean();
            
            testSubject.subscribe(new QualifiedName("test-event"), (event, state, ctx) -> {
                state.loadEntity(Integer.class, "42", ctx).thenAccept(result -> {
                    assertEquals(42, result);
                }).join();
                invoked.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage<String> event = new GenericEventMessage<>(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, StubProcessingContext.forMessage(event)).asCompletableFuture().join();

            // then
            assertTrue(invoked.get());
        }

        @Test
        void canRegisterNonStatefulNormalHandler() {
            // given
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);
            AtomicBoolean invoked = new AtomicBoolean();
            
            testSubject.subscribe(new QualifiedName("test-event"), (event, ctx) -> {
                invoked.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage<String> event = new GenericEventMessage<>(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, StubProcessingContext.forMessage(event)).asCompletableFuture().join();

            // then
            assertTrue(invoked.get());
        }

        @Test
        void exceptionWhileHandlingEventResultsInFailedStream() {
            // given
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);
            testSubject.subscribe(new QualifiedName("test-event"), (event, state, ctx) -> {
                throw new RuntimeException("Faking an exception");
            });

            // when / then
            CompletionException exception = assertThrows(CompletionException.class, () -> {
                GenericEventMessage<String> event = new GenericEventMessage<>(new MessageType("test-event"), "my-payload");
                testSubject.handle(event, StubProcessingContext.forMessage(event))
                           .asCompletableFuture()
                           .join();
            });

            assertInstanceOf(RuntimeException.class, exception.getCause());
            assertEquals("Faking an exception", exception.getCause().getMessage());
        }
    }

    @Nested
    class SupportedEventsTests {

        @Test
        void registeredHandlersAreListedInSupportedEvents() {
            // given
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);
            
            testSubject.subscribe(new QualifiedName("test-event"),
                                  (event, state, ctx) -> MessageStream.empty().cast());
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
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);

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
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);
            AtomicBoolean stateManagerProvided = new AtomicBoolean();
            
            testSubject.subscribe(new QualifiedName("test-event"), (event, state, ctx) -> {
                assertSame(stateManager, state);
                stateManagerProvided.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage<String> event = new GenericEventMessage<>(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, StubProcessingContext.forMessage(event)).asCompletableFuture().join();

            // then
            assertTrue(stateManagerProvided.get());
        }

        @Test
        void canLoadEntityThroughStateManager() {
            // given
            StatefulEventHandlingComponent testSubject = StatefulEventHandlingComponent.create("test", stateManager);
            AtomicBoolean entityLoaded = new AtomicBoolean();
            
            testSubject.subscribe(new QualifiedName("test-event"), (event, state, ctx) -> {
                Integer loadedEntity = state.loadEntity(Integer.class, "123", ctx).join();
                assertEquals(123, loadedEntity);
                entityLoaded.set(true);
                return MessageStream.empty().cast();
            });

            // when
            GenericEventMessage<String> event = new GenericEventMessage<>(new MessageType("test-event"), "my-payload");
            testSubject.handle(event, StubProcessingContext.forMessage(event)).asCompletableFuture().join();

            // then
            assertTrue(entityLoaded.get());
        }
    }

    @Nested
    class ComponentCreationTests {

        @Test
        void canCreateComponentWithNameAndStateManager() {
            // when
            StatefulEventHandlingComponent component = StatefulEventHandlingComponent.create("test-component", stateManager);

            // then
            assertNotNull(component);
        }

        @Test
        void throwsExceptionWhenNameIsNull() {
            // when / then
            assertThrows(AxonConfigurationException.class, () -> {
                StatefulEventHandlingComponent.create(null, stateManager);
            });
        }

        @Test
        void throwsExceptionWhenNameIsEmpty() {
            // when / then
            assertThrows(AxonConfigurationException.class, () -> {
                StatefulEventHandlingComponent.create("", stateManager);
            });
        }

        @Test
        void throwsExceptionWhenStateManagerIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () -> {
                StatefulEventHandlingComponent.create("test", null);
            });
        }
    }
}