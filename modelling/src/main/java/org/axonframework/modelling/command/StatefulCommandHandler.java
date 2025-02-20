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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.configuration.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import javax.annotation.Nonnull;

/**
 * Interface describing a stateful handler of {@link CommandMessage commands}.
 * This means it will get a model to work with, which can be updated based on the command.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @param <M> The type of model this handler can handle
 * @since 5.0.0
 */
@FunctionalInterface
public interface StatefulCommandHandler<M> extends MessageHandler {


    @Nonnull
    MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                            @Nonnull M model,
                                                            @Nonnull ProcessingContext context);
}
