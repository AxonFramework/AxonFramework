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

import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link DuplicateCommandHandlingMemberResolver} that allows initialize to be overridden by
 * first handler member, but logs this (on WARN level) to a given logger.
 *
 * @author leechedan
 * @since 4.5
 */
public class LoggingDuplicateCommandHandlingMemberResolver implements DuplicateCommandHandlingMemberResolver {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDuplicateCommandHandlingMemberResolver.class);
    private static final LoggingDuplicateCommandHandlingMemberResolver INSTANCE =
            new LoggingDuplicateCommandHandlingMemberResolver();


    /**
     * Returns an instance that logs duplicate registrations.
     *
     * @return an instance that logs duplicate registrations
     */
    public static LoggingDuplicateCommandHandlingMemberResolver instance() {
        return INSTANCE;
    }

    private LoggingDuplicateCommandHandlingMemberResolver() {
    }

    public <T> MessageHandlingMember<? super T> resolve(String commandName,
                                                        List<MessageHandlingMember<? super T>> messageHandlingMemberList) {

        String methods = messageHandlingMemberList.stream()
                                                  .map(item -> item.getTargetMethod() == null ?
                                                          commandName : item.getTargetMethod().toString())
                                                  .collect(Collectors.joining(","));
        logger.warn("Duplicate command handler members [{}] were found for command [{}]. ", methods, commandName);
        return messageHandlingMemberList.stream().findFirst().get();
    }
}
