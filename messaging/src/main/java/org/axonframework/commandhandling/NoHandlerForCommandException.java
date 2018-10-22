/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.AxonNonTransientException;

import static java.lang.String.format;

/**
 * Exception indicating that no suitable handler could be found for the given command.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class NoHandlerForCommandException extends AxonNonTransientException {

    private static final long serialVersionUID = -7242262641697288852L;

    /**
     * Initialize a NoHandlerForCommandException with the given {@code message}.
     *
     * @param message The message describing the cause of the exception
     */
    public NoHandlerForCommandException(String message) {
        super(message);
    }

    /**
     * Initialize a NoHandlerForCommandException with a message describing the given {@code CommandMessage}.
     *
     * @param commandMessage The command for which no handler was found
     */
    public NoHandlerForCommandException(CommandMessage<?> commandMessage) {
        this(format("No handler available to handle command [%s]", commandMessage.getCommandName()));
    }
}
