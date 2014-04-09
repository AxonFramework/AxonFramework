/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CommandCallback implementation that simply logs the results of a command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class LoggingCallback implements CommandCallback<Object> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingCallback.class);

    private final CommandMessage<?> message;

    /**
     * Initialize a LoggingCallback for the given <code>message</code>.
     *
     * @param message The message for which the results are to be logged.
     */
    public LoggingCallback(CommandMessage<?> message) {
        this.message = message;
    }

    @Override
    public void onSuccess(Object result) {
        logger.info("Command executed successfully: {}", message.getCommandName());
    }

    @Override
    public void onFailure(Throwable cause) {
        logger.warn("Command resulted in exception: {}", message.getCommandName(), cause);
    }
}
