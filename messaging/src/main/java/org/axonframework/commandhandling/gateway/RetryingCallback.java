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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.lock.DeadlockException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

/**
 * Callback implementation that will invoke a retry scheduler if a command results in a runtime exception.
 * <p/>
 * Generally, it is not necessary to use this class directly. It is used by CommandGateway implementations to support
 * retrying of commands.
 *
 * @param <R> The type of return value expected by the callback
 * @param <C> The type of payload of the dispatched command
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0
 */
public class RetryingCallback<C, R> implements CommandCallback<C, R> {

    private final CommandCallback<C, R> delegate;
    private final RetryScheduler retryScheduler;
    private final CommandBus commandBus;
    private final List<Class<? extends Throwable>[]> history;

    /**
     * Initialize the RetryingCallback with the given {@code delegate}, representing the actual callback passed as
     * a parameter to dispatch, the given {@code commandMessage}, {@code retryScheduler} and
     * {@code commandBus}.
     *
     * @param delegate       The callback to invoke when the command succeeds, or when retries are rejected.
     * @param retryScheduler The scheduler that decides if and when a retry should be scheduled
     * @param commandBus     The commandBus on which the command must be dispatched
     */
    public RetryingCallback(CommandCallback<C, R> delegate,
                            RetryScheduler retryScheduler,
                            CommandBus commandBus) {
        this.delegate = delegate;
        this.retryScheduler = retryScheduler;
        this.commandBus = commandBus;
        this.history = new ArrayList<>();
    }

    @Override
    public void onResult(@Nonnull CommandMessage<? extends C> commandMessage,
                         @Nonnull CommandResultMessage<? extends R> commandResultMessage) {
        if (commandResultMessage.isExceptional()) {
            Throwable cause = commandResultMessage.exceptionResult();
            history.add(simplify(cause));
            try {
                // We fail immediately when the exception is checked,
                // or when it is a Deadlock Exception and we have an active unit of work.
                if (!(cause instanceof RuntimeException)
                        || (isCausedBy(cause, DeadlockException.class) && CurrentUnitOfWork.isStarted())
                        || !retryScheduler.scheduleRetry(commandMessage, (RuntimeException) cause,
                                                         new ArrayList<>(history),
                                                         new RetryDispatch(commandMessage))) {
                    delegate.onResult(commandMessage, commandResultMessage);
                }
            } catch (Exception e) {
                delegate.onResult(commandMessage, asCommandResultMessage(e));
            }
        } else {
            delegate.onResult(commandMessage, commandResultMessage);
        }
    }

    private boolean isCausedBy(Throwable exception, Class<? extends Throwable> causeType) {
        return causeType.isInstance(exception)
                || (exception.getCause() != null && isCausedBy(exception.getCause(), causeType));
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Throwable>[] simplify(Throwable cause) {
        List<Class<? extends Throwable>> types = new ArrayList<>();
        types.add(cause.getClass());
        Throwable rootCause = cause;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
            types.add(rootCause.getClass());
        }
        return types.toArray(new Class[0]);
    }

    private class RetryDispatch implements Runnable {

        private final CommandMessage<? extends C> commandMessage;

        private RetryDispatch(CommandMessage<? extends C> commandMessage) {
            this.commandMessage = commandMessage;
        }

        @Override
        public void run() {
            try {
                commandBus.dispatch(commandMessage, RetryingCallback.this);
            } catch (Exception e) {
                RetryingCallback.this.onResult(commandMessage, asCommandResultMessage(e));
            }
        }
    }
}
