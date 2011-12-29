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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.EventHandler;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.DefaultInterceptorChain;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Component of the DisruptorCommandBus that looks up the command handler and prepares the data for the command's
 * execution. It loads the targeted aggregate and prepares the interceptor chain.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlerPreFetcher<T extends EventSourcedAggregateRoot> implements EventHandler<CommandHandlingEntry<T>> {

    private final Map<Object, T> preLoadedAggregates = new HashMap<Object, T>();
    private final EventStore eventStore;
    private final AggregateFactory<T> aggregateFactory;
    private final Map<Class<?>, CommandHandler<?>> commandHandlers;
    private final List<CommandHandlerInterceptor> interceptors;

    /**
     * Initialize the CommandHandlerPreFetcher using the given resources.
     *
     * @param eventStore       The EventStore providing the events to reconstruct the targeted aggregate
     * @param aggregateFactory The factory creating empty aggregate instances
     * @param commandHandlers  The command handlers for command processing
     * @param interceptors     The command handler interceptors
     */
    CommandHandlerPreFetcher(EventStore eventStore, AggregateFactory<T> aggregateFactory,
                             Map<Class<?>, CommandHandler<?>> commandHandlers,
                             List<CommandHandlerInterceptor> interceptors) {
        this.eventStore = eventStore;
        this.aggregateFactory = aggregateFactory;
        this.commandHandlers = commandHandlers;
        this.interceptors = interceptors;
    }

    @Override
    public void onEvent(CommandHandlingEntry<T> entry, long sequence, boolean endOfBatch) throws Exception {
        preLoadAggregate(entry);
        resolveCommandHandler(entry);
        prepareInterceptorChain(entry);
    }

    private void prepareInterceptorChain(CommandHandlingEntry<T> entry) {
        entry.setInterceptorChain(new DefaultInterceptorChain(entry.getCommand(),
                                                              entry.getUnitOfWork(),
                                                              entry.getCommandHandler(),
                                                              interceptors));
    }

    private void preLoadAggregate(CommandHandlingEntry<T> entry) {
        final Object aggregateIdentifier = entry.getAggregateIdentifier();
        if (preLoadedAggregates.containsKey(aggregateIdentifier)) {
            entry.setPreLoadedAggregate(preLoadedAggregates.get(aggregateIdentifier));
        } else {
            DomainEventStream events = eventStore.readEvents(aggregateFactory.getTypeIdentifier(), aggregateIdentifier);
            T aggregateRoot = aggregateFactory.createAggregate(aggregateIdentifier, events.peek());
            aggregateRoot.initializeState(events);
            preLoadedAggregates.put(aggregateIdentifier, aggregateRoot);
            entry.setPreLoadedAggregate(aggregateRoot);
        }
    }

    private void resolveCommandHandler(CommandHandlingEntry<T> entry) {
        entry.setCommandHandler(commandHandlers.get(entry.getCommand().getPayloadType()));
        entry.setUnitOfWork(new DisruptorUnitOfWork(entry.getPreLoadedAggregate()));
    }
}
