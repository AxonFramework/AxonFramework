/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Set;

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

    private final Object target;
    private final AggregateModel<Object> modelInspector;

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
    @SuppressWarnings("unchecked")
    public AnnotationCommandHandlerAdapter(Object annotatedCommandHandler,
                                           ParameterResolverFactory parameterResolverFactory) {
        Assert.notNull(annotatedCommandHandler, "annotatedCommandHandler may not be null");
        this.modelInspector = ModelInspector.inspectAggregate((Class<Object>)annotatedCommandHandler.getClass(),
                                                              parameterResolverFactory);

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
        return () -> subscriptions.stream().map(Registration::cancel).reduce(Boolean::logicalOr).orElse(false);
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
    public Object handle(CommandMessage<?> command, UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
        return modelInspector.commandHandler(command.getCommandName()).handle(command, target);
    }

    @Override
    public Set<String> supportedCommandNames() {
        return modelInspector.commandHandlers().keySet();
    }
}
