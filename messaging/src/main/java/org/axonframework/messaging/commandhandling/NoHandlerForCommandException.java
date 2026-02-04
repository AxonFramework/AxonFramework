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

package org.axonframework.messaging.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonTransientException;
import org.axonframework.messaging.core.retry.RetryScheduler;

import static java.lang.String.format;

/**
 * Exception indicating that no suitable command handler could be found for the given command.
 * <p>
 * As of 4.2, this exception has been moved to {@link AxonTransientException}, since (especially in a MicroServices
 * Architecture context) the handler may return. {@link RetryScheduler}s will now see this exception as retryable.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class NoHandlerForCommandException extends AxonTransientException {

    /**
     * Initialize this exception with the given {@code message}.
     *
     * @param message The message describing the cause of the exception.
     */
    public NoHandlerForCommandException(String message) {
        super(message);
    }

    /**
     * Initialize this exception with a message describing the given {@link CommandMessage}. This constructor specifies
     * in its message that missing parameters could be the culprit of finding a matching handler.
     *
     * @param commandMessage The {@link CommandMessage command} for which no handler was found.
     */
    public NoHandlerForCommandException(CommandMessage commandMessage) {
        this(format(
                "No matching handler available to handle command of type [%s]. To find a matching handler, "
                        + "note that the command handler's name should match the command's name, "
                        + "and all the parameters on the command handling method should be resolvable. "
                        + "It is thus recommended to validate both the name and the parameters.",
                commandMessage.type()
        ));
    }

    /**
     * Initialize this exception with a message describing the given {@link CommandMessage} and the given
     * {@code entityType}.
     *
     * @param message    The {@link CommandMessage} that was handled.
     * @param entityType The {@link Class} of the entity that was expected to handle the command.
     */
    public NoHandlerForCommandException(@Nonnull CommandMessage message, @Nonnull Class<?> entityType) {
        this(String.format(
                "No command handler was found for command of type [%s] for entity [%s]",
                message.type(),
                entityType.getName()
        ));
    }
}
