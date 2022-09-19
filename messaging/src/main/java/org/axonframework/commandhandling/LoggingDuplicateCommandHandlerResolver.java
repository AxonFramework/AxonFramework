/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.messaging.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Implementation of the DuplicateCommandHandlerResolver that allows registrations to be overridden by new handlers, but
 * logs this (on WARN level) to a given logger.
 *
 * @author Allard Buijze
 * @since 4.2
 */
public class LoggingDuplicateCommandHandlerResolver implements DuplicateCommandHandlerResolver {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDuplicateCommandHandlerResolver.class);
    private static final LoggingDuplicateCommandHandlerResolver INSTANCE = new LoggingDuplicateCommandHandlerResolver();


    /**
     * Returns an instance that logs duplicate registrations.
     *
     * @return an instance that logs duplicate registrations
     */
    public static LoggingDuplicateCommandHandlerResolver instance() {
        return INSTANCE;
    }

    private LoggingDuplicateCommandHandlerResolver() {
    }

    @Override
    public MessageHandler<? super CommandMessage<?>> resolve(@Nonnull String commandName,
                                                             @Nonnull MessageHandler<? super CommandMessage<?>> registeredHandler,
                                                             @Nonnull MessageHandler<? super CommandMessage<?>> candidateHandler) {

        logger.warn("A duplicate command handler was found for command [{}]. "
                            + "The handler in [{}] has been replaced by the handler in [{}].",
                    commandName,
                    registeredHandler.getTargetType().getName(),
                    candidateHandler.getTargetType().getName()
        );
        return candidateHandler;
    }
}
