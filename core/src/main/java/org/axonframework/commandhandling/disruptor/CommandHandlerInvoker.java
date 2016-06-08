/*
 * Copyright (c) 2010-2016. Axon Framework
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
import com.lmax.disruptor.LifecycleAware;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateNotFoundException;
import org.axonframework.commandhandling.model.ConflictingAggregateVersionException;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.Assert;
import org.axonframework.common.caching.Cache;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventStreamDecorator;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component of the DisruptorCommandBus that invokes the command handler. The execution is done within a Unit Of Work.
 * If an aggregate has been pre-loaded, it is set to the ThreadLocal.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlerInvoker implements EventHandler<CommandHandlingEntry>, LifecycleAware {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerInvoker.class);
    private static final ThreadLocal<CommandHandlerInvoker> CURRENT_INVOKER = new ThreadLocal<>();
    private static final Object PLACEHOLDER_VALUE = new Object();

    private final Map<Class<?>, DisruptorRepository> repositories = new ConcurrentHashMap<>();
    private final Cache cache;
    private final int segmentId;
    private final EventStore eventStore;

    /**
     * Create an aggregate invoker instance that uses the given <code>eventStore</code> and <code>cache</code> to
     * retrieve aggregate instances.
     *
     * @param eventStore The event store providing access to events to reconstruct aggregates
     * @param cache      The cache temporarily storing aggregate instances
     * @param segmentId  The id of the segment this invoker should handle
     */
    public CommandHandlerInvoker(EventStore eventStore, Cache cache, int segmentId) {
        this.eventStore = eventStore;
        this.cache = cache;
        this.segmentId = segmentId;
    }

    /**
     * Returns the Repository instance for Aggregate with given <code>typeIdentifier</code> used by the
     * CommandHandlerInvoker that is running on the current thread.
     * <p>
     * Calling this method from any other thread will return <code>null</code>.
     *
     * @param type The type of aggregate
     * @param <T>  The type of aggregate
     * @return the repository instance for aggregate of given type
     */
    @SuppressWarnings("unchecked")
    public static <T> DisruptorRepository<T> getRepository(Class<?> type) {
        final CommandHandlerInvoker invoker = CURRENT_INVOKER.get();
        Assert.state(invoker != null, "The repositories of a DisruptorCommandBus are only available "
                + "in the invoker thread");
        return invoker.repositories.get(type);
    }

    @Override
    public void onEvent(CommandHandlingEntry entry, long sequence, boolean endOfBatch) throws Exception {
        if (entry.isRecoverEntry()) {
            removeEntry(entry.getAggregateIdentifier());
        } else if (entry.getInvokerId() == segmentId) {
            entry.start();
            try {
                Object result = entry.getInvocationInterceptorChain().proceed();
                entry.setResult(result);
            } catch (Exception throwable) {
                entry.setExceptionResult(throwable);
            } finally {
                entry.pause();
            }
        }
    }

    /**
     * Create a repository instance for an aggregate created by the given <code>aggregateFactory</code>. The returning
     * repository must be sage to use by this invoker instance.
     *
     * @param aggregateFactory The factory creating aggregate instances
     * @param decorator        The decorator to decorate event streams with
     * @param <T>              The type of aggregate created by the factory
     * @return A Repository instance for the given aggregate
     */
    @SuppressWarnings("unchecked")
    public <T> Repository<T> createRepository(AggregateFactory<T> aggregateFactory,
                                              EventStreamDecorator decorator) {
        return repositories.computeIfAbsent(aggregateFactory.getAggregateType(),
                                            k -> new DisruptorRepository<>(aggregateFactory, cache,
                                                                           eventStore, decorator));
    }

    private void removeEntry(String aggregateIdentifier) {
        for (DisruptorRepository repository : repositories.values()) {
            repository.removeFromCache(aggregateIdentifier);
        }
        cache.remove(aggregateIdentifier);
    }

    @Override
    public void onStart() {
        CURRENT_INVOKER.set(this);
    }

    @Override
    public void onShutdown() {
        CURRENT_INVOKER.remove();
    }

    /**
     * Repository implementation that is safe to use by a single CommandHandlerInvoker instance.
     *
     * @param <T> The type of aggregate stored in this repository
     */
    static final class DisruptorRepository<T> implements Repository<T> {

        private final EventStore eventStore;
        private final EventStreamDecorator decorator;
        private final AggregateFactory<T> aggregateFactory;
        private final Map<EventSourcedAggregate<T>, Object> firstLevelCache = new WeakHashMap<>();
        private final Cache cache;
        private final AggregateModel<T> model;

        private DisruptorRepository(AggregateFactory<T> aggregateFactory, Cache cache, EventStore eventStore,
                                    EventStreamDecorator decorator) {
            this.aggregateFactory = aggregateFactory;
            this.cache = cache;
            this.eventStore = eventStore;
            this.decorator = decorator;
            this.model = ModelInspector.inspectAggregate(aggregateFactory.getAggregateType());
        }

        @Override
        public Aggregate<T> load(String aggregateIdentifier, Long expectedVersion) {
            ((CommandHandlingEntry)CurrentUnitOfWork.get()).registerAggregateIdentifier(aggregateIdentifier);
            Aggregate<T> aggregate = load(aggregateIdentifier);
            if (expectedVersion != null && aggregate.version() > expectedVersion) {
                throw new ConflictingAggregateVersionException(aggregateIdentifier,
                                                               expectedVersion,
                                                               aggregate.version());
            }
            return aggregate;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Aggregate<T> load(String aggregateIdentifier) {
            ((CommandHandlingEntry)CurrentUnitOfWork.get()).registerAggregateIdentifier(aggregateIdentifier);
            EventSourcedAggregate<T> aggregateRoot = null;
            for (EventSourcedAggregate<T> cachedAggregate : firstLevelCache.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.identifier())) {
                    logger.debug("Aggregate {} found in first level cache", aggregateIdentifier);
                    aggregateRoot = cachedAggregate;
                }
            }
            if (aggregateRoot == null) {
                Object cachedItem = cache.get(aggregateIdentifier);
                if (cachedItem != null && EventSourcedAggregate.class.isInstance(cachedItem)) {
                    EventSourcedAggregate<T> cachedAggregate = (EventSourcedAggregate<T>) cachedItem;
                    aggregateRoot = cachedAggregate.invoke(r -> {
                        if (aggregateFactory.getAggregateType().isInstance(r)) {
                            return cachedAggregate;
                        } else {
                            return null;
                        }
                    });
                }
            }
            if (aggregateRoot == null) {
                logger.debug("Aggregate {} not in first level cache, loading fresh one from Event Store",
                             aggregateIdentifier);
                DomainEventStream eventStream = eventStore.readEvents(aggregateIdentifier);
                eventStream = decorator.decorateForRead(aggregateIdentifier, eventStream);
                if (!eventStream.hasNext()) {
                    throw new AggregateNotFoundException(aggregateIdentifier,
                                                         "The aggregate was not found in the event store");
                }
                aggregateRoot = EventSourcedAggregate
                        .initialize(aggregateFactory.createAggregateRoot(aggregateIdentifier, eventStream.peek()),
                                    model, eventStore);
                aggregateRoot.initializeState(eventStream);
                firstLevelCache.put(aggregateRoot, PLACEHOLDER_VALUE);
                cache.put(aggregateIdentifier, aggregateRoot);
            }
            return aggregateRoot;
        }

        @Override
        public Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception {
            EventSourcedAggregate<T> aggregate = EventSourcedAggregate.initialize(factoryMethod, model, eventStore);
            firstLevelCache.put(aggregate, PLACEHOLDER_VALUE);
            cache.put(aggregate.identifier(), aggregate);
            return aggregate;
        }

        private void removeFromCache(String aggregateIdentifier) {
            for (EventSourcedAggregate<T> cachedAggregate : firstLevelCache.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.identifier())) {
                    firstLevelCache.remove(cachedAggregate);
                    logger.debug("Aggregate {} removed from first level cache for recovery purposes.",
                                 aggregateIdentifier);
                    return;
                }
            }
        }
    }
}
