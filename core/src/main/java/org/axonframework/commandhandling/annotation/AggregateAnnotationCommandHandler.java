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

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.common.Assert;
import org.axonframework.common.Subscription;
import org.axonframework.common.annotation.AbstractMessageHandler;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.repository.Repository;

import java.util.*;

import static org.axonframework.commandhandling.annotation.CommandMessageHandlerUtils.resolveAcceptedCommandName;

/**
 * Command handler that handles commands based on {@link org.axonframework.commandhandling.annotation.CommandHandler}
 * annotations on an aggregate. Those annotations may appear on methods, in which case a specific aggregate instance
 * needs to be targeted by the command, or on the constructor. The latter will create a new Aggregate instance, which
 * is then stored in the repository.
 *
 * @param <T> the type of aggregate this handler handles commands for
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateAnnotationCommandHandler<T extends AggregateRoot> implements CommandHandler<Object>,
                                                                                   SupportedCommandNamesAware {

    private final Repository<T> repository;

    private final CommandTargetResolver commandTargetResolver;
    private final Map<String, CommandHandler<Object>> handlers;
    private final ParameterResolverFactory parameterResolverFactory;

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
        this.parameterResolverFactory = parameterResolverFactory;
        Assert.notNull(aggregateType, "aggregateType may not be null");
        Assert.notNull(repository, "repository may not be null");
        Assert.notNull(commandTargetResolver, "commandTargetResolver may not be null");
        this.repository = repository;
        this.commandTargetResolver = commandTargetResolver;
        this.handlers = initializeHandlers(new AggregateCommandHandlerInspector<>(aggregateType,
                                                                                   parameterResolverFactory));
    }

    /**
     * Subscribe this command handler to the given <code>commandBus</code>. The command handler will be subscribed
     * for each of the supported commands.
     *
     * @param commandBus    The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Subscription subscribe(CommandBus commandBus) {
        Collection<Subscription> subscriptions = new ArrayList<>();
        for (String supportedCommand : supportedCommandNames()) {
            Subscription subscription = commandBus.subscribe(supportedCommand, this);
            if (subscription != null) {
                subscriptions.add(subscription);
            }
        }
        return () -> {
            subscriptions.forEach(Subscription::stop);
            return true;
        };
    }

    private Map<String, CommandHandler<Object>> initializeHandlers(AggregateCommandHandlerInspector<T> inspector) {
        Map<String, CommandHandler<Object>> handlersFound = new HashMap<>();
        for (final AbstractMessageHandler commandHandler : inspector.getHandlers()) {
            handlersFound.put(resolveAcceptedCommandName(commandHandler), new AggregateCommandHandler(commandHandler));
        }
        for (final ConstructorCommandMessageHandler<T> handler : inspector.getConstructorHandlers()) {
            handlersFound.put(resolveAcceptedCommandName(handler), new AggregateConstructorCommandHandler(handler));
        }
        return handlersFound;
    }

    @Override
    public Object handle(CommandMessage<Object> commandMessage, UnitOfWork unitOfWork) throws Exception {
        unitOfWork.resources().put(ParameterResolverFactory.class.getName(), parameterResolverFactory);
        return handlers.get(commandMessage.getCommandName()).handle(commandMessage, unitOfWork);
    }

    private T loadAggregate(CommandMessage<?> command) {
        VersionedAggregateIdentifier iv = commandTargetResolver.resolveTarget(command);
        return repository.load(iv.getIdentifier(), iv.getVersion());
    }

    /**
     * Resolves the value to return when the given <code>command</code> has created the given <code>aggregate</code>.
     * This implementation returns the identifier of the created aggregate.
     * <p/>
     * This method may be overridden to change the return value of this Command Handler
     *
     * @param command          The command being executed
     * @param createdAggregate The aggregate that has been created as a result of the command
     * @return The value to report as result of the command
     */
    protected Object resolveReturnValue(CommandMessage<?> command, T createdAggregate) {
        return createdAggregate.getIdentifier();
    }

    @Override
    public Set<String> supportedCommandNames() {
        return handlers.keySet();
    }

    private class AggregateConstructorCommandHandler implements CommandHandler<Object> {

        private final ConstructorCommandMessageHandler<T> handler;

        public AggregateConstructorCommandHandler(ConstructorCommandMessageHandler<T> handler) {
            this.handler = handler;
        }

        @Override
        public Object handle(CommandMessage<Object> command, UnitOfWork unitOfWork) throws Exception {
            final T createdAggregate = handler.invoke(null, command);
            repository.add(createdAggregate);
            return resolveReturnValue(command, createdAggregate);
        }
    }

    private class AggregateCommandHandler implements CommandHandler<Object> {

        private final AbstractMessageHandler commandHandler;

        public AggregateCommandHandler(AbstractMessageHandler commandHandler) {
            this.commandHandler = commandHandler;
        }

        @Override
        public Object handle(CommandMessage<Object> command, UnitOfWork unitOfWork) {
            T aggregate = loadAggregate(command);
            return commandHandler.invoke(aggregate, command);
        }
    }
}
