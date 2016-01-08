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

import org.axonframework.common.Registration;
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
    private final Set<EventProcessor> eventProcessors = new CopyOnWriteArraySet<>();

    private final PublicationStrategy publicationStrategy;

    /**
     * Initializes an event bus with a {@link PublicationStrategy} that forwards events to all subscribed event
     * processors.
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
    public Registration subscribe(EventProcessor eventProcessor) {
        if (this.eventProcessors.add(eventProcessor)) {
            logger.debug("EventProcessor [{}] subscribed successfully", eventProcessor.getName());
        } else {
            logger.info("EventProcessor [{}] not added. It was already subscribed", eventProcessor.getName());
        }
        return () -> {
            if (eventProcessors.remove(eventProcessor)) {
                logger.debug("EventListener {} unsubscribed successfully", eventProcessor.getName());
                return true;
            } else {
                logger.info("EventListener {} not removed. It was already unsubscribed", eventProcessor.getName());
                return false;
            }
        };
    }

    @Override
    protected void prepareCommit(List<EventMessage<?>> events) {
        publicationStrategy.publish(events, eventProcessors);
    }

    private static class DirectTerminal implements PublicationStrategy {

        @Override
        public void publish(List<EventMessage<?>> events, Set<EventProcessor> eventProcessors) {
            for (EventProcessor eventProcessor : eventProcessors) {
                eventProcessor.handle(events);
            }
        }
    }
}
