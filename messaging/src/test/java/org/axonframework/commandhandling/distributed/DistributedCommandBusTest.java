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
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DistributedCommandBus}.
 *
 * @author Allard Buijze
 */
@SuppressWarnings("unchecked")
class DistributedCommandBusTest {

    private DistributedCommandBus testSubject;

    private Connector connector;

    @BeforeEach
    void setUp() {
        connector = new StubConnector();
        testSubject = new DistributedCommandBus(new SimpleCommandBus(), connector);
    }

    @Test
    void name() {
        fail("Not implemented yet");
    }

    private class StubConnector implements Connector {

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
