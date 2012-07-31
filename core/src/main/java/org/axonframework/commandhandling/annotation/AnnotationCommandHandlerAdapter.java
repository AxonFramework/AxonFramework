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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.Subscribable;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.domain.Message;
import org.axonframework.unitofwork.UnitOfWork;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Adapter that turns any {@link CommandHandler @CommandHandler} annotated bean into a {@link
 * org.axonframework.commandhandling.CommandHandler CommandHandler} implementation. Each annotated method is subscribed
 * as a CommandHandler at the {@link CommandBus} for the command type specified by the parameter of that method.
 *
 * @author Allard Buijze
 * @see CommandHandler
 * @since 0.5
 */
public class AnnotationCommandHandlerAdapter
        implements org.axonframework.commandhandling.CommandHandler<Object>, Subscribable {

    private final CommandBus commandBus;
    private final MessageHandlerInvoker invoker;

    /**
     * Subscribe the annotated command handler to the given command bus.
     *
     * @param annotatedCommandHandler The annotated command handler that is to be subscribed to the command bus
     * @param commandBus              The command bus that gets the handler's subscription
     * @return the Adapter created for the command handler target. Can be used to unsubscribe.
     */
    public static AnnotationCommandHandlerAdapter subscribe(Object annotatedCommandHandler, CommandBus commandBus) {
        AnnotationCommandHandlerAdapter adapter = new AnnotationCommandHandlerAdapter(annotatedCommandHandler,
                                                                                      commandBus);
        adapter.subscribe();
        return adapter;
    }

    /**
     * Initialize the command handler adapter for the given <code>target</code> which is to be subscribed with the
     * given <code>commandBus</code>.
     * <p/>
     * Note that you need to call {@link #subscribe()} to actually subscribe the command handlers to the command bus.
     *
     * @param target     The object containing the @CommandHandler annotated methods
     * @param commandBus The command bus to which the handlers must be subscribed
     */
    public AnnotationCommandHandlerAdapter(Object target, CommandBus commandBus) {
        this.invoker = new MessageHandlerInvoker(target, CommandHandler.class);
        this.commandBus = commandBus;
    }

    /**
     * Invokes the @CommandHandler annotated method that accepts the given <code>command</code>.
     *
     * @param command    The command to handle
     * @param unitOfWork The UnitOfWork the command is processed in
     * @return the result of the command handling. Is <code>null</code> when the annotated handler has a
     *         <code>void</code> return value.
     *
     * @throws NoHandlerForCommandException when no handler is found for given <code>command</code>.
     * @throws Throwable                    any exception occurring while handling the command
     */
    @Override
    public Object handle(CommandMessage<Object> command, UnitOfWork unitOfWork) throws Throwable {
        try {
            return invoker.invokeHandlerMethod(command, ErrorReportingNoMethodFoundCallback.INSTANCE);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Subscribe the command handlers to the command bus assigned during the initialization. A subscription is made
     * with
     * the command bus for each accepted type of command.
     */
    @Override
    @PostConstruct
    public void subscribe() {
        List<Class<?>> acceptedCommands = findAcceptedHandlerParameters();
        for (Class<?> acceptedCommand : acceptedCommands) {
            commandBus.subscribe(acceptedCommand, this);
        }
    }

    /**
     * Unsubscribe the command handlers from the command bus assigned during the initialization.
     */
    @Override
    @PreDestroy
    public void unsubscribe() {
        List<Class<?>> acceptedCommands = findAcceptedHandlerParameters();
        for (Class<?> acceptedCommand : acceptedCommands) {
            commandBus.unsubscribe(acceptedCommand, this);
        }
    }

    @SuppressWarnings({"unchecked"})
    private <T> List<Class<? extends T>> findAcceptedHandlerParameters() {
        final List<Class<? extends T>> handlerParameters = new LinkedList<Class<? extends T>>();
        for (Method m : ReflectionUtils.methodsOf(invoker.getTargetType())) {
            if (m.isAnnotationPresent(CommandHandler.class)) {
                handlerParameters.add((Class<T>) m.getParameterTypes()[0]);
            }
        }
        return handlerParameters;
    }

    private static class ErrorReportingNoMethodFoundCallback implements MessageHandlerInvoker.NoMethodFoundCallback {

        private static final ErrorReportingNoMethodFoundCallback INSTANCE = new ErrorReportingNoMethodFoundCallback();

        @Override
        public Object onNoMethodFound(Message message) {
            throw new NoHandlerForCommandException(String.format("No Handler found for 0%s]", message.toString()));
        }
    }
}
