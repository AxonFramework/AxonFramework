/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DistributedCommandBus}.
 *
 * @author Allard Buijze
 */
class DistributedCommandBusTest {

    private DistributedCommandBus testSubject;

    private StubConnector connector;
    private CommandMessage<?> commandMessage;
    private SimpleCommandBus delegate;

    @BeforeEach
    void setUp() {
        commandMessage = new GenericCommandMessage<>("test");
        connector = new StubConnector();
        delegate = new SimpleCommandBus();
        testSubject = new DistributedCommandBus(delegate, connector);
    }

    @Test
    void publishedCommandsAreSentToConnector() {
        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(commandMessage,
                                                                              null);

        assertSame(result, connector.getDispatchedCommands().get(commandMessage));
        // the connector doesn't actually dispatch commands, so we expect the CompletableFuture to remain unfinished
        assertFalse(result.isDone());
    }

    @Test
    void incomingCommandsAreRejectedWhenNoHandlerRegistered() {
        Connector.ResultCallback mockCallback = mock();
        connector.handler.get().accept(commandMessage, mockCallback);

        verify(mockCallback).error(isA(NoHandlerForCommandException.class));
    }

    @Test
    void incomingCommandsAreDelegatedToSubscribedHandlers() {
        GenericMessage<String> okMessage = new GenericMessage<>("OK");
        testSubject.subscribe(String.class.getName(), new MessageHandler<>() {
            @Override
            public Object handleSync(CommandMessage<?> message) {
                return "OK";
            }

            @Override
            public MessageStream<? extends Message<?>> handle(CommandMessage<?> message,
                                                              ProcessingContext processingContext) {
                return MessageStream.just(okMessage);
            }
        });
        Connector.ResultCallback mockCallback = mock();
        connector.handler.get().accept(commandMessage, mockCallback);

        verify(mockCallback).success(same(okMessage));
    }

    @Test
    void incomingCommandsAreRejectedForCancelledHandlerSubscription() {
        GenericMessage<String> okMessage = new GenericMessage<>("OK");
        Registration registration = testSubject.subscribe(String.class.getName(),
                                                          new MessageHandler<>() {
                                                              @Override
                                                              public Object handleSync(
                                                                      CommandMessage<?> message) {
                                                                  return "OK";
                                                              }

                                                              @Override
                                                              public MessageStream<? extends Message<?>> handle(
                                                                      CommandMessage<?> message,
                                                                      ProcessingContext processingContext) {
                                                                  return MessageStream.just(
                                                                          okMessage);
                                                              }
                                                          });

        assertTrue(registration.cancel());
        Connector.ResultCallback mockCallback = mock();
        connector.handler.get().accept(commandMessage, mockCallback);

        verify(mockCallback).error(isA(NoHandlerForCommandException.class));
    }

    @Test
    void unregisterNonExistentCommandHandlerReturnsFalse() {
        Registration registration = testSubject.subscribe(String.class.getName(), mock());
        assertTrue(registration.cancel());
        assertFalse(registration.cancel());
    }

    @Test
    void describeToMentionsConnector() {
        ComponentDescriptor mock = mock();
        testSubject.describeTo(mock);

        verify(mock).describeWrapperOf(delegate);
        verify(mock).describeProperty("connector", connector);
    }

    private static class StubConnector implements Connector {

        private final Map<CommandMessage<?>, CompletableFuture<?>> dispatchedCommands = new ConcurrentHashMap<>();
        private final Map<String, Integer> subscriptions = new ConcurrentHashMap<>();
        private final AtomicReference<BiConsumer<CommandMessage<?>, ResultCallback>> handler = new AtomicReference<>();


        @Override
        public CompletableFuture<CommandResultMessage<?>> dispatch(CommandMessage<?> command,
                                                                   ProcessingContext processingContext) {
            CompletableFuture<CommandResultMessage<?>> future = new CompletableFuture<>();
            dispatchedCommands.put(command, future);
            return future;
        }

        @Override
        public void subscribe(String commandName, int loadFactor) {
            subscriptions.put(commandName, loadFactor);
        }

        @Override
        public boolean unsubscribe(String commandName) {
            return subscriptions.remove(commandName) != null;
        }

        @Override
        public void onIncomingCommand(BiConsumer<CommandMessage<?>, ResultCallback> handler) {
            this.handler.set(handler);
        }

        public Map<CommandMessage<?>, CompletableFuture<?>> getDispatchedCommands() {
            return dispatchedCommands;
        }

        public Map<String, Integer> getSubscriptions() {
            return subscriptions;
        }

        public BiConsumer<CommandMessage<?>, ResultCallback> getHandler() {
            return handler.get();
        }
    }
}
