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

package org.axonframework.modelling.query;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SimpleQueryHandlingComponent;
import org.junit.jupiter.api.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the functionality of the {@link SimpleQueryHandlingComponent} with command handlers that use
 * {@link StateManager}.
 *
 * @author Steven van Beelen
 */
class StatefulQueryHandlingComponentTest {

    private static final QualifiedName QUERY_NAME = new QualifiedName("test-query");
    private static final MessageType QUERY_TYPE = new MessageType(QUERY_NAME);
    private static final String QUERY_PAYLOAD = "my-payload";

    private final StateManager stateManager = SimpleStateManager
            .named("test")
            .register(String.class, Integer.class,
                      (id, ctx) -> CompletableFuture.completedFuture(Integer.parseInt(id)),
                      (id, entity, context) -> CompletableFuture.completedFuture(null));

    @Nested
    class StatefulQueryHandlerTests {

        @Test
        void invokedRegisteredStatefulHandler() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qch");
            AtomicBoolean invoked = new AtomicBoolean();

            testSubject.subscribe(QUERY_NAME, (query, ctx) -> {
                var state = ctx.component(StateManager.class);
                state.loadEntity(Integer.class, "42", ctx).thenAccept(result ->
                                                                              assertEquals(42, result))
                     .join();
                invoked.set(true);
                return MessageStream.empty().cast();
            });

            // when
            QueryMessage query = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD);
            testSubject.handle(query, messageProcessingContext(query)).first().asCompletableFuture().join();

            // then
            assertTrue(invoked.get());
        }

        @Test
        void canRegisterNonStatefulNormalHandler() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qch");
            AtomicBoolean invoked = new AtomicBoolean();

            testSubject.subscribe(QUERY_NAME, (query, ctx) -> {
                invoked.set(true);
                return MessageStream.empty().cast();
            });

            // when
            QueryMessage query = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD);
            testSubject.handle(query, messageProcessingContext(query)).first().asCompletableFuture().join();

            // then
            assertTrue(invoked.get());
        }

        @Test
        void exceptionWhileHandlingQueryResultsInFailedStream() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qch");
            testSubject.subscribe(QUERY_NAME, (query, ctx) -> {
                throw new RuntimeException("Faking an exception");
            });

            // when / then
            var exception = assertThrows(RuntimeException.class, () -> {
                QueryMessage query = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD);
                testSubject.handle(query, messageProcessingContext(query))
                           .first()
                           .asCompletableFuture()
                           .join();
            });

            assertEquals("Faking an exception", exception.getCause().getMessage());
        }
    }

    @Nested
    class SupportedQueriesTests {

        @Test
        void registeredHandlersAreListedInSupportedQueries() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qch");
            QualifiedName otherQueryName = new QualifiedName("test-query-2");

            testSubject.subscribe(QUERY_NAME,
                                  (query, ctx) -> MessageStream.empty().cast());
            testSubject.subscribe(otherQueryName,
                                  (query, ctx) -> MessageStream.empty().cast());

            // when
            Set<QualifiedName> supportedQueries = testSubject.supportedQueries();

            // then
            assertEquals(2, supportedQueries.size());
            assertTrue(supportedQueries.contains(QUERY_NAME));
            assertTrue(supportedQueries.contains(otherQueryName));
        }

        @Test
        void emptySupportedQuerysWhenNoHandlersRegistered() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qch");

            // when
            Set<QualifiedName> supportedQueries = testSubject.supportedQueries();

            // then
            assertTrue(supportedQueries.isEmpty());
        }
    }

    @Nested
    class StateManagerIntegrationTests {

        @Test
        void stateManagerIsProvidedToStatefulHandler() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qhc");
            AtomicBoolean stateManagerProvided = new AtomicBoolean();

            testSubject.subscribe(QUERY_NAME, (quey, ctx) -> {
                var state = ctx.component(StateManager.class);
                assertSame(stateManager, state);
                stateManagerProvided.set(true);
                return MessageStream.empty().cast();
            });

            // when
            QueryMessage query = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD);
            testSubject.handle(query, messageProcessingContext(query)).first().asCompletableFuture().join();

            // then
            assertTrue(stateManagerProvided.get());
        }

        @Test
        void canLoadEntityThroughStateManager() {
            // given
            SimpleQueryHandlingComponent testSubject = SimpleQueryHandlingComponent.create("qch");
            AtomicBoolean entityLoaded = new AtomicBoolean();

            testSubject.subscribe(QUERY_NAME, (query, ctx) -> {
                var state = ctx.component(StateManager.class);
                Integer loadedEntity = state.loadEntity(Integer.class, "123", ctx).join();
                assertEquals(123, loadedEntity);
                entityLoaded.set(true);
                return MessageStream.empty().cast();
            });

            // when
            QueryMessage query = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD);
            testSubject.handle(query, messageProcessingContext(query)).first().asCompletableFuture().join();

            // then
            assertTrue(entityLoaded.get());
        }
    }

    @Nonnull
    private ProcessingContext messageProcessingContext(QueryMessage query) {
        return StubProcessingContext.withComponent(StateManager.class, stateManager).withMessage(query);
    }
}
