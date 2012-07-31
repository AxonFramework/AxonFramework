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

    protected final CommandBus commandBus;
    protected final RetryScheduler retryScheduler;
    protected final List<CommandDispatchInterceptor> dispatchInterceptors;

    protected AbstractCommandGateway(CommandBus commandBus, RetryScheduler retryScheduler,
                                     List<CommandDispatchInterceptor> commandDispatchInterceptors) {
        this.commandBus = commandBus;
        this.dispatchInterceptors = new ArrayList<CommandDispatchInterceptor>(commandDispatchInterceptors);
        this.retryScheduler = retryScheduler;
    }

    protected <R> void send(Object command, CommandCallback<R> callback) {
        CommandMessage commandMessage = processInterceptors(asCommandMessage(command));
        CommandCallback<R> commandCallback = callback;
        if (retryScheduler != null) {
            commandCallback = new RetryingCallback<R>(callback, commandMessage, retryScheduler, commandBus);
        }
        commandBus.dispatch(commandMessage, commandCallback);
    }

    protected CommandMessage processInterceptors(CommandMessage commandMessage) {
        CommandMessage message = commandMessage;
        for (CommandDispatchInterceptor dispatchInterceptor : dispatchInterceptors) {
            message = dispatchInterceptor.handle(message);
        }
        return message;
    }

    protected <R> FutureCallback<R> doSend(Object command) {
        FutureCallback<R> futureCallback = new FutureCallback<R>();
        send(command, futureCallback);
        return futureCallback;
    }
}
