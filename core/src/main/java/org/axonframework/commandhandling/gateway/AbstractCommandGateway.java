/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.commandhandling.callbacks.FutureCallback;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;

/**
 * @author Allard Buijze
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
        this.commandBus = commandBus;
        this.dispatchInterceptors = new ArrayList<CommandDispatchInterceptor>(commandDispatchInterceptors);
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
        CommandMessage commandMessage = processInterceptors(asCommandMessage(command));
        CommandCallback<R> commandCallback = callback;
        if (retryScheduler != null) {
            commandCallback = new RetryingCallback<R>(callback, commandMessage, retryScheduler, commandBus);
        }
        commandBus.dispatch(commandMessage, commandCallback);
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

    /**
     * Dispatches a command and returns a Future from which the processing results can be retrieved.
     *
     * @param command The command to dispatch
     * @param <R>     The expected result of the command
     * @return a future providing access to the command's result
     */
    protected <R> FutureCallback<R> doSend(Object command) {
        FutureCallback<R> futureCallback = new FutureCallback<R>();
        send(command, futureCallback);
        return futureCallback;
    }
}
