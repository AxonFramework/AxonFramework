/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class RoutingKeyResolvingConnector implements Connector {

    private final Connector delegate;
    private final RoutingStrategy routingStrategy;

    public RoutingKeyResolvingConnector(Connector delegate, RoutingStrategy routingStrategy) {
        this.delegate = delegate;
        this.routingStrategy = routingStrategy;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(CommandMessage<?> command,
                                                            ProcessingContext processingContext) {
        String routingKey = routingStrategy.getRoutingKey(command);
        if (routingKey == null) {
            return delegate.dispatch(command, processingContext);
        } else {
            return delegate.dispatch(command,
                                     processingContext.withResource(RoutingStrategy.ROUTING_KEY, routingKey));
        }
    }

    @Override
    public void subscribe(String commandName, int loadFactor) {
        delegate.subscribe(commandName, loadFactor);
    }

    @Override
    public boolean unsubscribe(String commandName) {
        return delegate.unsubscribe(commandName);
    }

    @Override
    public void onIncomingCommand(BiConsumer<CommandMessage<?>, ResultCallback> handler) {
        delegate.onIncomingCommand(handler);
    }
}
