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

package org.axonframework.commandhandling;

import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.configuration.CommandHandler;
import org.axonframework.messaging.configuration.MessageHandlerRegistry;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The mechanism that dispatches Command objects to their appropriate CommandHandler. CommandHandlers can subscribe and
 * unsubscribe to specific commands (identified by their {@link CommandMessage#getCommandName() name}) on the command
 * bus. Only a single handler may be subscribed for a single command name at any time.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface CommandBus extends MessageHandlerRegistry<CommandHandler>, DescribableComponent {

    /**
     * Dispatch the given {@code command} to the CommandHandler subscribed to the given {@code command}'s name.
     *
     * @param command           The Command to dispatch
     * @param processingContext The processing context under which the command is being published (can be {@code null})
     * @return The CompletableFuture providing the result of the command, once finished
     * @throws NoHandlerForCommandException when no command handler is registered for the given {@code command}'s name.
     */
    CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                     @Nullable ProcessingContext processingContext);
}
