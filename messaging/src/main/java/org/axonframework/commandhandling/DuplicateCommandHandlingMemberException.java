/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception indicating duplicate Command Handling Members were detected.
 *
 * @author leechedan
 * @since 4.5
 */
public class DuplicateCommandHandlingMemberException extends AxonNonTransientException {

    private static final long serialVersionUID = 7168111526309151296L;

    /**
     * Initialize a duplicate command handling member exception using the given {@code payloadType} and {@code
     * duplicateHandlerMembers} to form a specific message.
     *
     * @param payloadType The payloadType of the command for which the duplicates were detected
     * @param duplicateHandlerMembers the duplicated {@link MessageHandler}
     */
    public <T> DuplicateCommandHandlingMemberException(Class<?> payloadType,
                                                       List<MessageHandlingMember<? super T>> duplicateHandlerMembers) {
        this(String.format("Multi command handler members [%s] were found for command [%s].",
                           duplicateHandlerMembers.stream()
                                                  .map(item -> item.getTargetMethod() == null ?
                                                          payloadType.getName() : item.getTargetMethod().toString())
                                                  .collect(Collectors.joining(",")),
                           payloadType.getName()));
    }

    /**
     * Initializes multi command handling member exception using the given {@code message}.
     *
     * @param message the message describing the exception
     */
    public DuplicateCommandHandlingMemberException(String message) {
        super(message);
    }
}
