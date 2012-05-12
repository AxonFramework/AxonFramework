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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.DefaultInterceptorChain;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Component of the DisruptorCommandBus that looks up the command handler and prepares the data for the command's
 * execution. It loads the targeted aggregate and prepares the interceptor chain.
 *
 * @param <T> The type of aggregate being handled
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlerPreFetcher<T extends EventSourcedAggregateRoot>
        implements EventHandler<CommandHandlingEntry<T>> {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerPreFetcher.class);
    private static final Object PLACEHOLDER = new Object();

    private final Map<T, Object> preLoadedAggregates = new WeakHashMap<T, Object>();
    private final EventStore eventStore;
    private final AggregateFactory<T> aggregateFactory;
    private final Map<Class<?>, CommandHandler<?>> commandHandlers;
    private final List<CommandHandlerInterceptor> invokerInterceptors;
    private final List<CommandHandlerInterceptor> publisherInterceptors;
    private final CommandTargetResolver commandTargetResolver;

    /**
     * Initialize the CommandHandlerPreFetcher using the given resources.
     *
     * @param eventStore            The EventStore providing the events to reconstruct the targeted aggregate
     * @param aggregateFactory      The factory creating empty aggregate instances
     * @param commandHandlers       The command handlers for command processing
     * @param invokerInterceptors   The command handler interceptors to be invoked during command handler invocation
     * @param publisherInterceptors The command handler interceptors to be invoked during event publication
     * @param commandTargetResolver The instance that resolves the aggregate identifier for each incoming command
     */
    CommandHandlerPreFetcher(EventStore eventStore, AggregateFactory<T> aggregateFactory,
                             Map<Class<?>, CommandHandler<?>> commandHandlers,
                             List<CommandHandlerInterceptor> invokerInterceptors,
                             List<CommandHandlerInterceptor> publisherInterceptors,
                             CommandTargetResolver commandTargetResolver) {
        this.eventStore = eventStore;
        this.aggregateFactory = aggregateFactory;
        this.commandHandlers = commandHandlers;
        this.invokerInterceptors = invokerInterceptors;
        this.publisherInterceptors = publisherInterceptors;
        this.commandTargetResolver = commandTargetResolver;
    }

    @Override
    public void onEvent(CommandHandlingEntry<T> entry, long sequence, boolean endOfBatch) throws Exception {
        if (entry.isRecoverEntry()) {
            removeEntry(entry.getAggregateIdentifier());
        } else {
            preLoadAggregate(entry);
            resolveCommandHandler(entry);
            prepareInterceptorChain(entry);
            // make sure that any lazy initializing messages are initialized by now
            entry.getCommand().getPayload();
        }
    }

    private void removeEntry(Object aggregateIdentifier) {
        for (T entry : preLoadedAggregates.keySet()) {
            if (aggregateIdentifier.equals(entry.getIdentifier())) {
                preLoadedAggregates.remove(entry);
                return;
            }
        }
    }

    private void prepareInterceptorChain(CommandHandlingEntry<T> entry) {
        entry.setInvocationInterceptorChain(new DefaultInterceptorChain(entry.getCommand(),
                                                                        entry.getUnitOfWork(),
                                                                        entry.getCommandHandler(),
                                                                        invokerInterceptors));
        entry.setPublisherInterceptorChain(new DefaultInterceptorChain(entry.getCommand(),
                                                                       entry.getUnitOfWork(),
                                                                       new RepeatingCommandHandler<T>(entry),
                                                                       publisherInterceptors));
    }

    private void preLoadAggregate(CommandHandlingEntry<T> entry) {
        Object aggregateIdentifier = commandTargetResolver.resolveTarget(entry.getCommand()).getIdentifier();
        entry.setAggregateIdentifier(aggregateIdentifier);
        T foundAggregate = findPreLoadedAggregate(aggregateIdentifier);
        if (foundAggregate != null) {
            entry.setPreLoadedAggregate(foundAggregate);
        } else {
            try {
                DomainEventStream events = eventStore.readEvents(aggregateFactory.getTypeIdentifier(),
                                                                 aggregateIdentifier);
                if (events.hasNext()) {
                    T aggregateRoot = aggregateFactory.createAggregate(aggregateIdentifier, events.peek());
                    aggregateRoot.initializeState(aggregateIdentifier, events);
                    preLoadedAggregates.put(aggregateRoot, PLACEHOLDER);
                    entry.setPreLoadedAggregate(aggregateRoot);
                }
            } catch (AggregateNotFoundException e) {
                logger.info("Aggregate to pre-load not found. Possibly involves an aggregate being created, "
                                    + "or a command that was executed against an aggregate that did not yet"
                                    + "finish the creation process. It will be rescheduled for publication when it "
                                    + "attempts to load an aggregate");
            }
        }
    }

    private T findPreLoadedAggregate(Object aggregateIdentifier) {
        for (T available : preLoadedAggregates.keySet()) {
            if (aggregateIdentifier.equals(available.getIdentifier())) {
                return available;
            }
        }
        return null;
    }

    private void resolveCommandHandler(CommandHandlingEntry<T> entry) {
        entry.setCommandHandler(commandHandlers.get(entry.getCommand().getPayloadType()));
        entry.setUnitOfWork(new DisruptorUnitOfWork(entry.getPreLoadedAggregate()));
    }

    private static class RepeatingCommandHandler<T extends EventSourcedAggregateRoot>
            implements CommandHandler<Object> {

        private final CommandHandlingEntry<T> entry;

        public RepeatingCommandHandler(CommandHandlingEntry<T> entry) {
            this.entry = entry;
        }

        @Override
        public Object handle(CommandMessage<Object> commandMessage, UnitOfWork unitOfWork) throws Throwable {
            Throwable exceptionResult = entry.getExceptionResult();
            if (exceptionResult != null) {
                throw exceptionResult;
            }
            return entry.getResult();
        }
    }
}
