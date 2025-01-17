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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.configuration.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * TODO documentation
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public /*non-sealed */interface CommandHandler extends MessageHandler {

    /**
     * @param command
     * @param context
     * @return
     */
    @Nonnull
    MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                            @Nonnull ProcessingContext context);

    // TODO discuss if we want to deviate from the MessageStream here.
    // Foreseen downside of doing so, is removal of void/CompletableFuture/Mono return type flexibility
    default CompletableFuture<?> handleSimple(@Nonnull CommandMessage<?> command,
                                              @Nonnull ProcessingContext context) {
        return handle(command, context).firstAsCompletableFuture();
    }
}
