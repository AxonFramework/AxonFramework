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

import com.lmax.disruptor.BatchHandler;
import com.lmax.disruptor.LifecycleAware;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Allard Buijze
 */
class AggregatePreLoader implements BatchHandler<CommandHandlingEntry>, LifecycleAware {

    private final Map<AggregateIdentifier, EventSourcedAggregateRoot> map = new HashMap<AggregateIdentifier, EventSourcedAggregateRoot>();
    private final EventStore eventStore;
    private final AggregateFactory<?> aggregateFactory;

    AggregatePreLoader(EventStore eventStore, AggregateFactory<?> aggregateFactory) {
        this.eventStore = eventStore;
        this.aggregateFactory = aggregateFactory;
    }

    @Override
    public void onAvailable(CommandHandlingEntry entry) throws Exception {
        final AggregateIdentifier aggregateIdentifier = entry.getAggregateIdentifier();
        if (map.containsKey(aggregateIdentifier)) {
            entry.setPreLoadedAggregate(map.get(aggregateIdentifier));
        } else {
            DomainEventStream events = eventStore.readEvents(aggregateFactory.getTypeIdentifier(), aggregateIdentifier);
            EventSourcedAggregateRoot aggregateRoot = aggregateFactory.createAggregate(aggregateIdentifier,
                                                                                       events.peek());
            aggregateRoot.initializeState(events);
            map.put(aggregateIdentifier, aggregateRoot);
            entry.setPreLoadedAggregate(aggregateRoot);
        }
    }

    @Override
    public void onEndOfBatch() throws Exception {
    }

    @Override
    public void onStart() {
        System.out.println("Started!");
    }

    @Override
    public void onShutdown() {
        System.out.println("Shutdown");
    }
}
