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

import org.axonframework.common.AxonNonTransientException;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.QualifiedName;

/**
 * Exception indicating a duplicate Command Handler was subscribed whilst this behavior is purposefully guarded
 * against.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
public class DuplicateCommandHandlerSubscriptionException extends AxonNonTransientException {

    /**
     * Initialize a duplicate command handler subscription exception using the given {@code initialHandler} and
     * {@code duplicateHandler} to form a specific message.
     *
     * @param name             The name of the command for which the duplicate was detected.
     * @param initialHandler   the initial {@link MessageHandler} for which a duplicate was encountered.
     * @param duplicateHandler The duplicated {@link MessageHandler}.
     */
    public DuplicateCommandHandlerSubscriptionException(QualifiedName name,
                                                        CommandHandler initialHandler,
                                                        CommandHandler duplicateHandler) {
        this(String.format("Duplicate subscription for command [%s] detected. Registration of handler [%s] "
                                   + " conflicts with previously registered handler [%s].",
                           name,
                           initialHandler,
                           duplicateHandler
        ));
    }

    /**
     * Initializes a duplicate command handler subscription exception using the given {@code message}.
     *
     * @param message The message describing the exception.
     */
    public DuplicateCommandHandlerSubscriptionException(String message) {
        super(message);
    }
}
