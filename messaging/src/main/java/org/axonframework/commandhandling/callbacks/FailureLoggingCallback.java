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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CommandCallback} implementation wrapping another, that concisely logs failed commands. Since the full
 * exception is being reported to the delegate callback, the full stacktrace is not logged.
 *
 * @param <C> The type of payload of the command
 * @param <R> The return value of the command handler
 */
public class FailureLoggingCallback<C, R> implements CommandCallback<C, R> {

    private final CommandCallback<C, R> delegate;
    private final Logger logger;

    /**
     * Initialize the callback to delegate calls to the given {@code delegate}, logging failures on a logger
     * for this class (on warn level).
     *
     * @param delegate The command callback to forward invocations to
     */
    public FailureLoggingCallback(CommandCallback<C, R> delegate) {
        this(LoggerFactory.getLogger(FailureLoggingCallback.class), delegate);
    }

    /**
     * Initialize the callback to delegate calls to the given {@code delegate}, logging failures on the given
     * {@code logger} (on warn level).
     *
     * @param logger   The logger to log exceptions on
     * @param delegate The command callback to forward invocations to
     */
    public FailureLoggingCallback(Logger logger, CommandCallback<C, R> delegate) {
        this.logger = logger;
        this.delegate = delegate;
    }

    @Override
    public void onResult(CommandMessage<? extends C> commandMessage,
                         CommandResultMessage<? extends R> commandResultMessage) {
        commandResultMessage.optionalExceptionResult()
                            .ifPresent(cause ->
                                               logger.warn("Command '{}' resulted in {}({})",
                                                           commandMessage.getCommandName(),
                                                           cause.getClass().getName(),
                                                           cause.getMessage()));
        delegate.onResult(commandMessage, commandResultMessage);
    }
}
