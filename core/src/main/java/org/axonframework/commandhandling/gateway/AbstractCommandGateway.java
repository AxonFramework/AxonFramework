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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.common.Assert;
import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.domain.MetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;

/**
 * Abstract implementation of a CommandGateway, which handles the dispatch interceptors and retrying on failure. The
 * actual dispatching of commands is left to the subclasses.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public abstract class AbstractCommandGateway {

    private final CommandBus commandBus;
    private final RetryScheduler retryScheduler;
    private final List<CommandDispatchInterceptor> dispatchInterceptors;

    /**
     * Initialize the AbstractCommandGateway with given <code>commandBus</code>, <code>retryScheduler</code> and
     * <code>commandDispatchInterceptors</code>.
     *
     * @param commandBus                  The command bus on which to dispatch events
     * @param retryScheduler              The scheduler capable of performing retries of failed commands. May be
     *                                    <code>null</code> when to prevent retries.
     * @param commandDispatchInterceptors The interceptors to invoke when dispatching a command
     */
    protected AbstractCommandGateway(CommandBus commandBus, RetryScheduler retryScheduler,
                                     List<CommandDispatchInterceptor> commandDispatchInterceptors) {
        Assert.notNull(commandBus, "commandBus may not be null");
        this.commandBus = commandBus;
        if (commandDispatchInterceptors != null && !commandDispatchInterceptors.isEmpty()) {
            this.dispatchInterceptors = new ArrayList<CommandDispatchInterceptor>(commandDispatchInterceptors);
        } else {
            this.dispatchInterceptors = Collections.emptyList();
        }
        this.retryScheduler = retryScheduler;
    }

    /**
     * Sends the given <code>command</code>, and invokes the <code>callback</code> when the command is processed.
     *
     * @param command  The command to dispatch
     * @param callback The callback to notify with the processing result
     * @param <R>      The type of response expected from the command
     */
    protected <R> void send(Object command, CommandCallback<R> callback) {
        CommandMessage commandMessage = processInterceptors(createCommandMessage(command));
        CommandCallback<R> commandCallback = callback;
        if (retryScheduler != null) {
            commandCallback = new RetryingCallback<R>(callback, commandMessage, retryScheduler, commandBus);
        }
        commandBus.dispatch(commandMessage, commandCallback);
    }

    /**
     * Dispatches a command without callback. When dispatching fails, since there is no callback, the command will
     * <em>not</em> be retried.
     *
     * @param command The command to dispatch
     */
    protected void sendAndForget(Object command) {
        if (retryScheduler == null) {
            commandBus.dispatch(processInterceptors(createCommandMessage(command)));
        } else {
            CommandMessage<?> commandMessage = createCommandMessage(command);
            send(commandMessage, new LoggingCallback(commandMessage));
        }
    }

    private CommandMessage createCommandMessage(Object command) {
        CommandMessage<?> message = asCommandMessage(command);
        return message.withMetaData(MetaData.from(CorrelationDataHolder.getCorrelationData())
                                            .mergedWith(message.getMetaData()));
    }

    /**
     * Invokes all the dispatch interceptors and returns the CommandMessage instance that should be dispatched.
     *
     * @param commandMessage The incoming command message
     * @return The command message to dispatch
     */
    protected CommandMessage processInterceptors(CommandMessage commandMessage) {
        CommandMessage message = commandMessage;
        for (CommandDispatchInterceptor dispatchInterceptor : dispatchInterceptors) {
            message = dispatchInterceptor.handle(message);
        }
        return message;
    }
}
