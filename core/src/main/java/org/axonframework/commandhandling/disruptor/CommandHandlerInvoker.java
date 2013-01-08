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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import net.sf.jsr107cache.Cache;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
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
    private static final ThreadLocal<CommandHandlerInvoker> CURRENT_INVOKER = new ThreadLocal<CommandHandlerInvoker>();
    private static final Object PLACEHOLDER_VALUE = new Object();

    private final ConcurrentMap<String, DisruptorRepository> repositories = new ConcurrentHashMap<String, DisruptorRepository>();
    private final Cache cache;
    private final int segmentId;
    private final EventStore eventStore;

    /**
     * Returns the Repository instance for Aggregate with given <code>typeIdentifier</code> used by the
     * CommandHandlerInvoker that is running on the current thread.
     * <p/>
     * Calling this method from any other thread will return <code>null</code>.
     *
     * @param typeIdentifier The type identifier of the aggregate
     * @param <T>            The type of aggregate
     * @return the repository instance for aggregate of given type
     */
    @SuppressWarnings("unchecked")
    public static <T extends EventSourcedAggregateRoot> DisruptorRepository<T> getRepository(String typeIdentifier) {
        return CURRENT_INVOKER.get().repositories.get(typeIdentifier);
    }

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

    @Override
    public void onEvent(CommandHandlingEntry entry, long sequence, boolean endOfBatch) throws Exception {
        if (entry.isRecoverEntry()) {
            removeEntry(entry.getAggregateIdentifier());
        } else if (entry.getInvokerId() == segmentId) {
            DisruptorUnitOfWork unitOfWork = entry.getUnitOfWork();
            unitOfWork.start();
            try {
                Object result = entry.getInvocationInterceptorChain().proceed(entry.getCommand());
                entry.setResult(result);
                unitOfWork.commit();
            } catch (Throwable throwable) {
                entry.setExceptionResult(throwable);
                unitOfWork.rollback(throwable);
            }
        }
    }

    /**
     * Create a repository instance for an aggregate created by the given <code>aggregateFactory</code>. The returning
     * repository must be sage to use by this invoker instance.
     *
     * @param aggregateFactory The factory creating aggregate instances
     * @param <T>              The type of aggregate created by the factory
     * @return A Repository instance for the given aggregate
     */
    @SuppressWarnings("unchecked")
    public <T extends EventSourcedAggregateRoot> Repository<T> createRepository(AggregateFactory<T> aggregateFactory) {
        String typeIdentifier = aggregateFactory.getTypeIdentifier();
        if (!repositories.containsKey(typeIdentifier)) {
            DisruptorRepository<T> repository = new DisruptorRepository<T>(aggregateFactory, cache, eventStore);
            repositories.putIfAbsent(typeIdentifier, repository);
        }
        return repositories.get(typeIdentifier);
    }

    private void removeEntry(Object aggregateIdentifier) {
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
        private final AggregateFactory<T> aggregateFactory;
        private final Map<T, Object> firstLevelCache = new WeakHashMap<T, Object>();
        private final String typeIdentifier;
        private final Cache cache;

        private DisruptorRepository(AggregateFactory<T> aggregateFactory, Cache cache, EventStore eventStore) {
            this.aggregateFactory = aggregateFactory;
            this.cache = cache;
            this.eventStore = eventStore;
            typeIdentifier = this.aggregateFactory.getTypeIdentifier();
        }

        @Override
        public T load(Object aggregateIdentifier, Long expectedVersion) {
            T aggregate = load(aggregateIdentifier);
            if (expectedVersion != null && aggregate.getVersion() > expectedVersion) {
                throw new ConflictingAggregateVersionException(aggregateIdentifier,
                                                               expectedVersion,
                                                               aggregate.getVersion());
            }
            return aggregate;
        }

        @Override
        public T load(Object aggregateIdentifier) {
            T aggregateRoot = null;
            for (T cachedAggregate : firstLevelCache.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.getIdentifier())) {
                    logger.debug("Aggregate {} found in first level cache", aggregateIdentifier);
                    aggregateRoot = cachedAggregate;
                }
            }
            if (aggregateRoot == null) {
                Object cachedItem = cache.get(aggregateIdentifier);
                if (cachedItem != null && aggregateFactory.getAggregateType().isInstance(cachedItem)) {
                    aggregateRoot = aggregateFactory.getAggregateType().cast(cachedItem);
                }
            }
            if (aggregateRoot == null) {
                logger.debug("Aggregate {} not in first level cache, loading fresh one from Event Store",
                             aggregateIdentifier);
                try {
                    DomainEventStream events = eventStore.readEvents(typeIdentifier, aggregateIdentifier);
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
                            e);
                }
                firstLevelCache.put(aggregateRoot, PLACEHOLDER_VALUE);
                cache.put(aggregateIdentifier, aggregateRoot);
            }
            if (aggregateRoot != null) {
                DisruptorUnitOfWork unitOfWork = (DisruptorUnitOfWork) CurrentUnitOfWork.get();
                unitOfWork.setAggregateType(typeIdentifier);
                unitOfWork.registerAggregate(aggregateRoot, null, null);
            }
            return aggregateRoot;
        }

        @Override
        public void add(T aggregate) {
            DisruptorUnitOfWork unitOfWork = (DisruptorUnitOfWork) CurrentUnitOfWork.get();
            unitOfWork.setAggregateType(typeIdentifier);
            unitOfWork.registerAggregate(aggregate, null, null);
            firstLevelCache.put(aggregate, PLACEHOLDER_VALUE);
            cache.put(aggregate.getIdentifier(), aggregate);
        }

        private void removeFromCache(Object aggregateIdentifier) {
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
