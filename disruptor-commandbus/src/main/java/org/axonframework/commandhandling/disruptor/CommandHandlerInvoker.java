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
public class CommandHandlerInvoker implements EventHandler<CommandHandlingEntry> {

    private final ConcurrentMap<String, DisruptorRepository> repositories = new ConcurrentHashMap<String, DisruptorRepository>();
    private final Cache cache;
    private final EventStore eventStore;

    /**
     * Create an aggregate invoker instance that uses the given <code>eventStore</code> and <code>cache</code> to
     * retrieve aggregate instances.
     *
     * @param eventStore The event store providing access to events to reconstruct aggregates
     * @param cache      The cache temporarily storing aggregate instances
     */
    public CommandHandlerInvoker(EventStore eventStore, Cache cache) {
        this.eventStore = eventStore;
        this.cache = cache;
    }

    @Override
    public void onEvent(CommandHandlingEntry entry, long sequence, boolean endOfBatch) throws Exception {
        if (entry.isRecoverEntry()) {
            removeEntry(entry.getAggregateIdentifier());
        } else {
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
            DisruptorRepository<T> repository = new DisruptorRepository<T>(aggregateFactory, eventStore);
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

    private class DisruptorRepository<T extends EventSourcedAggregateRoot> implements Repository<T> {

        private final Object VAL = new Object();
        private final EventStore eventStore;
        private final AggregateFactory<T> aggregateFactory;
        private final Map<T, Object> preLoadedAggregates = new WeakHashMap<T, Object>();
        private final String typeIdentifier;

        private DisruptorRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore) {
            this.aggregateFactory = aggregateFactory;
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
            for (T cachedAggregate : preLoadedAggregates.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.getIdentifier())) {
                    aggregateRoot = cachedAggregate;
                }
            }
            if (aggregateRoot == null) {
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
            }
            if (aggregateRoot != null) {
                DisruptorUnitOfWork unitOfWork = (DisruptorUnitOfWork) CurrentUnitOfWork.get();
                unitOfWork.setAggregateType(typeIdentifier);
                unitOfWork.registerAggregate(aggregateRoot, null, null);
                preLoadedAggregates.put(aggregateRoot, VAL);
            }
            return aggregateRoot;
        }

        @Override
        public void add(T aggregate) {
            DisruptorUnitOfWork unitOfWork = (DisruptorUnitOfWork) CurrentUnitOfWork.get();
            unitOfWork.setAggregateType(typeIdentifier);
            unitOfWork.registerAggregate(aggregate, null, null);
        }

        public void removeFromCache(Object aggregateIdentifier) {
            for (T cachedAggregate : preLoadedAggregates.keySet()) {
                if (aggregateIdentifier.equals(cachedAggregate.getIdentifier())) {
                    preLoadedAggregates.remove(cachedAggregate);
                    return;
                }
            }
        }
    }
}
