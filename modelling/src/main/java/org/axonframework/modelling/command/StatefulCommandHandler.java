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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.configuration.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.ModelContainer;

import javax.annotation.Nonnull;

/**
 * Interface describing a stateful handler of {@link CommandMessage commands}.
 * Receives a {@link ModelContainer container} where models can be fetched from to reach a decision about the
 * received command message.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 * @see ModelContainer
 * @see StatefulCommandHandlerRegistry
 */
@FunctionalInterface
public interface StatefulCommandHandler extends MessageHandler {

    /**
     * Handles the given {@code command} within the given {@code context}. The {@code models} parameter provides access
     * to the available models to load.
     * <p>
     * The {@link CommandResultMessage result message} in the returned {@link MessageStream stream} may be {@code null}.
     * Only a {@link MessageStream#just(Message) single} or {@link MessageStream#empty() empty} result message should
     * ever be expected.
     *
     * @param command The command to handle.
     * @param models  The container providing access to the available models to load.
     * @param context The context to the given {@code command} is handled in.
     * @return A {@code MessagesStream} of a {@link CommandResultMessage}.
     */
    @Nonnull
    MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                            @Nonnull ModelContainer models,
                                                            @Nonnull ProcessingContext context);
}
