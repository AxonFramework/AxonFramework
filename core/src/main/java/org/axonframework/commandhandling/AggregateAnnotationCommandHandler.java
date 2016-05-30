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

import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.*;

/**
 * Command handler that handles commands based on {@link CommandHandler}
 * annotations on an aggregate. Those annotations may appear on methods, in which case a specific aggregate instance
 * needs to be targeted by the command, or on the constructor. The latter will create a new Aggregate instance, which
 * is then stored in the repository.
 *
 * @param <T> the type of aggregate this handler handles commands for
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateAnnotationCommandHandler<T> implements MessageHandler<CommandMessage<?>>,
        SupportedCommandNamesAware {

    private final Repository<T> repository;

    private final CommandTargetResolver commandTargetResolver;
    private final Map<String, MessageHandler<CommandMessage<?>>> handlers;

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, using the
     * given <code>repository</code> to add and load aggregate instances.
     *
     * @param aggregateType The type of aggregate
     * @param repository    The repository providing access to aggregate instances
     */
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository) {
        this(aggregateType, repository, new AnnotationCommandTargetResolver());
    }

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, using the
     * given <code>repository</code> to add and load aggregate instances and the default ParameterResolverFactory.
     *
     * @param aggregateType         The type of aggregate
     * @param repository            The repository providing access to aggregate instances
     * @param commandTargetResolver The target resolution strategy
     */
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository,
                                             CommandTargetResolver commandTargetResolver) {
        this(aggregateType, repository, commandTargetResolver,
                ClasspathParameterResolverFactory.forClass(aggregateType));
    }

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, using the
     * given <code>repository</code> to add and load aggregate instances and the given
     * <code>parameterResolverFactory</code>.
     *
     * @param aggregateType            The type of aggregate
     * @param repository               The repository providing access to aggregate instances
     * @param commandTargetResolver    The target resolution strategy
     * @param parameterResolverFactory The strategy for resolving parameter values for handler methods
     */
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository,
                                             CommandTargetResolver commandTargetResolver,
                                             ParameterResolverFactory parameterResolverFactory) {
        this(repository, commandTargetResolver,
                ModelInspector.inspectAggregate(aggregateType, parameterResolverFactory));
    }

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, using the
     * given <code>repository</code> to add and load aggregate instances and the given
     * <code>parameterResolverFactory</code>.
     *
     * @param repository            The repository providing access to aggregate instances
     * @param commandTargetResolver The target resolution strategy
     * @param aggregateModel        The description of the command handling model
     */
    public AggregateAnnotationCommandHandler(Repository<T> repository,
                                             CommandTargetResolver commandTargetResolver,
                                             AggregateModel<T> aggregateModel) {
        Assert.notNull(aggregateModel, "aggregateModel may not be null");
        Assert.notNull(repository, "repository may not be null");
        Assert.notNull(commandTargetResolver, "commandTargetResolver may not be null");
        this.repository = repository;
        this.commandTargetResolver = commandTargetResolver;
        this.handlers = initializeHandlers(aggregateModel);
    }

    /**
     * Subscribe this command handler to the given <code>commandBus</code>. The command handler will be subscribed
     * for each of the supported commands.
     *
     * @param commandBus The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Registration subscribe(CommandBus commandBus) {
        Collection<Registration> subscriptions = new ArrayList<>();
        for (String supportedCommand : supportedCommandNames()) {
            Registration subscription = commandBus.subscribe(supportedCommand, this);
            if (subscription != null) {
                subscriptions.add(subscription);
            }
        }
        return () -> {
            subscriptions.forEach(Registration::cancel);
            return true;
        };
    }

    private Map<String, MessageHandler<CommandMessage<?>>> initializeHandlers(AggregateModel<T> aggregateModel) {
        Map<String, MessageHandler<CommandMessage<?>>> handlersFound = new HashMap<>();
        AggregateCommandHandler aggregateCommandHandler = new AggregateCommandHandler();
        aggregateModel.commandHandlers().forEach((k, v) -> {
            if (v.isFactoryHandler()) {
                handlersFound.put(k, new AggregateConstructorCommandHandler(v));
            } else {
                handlersFound.put(k, aggregateCommandHandler);
            }
        });
        return handlersFound;
    }

    @Override
    public Object handle(CommandMessage<?> commandMessage, UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
        return handlers.get(commandMessage.getCommandName()).handle(commandMessage, unitOfWork);
    }

    /**
     * Resolves the value to return when the given <code>command</code> has created the given <code>aggregate</code>.
     * This implementation returns the identifier of the created aggregate.
     * <p>
     * This method may be overridden to change the return value of this Command Handler
     *
     * @param command          The command being executed
     * @param createdAggregate The aggregate that has been created as a result of the command
     * @return The value to report as result of the command
     */
    protected Object resolveReturnValue(CommandMessage<?> command, Aggregate<T> createdAggregate) {
        return createdAggregate.identifier();
    }

    @Override
    public Set<String> supportedCommandNames() {
        return handlers.keySet();
    }

    private class AggregateConstructorCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final org.axonframework.common.annotation.MessageHandler<?> handler;

        public AggregateConstructorCommandHandler(org.axonframework.common.annotation.MessageHandler<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object handle(CommandMessage<?> command, UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
            Aggregate<T> aggregate = repository.newInstance(() -> (T) handler.handle(command, null));
            return resolveReturnValue(command, aggregate);
        }
    }

    private class AggregateCommandHandler implements MessageHandler<CommandMessage<?>> {

        @SuppressWarnings("unchecked")
        @Override
        public Object handle(CommandMessage<?> command, UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
            VersionedAggregateIdentifier iv = commandTargetResolver.resolveTarget(command);
            return repository.load(iv.getIdentifier(), iv.getVersion()).handle(command);
        }
    }
}
