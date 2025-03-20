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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * The mechanism that dispatches {@link CommandMessage commands} to their appropriate
 * {@link CommandHandler command handler}.
 * <p>
 * Command handlers can {@link #subscribe(QualifiedName, CommandHandler) subscribe} and unsubscribe to specific commands
 * on the command bus. A command being {@link #dispatch(CommandMessage, ProcessingContext) dispatched} matches a command
 * handler based on the {@link QualifiedName} present in the {@link CommandMessage#type() command's type}.
 * <p>
 * Only a <em>single</em> handler may be subscribed for a single command name at any time.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface CommandBus extends CommandHandlerRegistry<CommandBus>, DescribableComponent {

    /**
     * Dispatch the given {@code command} to the {@link CommandHandler command handler}
     * {@link #subscribe(QualifiedName, CommandHandler) subscribed} to the given {@code command}'s name. The name is
     * typically deferred from the {@link Message#type()}, which contains a {@link MessageType#qualifiedName()}.
     *
     * @param command           The command to dispatch.
     * @param processingContext The processing context under which the command is being published (can be
     *                          {@code null}).
     * @return The {@code CompletableFuture} providing the result of the command, once finished.
     * @throws NoHandlerForCommandException when no {@link CommandHandler command handler} is registered for the given
     *                                      {@code command}'s name.
     */
    CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                     @Nullable ProcessingContext processingContext);
}
