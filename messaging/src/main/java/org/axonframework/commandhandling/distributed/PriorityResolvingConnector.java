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

public class PriorityResolvingConnector implements Connector {

    private final Connector delegate;
    private final PriorityResolver<? super CommandMessage<?>> priorityResolver;

    public PriorityResolvingConnector(Connector delegate, PriorityResolver<? super CommandMessage<?>> priorityResolver) {
        this.delegate = delegate;
        this.priorityResolver = priorityResolver;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(CommandMessage<?> command,
                                                            ProcessingContext processingContext) {
        return delegate.dispatch(command, processingContext);
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
