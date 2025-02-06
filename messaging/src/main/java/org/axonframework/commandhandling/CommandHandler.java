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

/**
 * Interface describing a handler of {@link CommandMessage commands}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface CommandHandler extends MessageHandler {

    /**
     * Handles the given {@code command} within the given {@code context}.
     * <p>
     * The {@link CommandResultMessage result message} in the returned {@link MessageStream stream} may be {@code null}.
     * Only a single result message should ever be expected.
     *
     * @param command The command to handle.
     * @param context The context to the given {@code command} is handled in.
     * @return A {@code MessagesStream} of a {@link CommandResultMessage}.
     */
    @Nonnull
    MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                            @Nonnull ProcessingContext context);
}
