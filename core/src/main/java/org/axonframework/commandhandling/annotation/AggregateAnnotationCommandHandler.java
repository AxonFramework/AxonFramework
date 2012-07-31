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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.VersionedAggregateIdentifier;
import org.axonframework.common.Subscribable;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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
public class AggregateAnnotationCommandHandler<T extends AggregateRoot> implements Subscribable {

    private final CommandBus commandBus;
    private final AggregateCommandHandlerInspector<T> inspector;
    private final Repository<T> repository;

    private final Map<Class<Object>, CommandHandler<Object>> registeredCommandHandlers =
            new HashMap<Class<Object>, CommandHandler<Object>>();
    private final CommandTargetResolver commandTargetResolver;

    /**
     * Subscribe a handler for the given aggregate type to the given command bus.
     *
     * @param aggregateType The type of aggregate
     * @param repository    The repository providing access to aggregate instances
     * @param commandBus    The command bus to register command handlers to
     * @param <T>           The type of aggregate this handler handles commands for
     * @return the Adapter created for the command handler target. Can be used to unsubscribe.
     */
    public static <T extends AggregateRoot> AggregateAnnotationCommandHandler subscribe(
            Class<T> aggregateType, Repository<T> repository, CommandBus commandBus) {
        AggregateAnnotationCommandHandler adapter = new AggregateAnnotationCommandHandler<T>(aggregateType,
                                                                                             repository,
                                                                                             commandBus);
        adapter.subscribe();
        return adapter;
    }

    /**
     * Subscribe a handler for the given aggregate type to the given command bus.
     *
     * @param aggregateType         The type of aggregate
     * @param repository            The repository providing access to aggregate instances
     * @param commandBus            The command bus to register command handlers to
     * @param commandTargetResolver The target resolution strategy
     * @param <T>                   The type of aggregate this handler handles commands for
     * @return the Adapter created for the command handler target. Can be used to unsubscribe.
     */
    public static <T extends AggregateRoot> AggregateAnnotationCommandHandler subscribe(
            Class<T> aggregateType, Repository<T> repository, CommandBus commandBus,
            CommandTargetResolver commandTargetResolver) {
        AggregateAnnotationCommandHandler adapter = new AggregateAnnotationCommandHandler<T>(
                aggregateType, repository, commandBus, commandTargetResolver);
        adapter.subscribe();
        return adapter;
    }

    /**
     * Initializes an AnnotationCommandHandler based on the annotations on given <code>aggregateType</code>, to be
     * registered on the given <code>commandBus</code>.
     *
     * @param aggregateType The type of aggregate
     * @param repository    The repository providing access to aggregate instances
     * @param commandBus    The command bus to register command handlers to
     */
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository,
                                             CommandBus commandBus) {
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
    public AggregateAnnotationCommandHandler(Class<T> aggregateType, Repository<T> repository,
                                             CommandBus commandBus, CommandTargetResolver commandTargetResolver) {
        this.repository = repository;
        this.commandBus = commandBus;
        this.commandTargetResolver = commandTargetResolver;
        this.inspector = new AggregateCommandHandlerInspector<T>(aggregateType);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    @PreDestroy
    public synchronized void unsubscribe() {
        for (Map.Entry<Class<Object>, CommandHandler<Object>> handlerEntry : registeredCommandHandlers.entrySet()) {
            commandBus.unsubscribe(handlerEntry.getKey(), handlerEntry.getValue());
        }
    }

    @PostConstruct
    @SuppressWarnings({"unchecked"})
    @Override
    public synchronized void subscribe() {
        for (final MethodMessageHandler commandHandler : inspector.getHandlers()) {
            CommandHandler<Object> handler = new CommandHandler<Object>() {
                @Override
                public Object handle(CommandMessage<Object> command, UnitOfWork unitOfWork) throws Throwable {
                    T aggregate = loadAggregate(command);
                    try {
                    return commandHandler.invoke(aggregate, command);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            };
            commandBus.subscribe(commandHandler.getPayloadType(), handler);
            registeredCommandHandlers.put(commandHandler.getPayloadType(), handler);
        }

        for (final ConstructorCommandMessageHandler<T> handler : inspector.getConstructorHandlers()) {
            commandBus.subscribe(handler.getPayloadType(), new AnnotatedConstructorCommandHandler(handler));
        }
    }

    private T loadAggregate(CommandMessage<?> command) {
        VersionedAggregateIdentifier iv = commandTargetResolver.resolveTarget(command);
        return repository.load(iv.getIdentifier(), iv.getVersion());
    }

    private class AnnotatedConstructorCommandHandler implements CommandHandler<Object> {

        private final ConstructorCommandMessageHandler<T> handler;

        public AnnotatedConstructorCommandHandler(ConstructorCommandMessageHandler<T> handler) {
            this.handler = handler;
        }

        @Override
        public Object handle(CommandMessage<Object> command, UnitOfWork unitOfWork) throws Throwable {
            try {
	            repository.add(handler.invoke(null, command));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            return null;        }
    }
}
