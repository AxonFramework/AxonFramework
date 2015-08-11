/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Implementation of the {@link EventBus} that directly forwards all published events (in the callers' thread) to all
 * subscribed listeners.
 * <p/>
 * Listeners are expected to implement asynchronous handling themselves.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleEventBus extends AbstractEventBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBus.class);
    private final Set<Cluster> clusters = new CopyOnWriteArraySet<>();

    private final PublicationStrategy publicationStrategy;

    /**
     * Initializes an event bus with a {@link PublicationStrategy} that forwards events to all subscribed clusters.
     */
    public SimpleEventBus() {
        this(new DirectTerminal());
    }

    /**
     * Initializes an event bus with given {@link PublicationStrategy}.
     *
     * @param publicationStrategy The strategy used by the event bus to publish events to listeners
     */
    public SimpleEventBus(PublicationStrategy publicationStrategy) {
        this.publicationStrategy = publicationStrategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(Cluster cluster) {
        if (this.clusters.remove(cluster)) {
            logger.debug("EventListener {} unsubscribed successfully", cluster.getName());
        } else {
            logger.info("EventListener {} not removed. It was already unsubscribed", cluster.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(Cluster cluster) {
        if (this.clusters.add(cluster)) {
            logger.debug("Cluster [{}] subscribed successfully", cluster.getName());
        } else {
            logger.info("Cluster [{}] not added. It was already subscribed", cluster.getName());
        }
    }

    @Override
    protected void prepareCommit(List<EventMessage<?>> events) {
        publicationStrategy.publish(events, clusters);
    }

    private static class DirectTerminal implements PublicationStrategy {

        @Override
        public void publish(List<EventMessage<?>> events, Set<Cluster> clusters) {
            for (Cluster cluster : clusters) {
                cluster.handle(events);
            }
        }
    }
}
