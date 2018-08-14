/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.*;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.String.format;

/**
 * Implementation of the CommandBus that dispatches commands to the handlers subscribed to that specific command's name.
 * Interceptors may be configured to add processing to commands regardless of their type or name, for example logging,
 * security (authorization), sla monitoring, etc.
 *
 * @author Allard Buijze
 * @author Martin Tilma
 * @since 0.5
 */
public class SimpleCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>>> subscriptions =
            new ConcurrentHashMap<>();
    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors
            = new CopyOnWriteArrayList<>();
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors
            = new CopyOnWriteArrayList<>();
    private final MessageMonitor<? super CommandMessage<?>> messageMonitor;
    private final TransactionManager transactionManager;
    private RollbackConfiguration rollbackConfiguration = RollbackConfigurationType.UNCHECKED_EXCEPTIONS;

    /**
     * Initializes the SimpleCommandBus. This instance shall not manage any transactions or expose any monitoring
     * information.
     */
    public SimpleCommandBus() {
        this(NoTransactionManager.INSTANCE, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes the SimpleCommandBus with the given {@code transactionManager} and {@code messageMonitor}
     *
     * @param transactionManager The transactionManager to manage transaction with
     * @param messageMonitor     the message monitor to monitor the command bus
     */
    public SimpleCommandBus(TransactionManager transactionManager,
                            MessageMonitor<? super CommandMessage<?>> messageMonitor) {
        this.transactionManager = transactionManager;
        this.messageMonitor = messageMonitor;
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, final CommandCallback<? super C, R> callback) {
        doDispatch(intercept(command), callback);
    }

    /**
     * Invokes all the dispatch interceptors.
     *
     * @param command The original command being dispatched
     * @return The command to actually dispatch
     */
    @SuppressWarnings("unchecked")
    protected <C> CommandMessage<C> intercept(CommandMessage<C> command) {
        CommandMessage<C> commandToDispatch = command;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> interceptor : dispatchInterceptors) {
            commandToDispatch = (CommandMessage<C>) interceptor.handle(commandToDispatch);
        }
        return commandToDispatch;
    }

    /**
     * Performs the actual dispatching logic. The dispatch interceptors must have been invoked at this point.
     *
     * @param command  The actual command to dispatch to the handler
     * @param callback The callback to notify of the result
     * @param <C>      The type of payload of the command
     * @param <R>      The type of result expected from the command handler
     */
    protected <C, R> void doDispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(command);

        MessageHandler<? super CommandMessage<?>> handler = findCommandHandlerFor(command).orElseThrow(() -> {
            NoHandlerForCommandException exception = new NoHandlerForCommandException(
                    format("No handler was subscribed to command [%s]", command.getCommandName()));
            monitorCallback.reportFailure(exception);
            return exception;
        });

        handle(command, handler, new MonitorAwareCallback<>(callback, monitorCallback));
    }

    private Optional<MessageHandler<? super CommandMessage<?>>> findCommandHandlerFor(CommandMessage<?> command) {
        return Optional.ofNullable(subscriptions.get(command.getCommandName()));
    }

    /**
     * Performs the actual handling logic.
     *
     * @param command  The actual command to handle
     * @param handler  The handler that must be invoked for this command
     * @param callback The callback to notify of the result
     * @param <C>      The type of payload of the command
     * @param <R>      The type of result expected from the command handler
     */
    @SuppressWarnings({"unchecked"})
    protected <C, R> void handle(CommandMessage<C> command, MessageHandler<? super CommandMessage<?>> handler, CommandCallback<? super C, R> callback) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handling command [{}]", command.getCommandName());
        }

        try {
            UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(command);
            unitOfWork.attachTransaction(transactionManager);
            InterceptorChain chain = new DefaultInterceptorChain<>(unitOfWork, handlerInterceptors, handler);

            R result = (R) unitOfWork.executeWithResult(chain::proceed, rollbackConfiguration);

            callback.onSuccess(command, result);
        } catch (Exception e) {
            callback.onFailure(command, e);
        }
    }

    /**
     * Subscribe the given {@code handler} to commands with given {@code commandName}. If a subscription already
     * exists for the given name, then the new handler takes over the subscription.
     */
    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        subscriptions.put(commandName, handler);
        return () -> subscriptions.remove(commandName, handler);
    }

    /**
     * Registers the given interceptor to the command bus. All incoming commands will pass through the
     * registered interceptors at the given order before the command is passed to the handler for processing.
     *
     * @param handlerInterceptor The interceptor to invoke when commands are handled
     * @return handle to unregister the interceptor
     */
    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        handlerInterceptors.add(handlerInterceptor);
        return () -> handlerInterceptors.remove(handlerInterceptor);
    }

    /**
     * Registers the given list of dispatch interceptors to the command bus. All incoming commands will pass through
     * the interceptors at the given order before the command is dispatched toward the command handler.
     *
     * @param dispatchInterceptor The interceptors to invoke when commands are dispatched
     * @return handle to unregister the interceptor
     */
    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    /**
     * Sets the RollbackConfiguration that allows you to change when the UnitOfWork is committed. If not set the
     * RollbackOnUncheckedExceptionConfiguration will be used, which triggers a rollback on all unchecked exceptions.
     *
     * @param rollbackConfiguration The RollbackConfiguration.
     */
    public void setRollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
        this.rollbackConfiguration = rollbackConfiguration;
    }
}
