/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.config;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.queryhandling.QueryHandler;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the correct registration of command, query and overall message handling beans, through the
 * {@link Configurer#registerCommandHandler(Function)}, {@link Configurer#registerQueryHandler(Function)} and {@link
 * Configurer#registerMessageHandler(Function)} methods.
 *
 * @author Steven van Beelen
 */
class DefaultConfigurerHandlerRegistrationTest {

    private static final String COMMAND_HANDLING_RESPONSE = "some-command-handling-response";
    private static final String QUERY_HANDLING_RESPONSE = "some-query-handling-response";

    private Configurer baseConfigurer;

    @BeforeEach
    void setUp() {
        baseConfigurer = DefaultConfigurer.defaultConfiguration()
                                          .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine());
        // Set to SEP to simplify event handler registration without an actual EventStore.
        baseConfigurer.eventProcessing().usingSubscribingEventProcessors();
    }

    @Test
    void registerCommandHandler() {
        AtomicBoolean handled = new AtomicBoolean(false);

        Configuration config = baseConfigurer.registerCommandHandler(c -> new CommandHandlingComponent(handled))
                                             .start();

        CompletableFuture<String> result = config.commandGateway().send(new SomeCommand());

        assertEquals(COMMAND_HANDLING_RESPONSE, result.join());
        assertTrue(handled.get());
    }

    @Test
    void registerQueryHandler() {
        AtomicBoolean handled = new AtomicBoolean(false);

        Configuration config = baseConfigurer.registerQueryHandler(c -> new QueryHandlingComponent(handled))
                                             .start();

        CompletableFuture<String> result = config.queryGateway().query(new SomeQuery(), String.class);

        assertEquals(QUERY_HANDLING_RESPONSE, result.join());
        assertTrue(handled.get());
    }

    @Test
    void registerMessageHandler() {
        AtomicReference<MessageHandlingComponent> commandHandled = new AtomicReference<>();
        AtomicReference<MessageHandlingComponent> eventHandled = new AtomicReference<>();
        AtomicReference<MessageHandlingComponent> queryHandled = new AtomicReference<>();

        Configuration config = baseConfigurer.registerMessageHandler(
                c -> new MessageHandlingComponent(commandHandled, eventHandled, queryHandled)
        ).start();

        CompletableFuture<String> commandHandlingResult = config.commandGateway().send(new SomeCommand());
        config.eventGateway().publish(new SomeEvent());
        CompletableFuture<String> queryHandling = config.queryGateway().query(new SomeQuery(), String.class);

        assertEquals(COMMAND_HANDLING_RESPONSE, commandHandlingResult.join());
        assertNotNull(commandHandled.get());
        assertNotNull(eventHandled.get());
        assertEquals(QUERY_HANDLING_RESPONSE, queryHandling.join());
        assertNotNull(queryHandled.get());

        assertSame(queryHandled.get(), eventHandled.get());
        assertSame(queryHandled.get(), commandHandled.get());
    }

    private static class SomeCommand {

    }

    private static class SomeEvent {

    }

    private static class SomeQuery {

    }

    @SuppressWarnings("unused")
    private static class CommandHandlingComponent {

        private final AtomicBoolean handled;

        private CommandHandlingComponent(AtomicBoolean handled) {
            this.handled = handled;
        }

        @CommandHandler
        public String handle(SomeCommand command) {
            handled.set(true);
            return COMMAND_HANDLING_RESPONSE;
        }
    }

    @SuppressWarnings("unused")
    private static class QueryHandlingComponent {

        private final AtomicBoolean handled;

        private QueryHandlingComponent(AtomicBoolean handled) {
            this.handled = handled;
        }

        @QueryHandler
        public String handle(SomeQuery query) {
            handled.set(true);
            return QUERY_HANDLING_RESPONSE;
        }
    }

    @SuppressWarnings("unused")
    private static class MessageHandlingComponent {

        private final AtomicReference<MessageHandlingComponent> handledCommand;
        private final AtomicReference<MessageHandlingComponent> handledEvent;
        private final AtomicReference<MessageHandlingComponent> handledQuery;

        private MessageHandlingComponent(AtomicReference<MessageHandlingComponent> handledCommand,
                                         AtomicReference<MessageHandlingComponent> handledEvent,
                                         AtomicReference<MessageHandlingComponent> handledQuery) {
            this.handledCommand = handledCommand;
            this.handledEvent = handledEvent;
            this.handledQuery = handledQuery;
        }

        @CommandHandler
        public String handle(SomeCommand command) {
            handledCommand.set(this);
            return COMMAND_HANDLING_RESPONSE;
        }

        @EventHandler
        public void on(SomeEvent event) {
            handledEvent.set(this);
        }

        @QueryHandler
        public String handle(SomeQuery query) {
            handledQuery.set(this);
            return QUERY_HANDLING_RESPONSE;
        }
    }
}
