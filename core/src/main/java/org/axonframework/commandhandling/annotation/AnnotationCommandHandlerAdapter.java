/*
 * Copyright (c) 2010. Axon Framework
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
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.util.AbstractHandlerInvoker;
import org.axonframework.util.AnnotatedHandlerAdapter;
import org.springframework.util.ReflectionUtils;

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
public class AnnotationCommandHandlerAdapter extends AbstractHandlerInvoker
        implements org.axonframework.commandhandling.CommandHandler<Object>, AnnotatedHandlerAdapter {

    private final CommandBus commandBus;

    /**
     * Initialize the command handler adapter for the given <code>target</code> which is to be subscribed with the given
     * <code>commandBus</code>.
     * <p/>
     * Note that you need to call {@link #subscribe()} to actually subscribe the command handlers to the command bus.
     *
     * @param target     The object containing the @CommandHandler annotated methods
     * @param commandBus The command bus to which the handlers must be subscribed
     */
    public AnnotationCommandHandlerAdapter(Object target, CommandBus commandBus) {
        super(target, CommandHandler.class);
        this.commandBus = commandBus;
    }

    /**
     * Invokes the @CommandHandler annotated method that accepts the given <code>command</code>.
     *
     * @param command The command to handle
     * @return the result of the command handling. Is {@link Void#TYPE} when the annotated handler has a
     *         <code>void</code> return value.
     *
     * @throws NoHandlerForCommandException when no handler is found for given <code>command</code>.
     * @throws Throwable                    any exception occurring while handling the command
     */
    @Override
    public Object handle(Object command, UnitOfWork unitOfWork) throws Throwable {
        try {
            return invokeHandlerMethod(command, unitOfWork);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(String.format(
                    "An error occurred when handling a command of type [%s]",
                    command.getClass().getSimpleName()), e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Subscribe the command handlers to the command bus assigned during the initialization. A subscription is made with
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

    private <T> List<Class<? extends T>> findAcceptedHandlerParameters() {
        final List<Class<? extends T>> handlerParameters = new LinkedList<Class<? extends T>>();
        ReflectionUtils.doWithMethods(getTarget().getClass(), new CommandHandlerResolverCallback<T>(handlerParameters));
        return handlerParameters;
    }

    @Override
    protected Object onNoMethodFound(Class<?> parameterType) {
        throw new NoHandlerForCommandException(
                String.format("No Handler found for a command of type[%s]", parameterType.getSimpleName()));
    }

    /**
     * Returns the method that will be called to handle the given command. Returns <code>null</code> is no such method
     * is found.
     *
     * @param command The command to find the handler method for
     * @return the command handler method for the given command
     */
    public Method findCommandHandlerMethodFor(Object command) {
        return super.findHandlerMethod(command.getClass());
    }

    private static class CommandHandlerResolverCallback<T> implements ReflectionUtils.MethodCallback {

        private final List<Class<? extends T>> handlerParameters;

        public CommandHandlerResolverCallback(List<Class<? extends T>> handlerParameters) {
            this.handlerParameters = handlerParameters;
        }

        @Override
        @SuppressWarnings({"unchecked"})
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if (method.isAnnotationPresent(CommandHandler.class)) {
                handlerParameters.add((Class<T>) method.getParameterTypes()[0]);
            }
        }
    }
}