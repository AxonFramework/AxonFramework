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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import org.axonframework.cache.Cache;
import org.axonframework.common.Assert;
import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventStreamDecorator;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.repository.ConflictingAggregateVersionException;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private final ConcurrentMap<Class<?>, DisruptorRepository> repositories = new ConcurrentHashMap<>();
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
     * <p/>
     * Calling this method from any other thread will return <code>null</code>.
     *
     * @param type The type of aggregate
     * @param <T>  The type of aggregate
     * @return the repository instance for aggregate of given type
     */
    @SuppressWarnings("unchecked")
    public static <T extends EventSourcedAggregateRoot> DisruptorRepository<T> getRepository(Class<?> type) {
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
                Object result = entry.getInvocationInterceptorChain().proceed(entry.getCommand());
                entry.setResult(result);
            } catch (Throwable throwable) {
                entry.setExceptionResult(throwable);
            } finally {
                EventSourcedAggregateRoot aggregateRoot = entry.getResource("AggregateRoot");
                if (aggregateRoot != null) {
                    entry.publishMessages(aggregateRoot.getUncommittedEvents());
                    aggregateRoot.commitEvents();
                }
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
    public <T extends EventSourcedAggregateRoot> Repository<T> createRepository(AggregateFactory<T> aggregateFactory,
                                                                                EventStreamDecorator decorator) {
        Class<T> typeIdentifier = aggregateFactory.getAggregateType();
        if (!repositories.containsKey(typeIdentifier)) {
            DisruptorRepository<T> repository = new DisruptorRepository<>(aggregateFactory, cache, eventStore,
                                                                          decorator);
            repositories.putIfAbsent(typeIdentifier, repository);
        }
        return repositories.get(typeIdentifier);
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
    static final class DisruptorRepository<T extends EventSourcedAggregateRoot> implements Repository<T> {

        private final EventStore eventStore;
        private final EventStreamDecorator decorator;
        private final AggregateFactory<T> aggregateFactory;
        private final Map<T, Object> firstLevelCache = new WeakHashMap<>();
        private final Cache cache;

        private DisruptorRepository(AggregateFactory<T> aggregateFactory, Cache cache, EventStore eventStore,
                                    EventStreamDecorator decorator) {
            this.aggregateFactory = aggregateFactory;
            this.cache = cache;
            this.eventStore = eventStore;
            this.decorator = decorator;
        }

        @Override
        public T load(String aggregateIdentifier, Long expectedVersion) {
            T aggregate = load(aggregateIdentifier);
            if (expectedVersion != null && aggregate.getVersion() > expectedVersion) {
                throw new ConflictingAggregateVersionException(aggregateIdentifier,
                                                               expectedVersion,
                                                               aggregate.getVersion());
            }
            return aggregate;
        }

        @Override
        public T load(String aggregateIdentifier) {
            T aggregateRoot = null;
            for (T cachedAggregate : firstLevelCache.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.getIdentifier())) {
                    logger.debug("Aggregate {} found in first level cache", aggregateIdentifier);
                    aggregateRoot = cachedAggregate;
                }
            }
            if (aggregateRoot == null) {
                String cachedItem = cache.get(aggregateIdentifier);
                if (cachedItem != null && aggregateFactory.getAggregateType().isInstance(cachedItem)) {
                    aggregateRoot = aggregateFactory.getAggregateType().cast(cachedItem);
                }
            }
            if (aggregateRoot == null) {
                logger.debug("Aggregate {} not in first level cache, loading fresh one from Event Store",
                             aggregateIdentifier);
                DomainEventStream events = null;
                try {
                    events = decorator.decorateForRead(aggregateIdentifier,
                                                       eventStore.readEvents(aggregateIdentifier));
                    if (events.hasNext()) {
                        aggregateRoot = aggregateFactory.createAggregate(aggregateIdentifier, events.peek());
                        aggregateRoot.initializeState(events);
                    }
                } catch (EventStreamNotFoundException e) {
                    throw new AggregateNotFoundException(
                            aggregateIdentifier,
                            "Aggregate not found. Possibly involves an aggregate being created, "
                                    + "or a command that was executed against an aggregate that did not yet "
                                    + "finish the creation process. It will be rescheduled for publication when it "
                                    + "attempts to load an aggregate",
                            e
                    );
                } finally {
                    IOUtils.closeQuietlyIfCloseable(events);
                }
                firstLevelCache.put(aggregateRoot, PLACEHOLDER_VALUE);
                cache.put(aggregateIdentifier, aggregateRoot);
            }
            CurrentUnitOfWork.get().resources().put("AggregateRoot", aggregateRoot);
            return aggregateRoot;
        }

        @Override
        public void add(T aggregate) {
            firstLevelCache.put(aggregate, PLACEHOLDER_VALUE);
            cache.put(aggregate.getIdentifier(), aggregate);
            CurrentUnitOfWork.get().resources().put("AggregateRoot", aggregate);
        }

        private void removeFromCache(String aggregateIdentifier) {
            for (T cachedAggregate : firstLevelCache.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.getIdentifier())) {
                    firstLevelCache.remove(cachedAggregate);
                    logger.debug("Aggregate {} removed from first level cache for recovery purposes.",
                                 aggregateIdentifier);
                    return;
                }
            }
        }
    }
}
