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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * CommandCallback implementation that simply logs the results of a command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class LoggingCallback implements CommandCallback<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingCallback.class);

    /**
     * The singleton instance for this callback
     */
    public static final LoggingCallback INSTANCE = new LoggingCallback();

    private LoggingCallback() {
    }

    @Override
    public void onResult(@Nonnull CommandMessage message, @Nonnull CommandResultMessage commandResultMessage) {
        if (commandResultMessage.isExceptional()) {
            logger.warn("Command resulted in exception: {}",
                        message.getCommandName(),
                        commandResultMessage.exceptionResult());
        } else {
            logger.info("Command executed successfully: {}", message.getCommandName());
        }
    }
}
