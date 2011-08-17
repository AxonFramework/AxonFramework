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

package org.axonframework.gae.eventstore;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.AggregateSnapshot;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventstore.SnapshotEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jettro Coenradie
 */
public class GaeSnapshotter implements Snapshotter, InitializingBean, ApplicationContextAware {
    private final static Logger logger = LoggerFactory.getLogger(GaeSnapshotter.class);

    private SnapshotEventStore eventStore;
    private Map<String, AggregateFactory<?>> aggregateFactories = new ConcurrentHashMap<String, AggregateFactory<?>>();
    private ApplicationContext applicationContext;

    @Autowired
    public GaeSnapshotter(SnapshotEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public void scheduleSnapshot(String typeIdentifier, AggregateIdentifier aggregateIdentifier) {
        logger.debug("Schedule a new task to create a snapshot for type {} and aggregate {}",
                     typeIdentifier, aggregateIdentifier);

        Queue queue = QueueFactory.getQueue("snapshotter");

        queue.add(TaskOptions.Builder.withUrl("/task/snapshot")
                                     .param("typeIdentifier", typeIdentifier)
                                     .param("aggregateIdentifier", aggregateIdentifier.asString())
                                     .method(TaskOptions.Method.POST)
        );
    }

    public void createSnapshot(String typeIdentifier, String aggregateIdentifier) {
        DomainEventStream eventStream =
                eventStore.readEvents(typeIdentifier, new StringAggregateIdentifier(aggregateIdentifier));
        DomainEvent snapshotEvent = createSnapshot(typeIdentifier, eventStream);
        if (snapshotEvent != null) {
            eventStore.appendSnapshotEvent(typeIdentifier, snapshotEvent);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, AggregateFactory> factories = applicationContext.getBeansOfType(AggregateFactory.class);

        for (AggregateFactory factory : factories.values()) {
            this.aggregateFactories.put(factory.getTypeIdentifier(), factory);
        }
    }

    private DomainEvent createSnapshot(String typeIdentifier, DomainEventStream eventStream) {
        AggregateFactory<?> aggregateFactory = aggregateFactories.get(typeIdentifier);

        DomainEvent firstEvent = eventStream.peek();
        AggregateIdentifier aggregateIdentifier = firstEvent.getAggregateIdentifier();

        EventSourcedAggregateRoot aggregate = aggregateFactory.createAggregate(aggregateIdentifier, firstEvent);
        aggregate.initializeState(eventStream);

        return new AggregateSnapshot<EventSourcedAggregateRoot>(aggregate);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
