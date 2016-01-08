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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.SupportedCommandNamesAware;
import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandlerInspector;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.*;

/**
 * Adapter that turns any {@link CommandHandler @CommandHandler} annotated bean into a {@link
 * MessageHandler} implementation. Each annotated method is subscribed
 * as a CommandHandler at the {@link CommandBus} for the command type specified by the parameter of that method.
 *
 * @author Allard Buijze
 * @see CommandHandler
 * @since 0.5
 */
public class AnnotationCommandHandlerAdapter implements MessageHandler<CommandMessage<?>>, SupportedCommandNamesAware {

    private final Map<String, MethodMessageHandler> handlers = new HashMap<>();
    private final Object target;
    private final ParameterResolverFactory parameterResolverFactory;

    /**
     * Wraps the given <code>annotatedCommandHandler</code>, allowing it to be subscribed to a Command Bus.
     *
     * @param annotatedCommandHandler The object containing the @CommandHandler annotated methods
     */
    public AnnotationCommandHandlerAdapter(Object annotatedCommandHandler) {
        this(annotatedCommandHandler, ClasspathParameterResolverFactory.forClass(annotatedCommandHandler.getClass()));
    }

    /**
     * Wraps the given <code>annotatedCommandHandler</code>, allowing it to be subscribed to a Command Bus.
     *
     * @param annotatedCommandHandler  The object containing the @CommandHandler annotated methods
     * @param parameterResolverFactory The strategy for resolving handler method parameter values
     */
    public AnnotationCommandHandlerAdapter(Object annotatedCommandHandler,
                                           ParameterResolverFactory parameterResolverFactory) {
        Assert.notNull(annotatedCommandHandler, "annotatedCommandHandler may not be null");
        MethodMessageHandlerInspector inspector = MethodMessageHandlerInspector.getInstance(
                annotatedCommandHandler.getClass(), CommandHandler.class, parameterResolverFactory, true);
        for (MethodMessageHandler handler : inspector.getHandlers()) {
            String commandName = CommandMessageHandlerUtils.resolveAcceptedCommandName(handler);
            handlers.put(commandName, handler);
        }
        this.parameterResolverFactory = parameterResolverFactory;
        this.target = annotatedCommandHandler;
    }

    /**
     * Subscribe this command handler to the given <code>commandBus</code>. The command handler will be subscribed
     * for each of the supported commands.
     *
     * @param commandBus    The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Registration subscribe(CommandBus commandBus) {
        Collection<Registration> subscriptions = new ArrayDeque<>();
        for (String supportedCommand : supportedCommandNames()) {
            subscriptions.add(commandBus.subscribe(supportedCommand, this));
        }
        return () -> {
            subscriptions.forEach(Registration::cancel);
            return true;
        };
    }

    /**
     * Invokes the @CommandHandler annotated method that accepts the given <code>command</code>.
     *
     * @param command    The command to handle
     * @param unitOfWork The UnitOfWork the command is processed in
     * @return the result of the command handling. Is <code>null</code> when the annotated handler has a
     * <code>void</code> return value.
     *
     * @throws NoHandlerForCommandException when no handler is found for given <code>command</code>.
     * @throws Exception any exception occurring while handling the command
     */
    @Override
    public Object handle(CommandMessage<?> command, UnitOfWork unitOfWork) throws Exception {
        final MethodMessageHandler handler = handlers.get(command.getCommandName());
        if (handler == null) {
            throw new NoHandlerForCommandException("No handler found for command " + command.getCommandName());
        }
        if (unitOfWork != null) {
            unitOfWork.resources().put(ParameterResolverFactory.class.getName(), parameterResolverFactory);
        }
        return handler.invoke(target, command);
    }

    @Override
    public Set<String> supportedCommandNames() {
        return handlers.keySet();
    }
}
