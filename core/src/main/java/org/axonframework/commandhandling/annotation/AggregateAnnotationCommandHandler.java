/*
 * Copyright (c) 2010-2011. Axon Framework
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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.VersionedAggregateIdentifier;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.util.Handler;
import org.axonframework.util.Subscribable;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Command handler that handles commands based on {@link org.axonframework.commandhandling.annotation.CommandHandler}
 * annotations on an aggregate. Those annotations may appear on methods, in which case a specific aggregate instance
 * needs to be targeted by the command (see {@link org.axonframework.domain.AggregateIdentifier}), or on the
 * constructor. The latter will create a new Aggregate instance, which is then stored in the repository.
 *
 * @param <T> the type of aggregate this handler handles commands for
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateAnnotationCommandHandler<T extends AggregateRoot> implements Subscribable {

    private final CommandBus commandBus;
    private final AggregateCommandHandlerInspector<T> inspector;
    private final Repository<T> repository;

    private final Map<Class, CommandHandler> registeredCommandHandlers = new HashMap<Class, CommandHandler>();
    private final CommandTargetResolver commandTargetResolver;

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, to be
     * registered on the given <code>commandBus</code>.
     *
     * @param aggregateType The type of aggregate
     * @param repository    The repository providing access to aggregate instances
     * @param commandBus    The command bus to register command handlers to
     */
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository, CommandBus commandBus) {
        this(aggregateType, repository, commandBus, new AnnotationCommandTargetResolver());
    }

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, to be
     * registered on the given <code>commandBus</code>.
     *
     * @param aggregateType         The type of aggregate
     * @param repository            The repository providing access to aggregate instances
     * @param commandBus            The command bus to register command handlers to
     * @param commandTargetResolver The target resolution strategy
     */
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository, CommandBus commandBus,
                                             CommandTargetResolver commandTargetResolver) {
        this.repository = repository;
        this.commandBus = commandBus;
        this.commandTargetResolver = commandTargetResolver;
        this.inspector = new AggregateCommandHandlerInspector<T>(aggregateType);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    @PreDestroy
    public synchronized void unsubscribe() {
        for (Map.Entry<Class, CommandHandler> handlerEntry : registeredCommandHandlers.entrySet()) {
            commandBus.unsubscribe(handlerEntry.getKey(), handlerEntry.getValue());
        }
    }

    @PostConstruct
    @Override
    public synchronized void subscribe() {
        for (final Handler commandHandler : inspector.getHandlers()) {
            CommandHandler<Object> handler = new CommandHandler<Object>() {
                @Override
                public Object handle(Object command, UnitOfWork unitOfWork) throws Throwable {
                    T aggregate = loadAggregate(command);
                    return commandHandler.invoke(aggregate, command, unitOfWork);
                }
            };
            commandBus.subscribe(commandHandler.getParameterType(), handler);
            registeredCommandHandlers.put(commandHandler.getParameterType(), handler);
        }

        for (final ConstructorCommandHandler<T> handler : inspector.getConstructorHandlers()) {
            commandBus.subscribe(handler.getCommandType(), new AnnotatedConstructorCommandHandler(handler));
        }
    }

    private T loadAggregate(Object command) {
        VersionedAggregateIdentifier iv = commandTargetResolver.resolveTarget(command);
        return repository.load(iv.getIdentifier(), iv.getVersion());
    }

    private class AnnotatedConstructorCommandHandler implements CommandHandler<Object> {
        private final ConstructorCommandHandler<T> handler;

        public AnnotatedConstructorCommandHandler(ConstructorCommandHandler<T> handler) {
            this.handler = handler;
        }

        @Override
        public Object handle(Object command, UnitOfWork unitOfWork) throws Throwable {
            repository.add(handler.invoke(command, unitOfWork));
            return Void.TYPE;
        }
    }
}
