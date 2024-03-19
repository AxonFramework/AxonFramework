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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DistributedCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final Connector connector;

    public DistributedCommandBus(CommandBus delegate, Connector connector) {
        this.delegate = delegate;
        this.connector = connector;
        connector.onIncomingCommand(
                (command, callback) -> delegate.dispatch(command, ProcessingContext.NONE)
                                               .whenComplete((chr, e) -> {
                                                   if (e == null) {
                                                       callback.success(chr);
                                                   } else {
                                                       callback.error(e);
                                                   }
                                               })
        );
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                               @Nullable ProcessingContext processingContext) {
        return connector.dispatch(command, processingContext);
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        Registration delegateSubscription = delegate.subscribe(commandName, handler);
        connector.subscribe(commandName, 100);
        return () -> delegateSubscription.cancel() && connector.unsubscribe(commandName);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("connector", connector);
    }
}
