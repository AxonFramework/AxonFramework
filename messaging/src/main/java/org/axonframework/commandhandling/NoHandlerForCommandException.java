/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.retry.RetryScheduler;
import org.axonframework.common.AxonTransientException;

import static java.lang.String.format;

/**
 * Exception indicating that no suitable command handler could be found for the given command.
 * <p>
 * As of 4.2, this exception has been moved to {@link AxonTransientException}, since (especially in a MicroServices
 * Architecture context) the handler may return. {@link RetryScheduler}s will
 * now see this exception as retryable.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class NoHandlerForCommandException extends AxonTransientException {

    private static final long serialVersionUID = -7202076465339197011L;

    /**
     * Initialize this exception with the given {@code message}.
     *
     * @param message the message describing the cause of the exception
     */
    public NoHandlerForCommandException(String message) {
        super(message);
    }

    /**
     * Initialize this exception with a message describing the given {@link CommandMessage}. This constructor specifies
     * in its message that missing parameters could be the culprit of finding a matching handler.
     *
     * @param commandMessage the {@link CommandMessage command} for which no handler was found
     */
    public NoHandlerForCommandException(CommandMessage<?> commandMessage) {
        this(format(
                "No matching handler available to handle command [%s]. To find a matching handler, "
                        + "note that the command handler's name should match the command's name, "
                        + "and all the parameters on the command handling method should be resolvable. "
                        + "It is thus recommended to validate both the name and the parameters.",
                commandMessage.getCommandName()
        ));
    }
}
