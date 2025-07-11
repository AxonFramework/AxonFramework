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

package org.axonframework.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
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

    private CommandMessage<?> testCommand;

    private StubConnector connector;
    private SimpleCommandBus delegate;
    private DistributedCommandBus testSubject;

    @BeforeEach
    void setUp() {
        testCommand = new GenericCommandMessage<>(new MessageType("command"), "test");

        connector = new StubConnector();
        delegate = new SimpleCommandBus();
        testSubject = new DistributedCommandBus(delegate, connector, new DistributedCommandBusConfiguration());
    }

    @Test
    void publishedCommandsAreSentToConnector() {
        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand, null);

        assertSame(result, connector.getDispatchedCommands().get(testCommand));
        // the connector doesn't actually dispatch commands, so we expect the CompletableFuture to remain unfinished
        assertFalse(result.isDone());
    }

    @Test
    void incomingCommandsAreRejectedWhenNoHandlerRegistered() {
        CommandBusConnector.ResultCallback mockCallback = mock();
        connector.handler.get().handle(testCommand, mockCallback);

        verify(mockCallback).error(isA(NoHandlerForCommandException.class));
    }

    @Test
    void incomingCommandsAreDelegatedToSubscribedHandlers() {
        GenericCommandResultMessage<String> resultMessage =
                new GenericCommandResultMessage<>(new MessageType("result"), "OK");
        testSubject.subscribe(testCommand.type().qualifiedName(),
                              (command, context) -> MessageStream.just(resultMessage));

        CommandBusConnector.ResultCallback mockCallback = mock();
        connector.handler.get().handle(testCommand, mockCallback);

        verify(mockCallback).success(same(resultMessage));
    }

    @Test
    void describeToMentionsConnector() {
        ComponentDescriptor mock = mock();
        testSubject.describeTo(mock);

        verify(mock).describeWrapperOf(delegate);
        verify(mock).describeProperty("connector", connector);
    }

    private static class StubConnector implements CommandBusConnector {

        private final Map<CommandMessage<?>, CompletableFuture<?>> dispatchedCommands = new ConcurrentHashMap<>();
        private final Map<String, Integer> subscriptions = new ConcurrentHashMap<>();
        private final AtomicReference<Handler> handler = new AtomicReference<>();


        @Nonnull
        @Override
        public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                                   @Nullable ProcessingContext processingContext) {
            CompletableFuture<CommandResultMessage<?>> future = new CompletableFuture<>();
            dispatchedCommands.put(command, future);
            return future;
        }

        @Override
        public void subscribe(@Nonnull String commandName, int loadFactor) {
            subscriptions.put(commandName, loadFactor);
        }

        @Override
        public boolean unsubscribe(@Nonnull String commandName) {
            return subscriptions.remove(commandName) != null;
        }

        @Override
        public void onIncomingCommand(@Nonnull Handler handler) {
            this.handler.set(handler);
        }

        public Map<CommandMessage<?>, CompletableFuture<?>> getDispatchedCommands() {
            return dispatchedCommands;
        }

        public Map<String, Integer> getSubscriptions() {
            return subscriptions;
        }

        public Handler getHandler() {
            return handler.get();
        }
    }
}
