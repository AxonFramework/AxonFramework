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

package org.axonframework.commandhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.*;
import org.axonframework.messaging.interceptors.TransactionManager;
import org.axonframework.messaging.unitofwork.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.lang.String.format;

/**
 * Implementation of the CommandBus that dispatches commands to the handlers subscribed to that specific type of
 * command. Interceptors may be configured to add processing to commands regardless of their type, for example logging,
 * security (authorization), sla monitoring, etc.
 * <p/>
 * This class can be monitored as the implementation of the <code>StatisticsProvider</code> interface indicates.
 *
 * @author Allard Buijze
 * @author Martin Tilma
 * @since 0.5
 */
public class SimpleCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>>> subscriptions =
            new ConcurrentHashMap<>();
    private volatile Iterable<MessageHandlerInterceptor<CommandMessage<?>>> handlerInterceptors
            = Collections.emptyList();
    private volatile Iterable<MessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors
            = Collections.emptyList();
    private UnitOfWorkFactory<?> unitOfWorkFactory = new DefaultUnitOfWorkFactory();
    private RollbackConfiguration rollbackConfiguration = RollbackConfigurationType.UNCHECKED_EXCEPTIONS;

    /**
     * Initializes the SimpleCommandBus.
     */
    public SimpleCommandBus() {
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
        for (MessageDispatchInterceptor<CommandMessage<?>> interceptor : dispatchInterceptors) {
            commandToDispatch = (CommandMessage<C>) interceptor.handle(commandToDispatch);
        }
        return commandToDispatch;
    }

    /**
     * Performs the actual dispatching logic. The dispatch interceptors must have been invoked at this point.
     *
     * @param command  The actual command to dispatch to the handler
     * @param callback The callback to notify of the result
     * @param <R>      The type of result expected from the command handler
     */
    @SuppressWarnings({"unchecked"})
    protected <C, R> void doDispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        try {
            MessageHandler<? super CommandMessage<?>> handler = findCommandHandlerFor(command);
            Object result = doDispatch(command, handler);
            callback.onSuccess(command, (R) result);
        } catch (Exception throwable) {
            callback.onFailure(command, throwable);
        }
    }

    private MessageHandler<? super CommandMessage<?>> findCommandHandlerFor(CommandMessage<?> command) {
        final MessageHandler<? super CommandMessage<?>> handler = subscriptions.get(command.getCommandName());
        if (handler == null) {
            throw new NoHandlerForCommandException(format("No handler was subscribed to command [%s]",
                                                          command.getCommandName()));
        }
        return handler;
    }

    private <C> Object doDispatch(CommandMessage<C> command, MessageHandler<? super CommandMessage<?>> handler) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Dispatching command [{}]", command.getCommandName());
        }
        UnitOfWork<CommandMessage<?>> unitOfWork = unitOfWorkFactory.createUnitOfWork(command);
        InterceptorChain chain = new DefaultInterceptorChain<>(unitOfWork, handlerInterceptors, handler);
        return unitOfWork.executeWithResult(chain::proceed, rollbackConfiguration);
    }

    /**
     * Subscribe the given <code>handler</code> to commands of type <code>commandType</code>. If a subscription already
     * exists for the given type, then the new handler takes over the subscription.
     *
     * @param commandName The type of command to subscribe the handler to
     * @param handler     The handler instance that handles the given type of command
     */
    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        subscriptions.put(commandName, handler);
        return () -> subscriptions.remove(commandName, handler);
    }

    /**
     * Registers the given list of interceptors to the command bus. All incoming commands will pass through the
     * interceptors at the given order before the command is passed to the handler for processing.
     *
     * @param handlerInterceptors The interceptors to invoke when commands are handled
     */
    public void setHandlerInterceptors(List<? extends MessageHandlerInterceptor<CommandMessage<?>>> handlerInterceptors) {
        this.handlerInterceptors = new ArrayList<>(handlerInterceptors);
    }

    /**
     * Registers the given list of dispatch interceptors to the command bus. All incoming commands will pass through
     * the interceptors at the given order before the command is dispatched toward the command handler.
     *
     * @param dispatchInterceptors The interceptors to invoke when commands are dispatched
     */
    public void setDispatchInterceptors(
            List<? extends MessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors) {
        this.dispatchInterceptors = new ArrayList<>(dispatchInterceptors);
    }

    /**
     * Sets the UnitOfWorkFactory that provides the UnitOfWork instances for handling incoming commands. Defaults to a
     * {@link DefaultUnitOfWorkFactory}.
     * <p/>
     * This method should not be used in combination with
     * {@link #setTransactionManager(TransactionManager)}. For transaction support, ensure
     * the provided UnitOfWorkFactory implementation binds each UnitOfWork to a transaction.
     *
     * @param unitOfWorkFactory The UnitOfWorkFactory providing UoW instances for this Command Bus.
     */
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    /**
     * Sets the transaction manager that manages the transaction around command handling. This should not be used in
     * combination with {@link #setUnitOfWorkFactory(UnitOfWorkFactory)}.
     *
     * @param transactionManager the transaction manager to use
     */
    public void setTransactionManager(TransactionManager transactionManager) {
        this.unitOfWorkFactory = new DefaultUnitOfWorkFactory(transactionManager);
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
