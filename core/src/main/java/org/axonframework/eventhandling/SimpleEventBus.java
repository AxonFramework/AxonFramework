/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.monitoring.SimpleEventBusStatistics;
import org.axonframework.monitoring.Monitored;
import org.axonframework.monitoring.jmx.JmxMonitorHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Implementation of the {@link EventBus} that directly forwards all published events (in the callers' thread) to all
 * subscribed listeners.
 * <p/>
 * Listeners are expected to implement asynchronous handling themselves.
 *
 * @author Allard Buijze
 * @see AsynchronousEventHandlerWrapper
 * @since 0.5
 */
public class SimpleEventBus implements EventBus, Monitored<SimpleEventBusStatistics> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBus.class);
    private final Set<EventListener> listeners = new CopyOnWriteArraySet<EventListener>();
    private volatile SimpleEventBusStatistics statistics = new SimpleEventBusStatistics();

    /**
     * Registers the current statistics object as a jmx monitor
     */
    @PostConstruct
    public void registerAsMonitor() {
        JmxMonitorHolder.registerMonitor("SimpleEventBusMonitor",statistics);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        if (listeners.remove(eventListener)) {
            Object listener = getActualListenerFrom(eventListener);
            statistics.recordUnregisteredListener(listener.getClass().getSimpleName());
            logger.debug("EventListener {} unsubscribed successfully", eventListener.getClass().getSimpleName());
        } else {
            logger.info("EventListener {} not removed. It was already unsubscribed",
                        eventListener.getClass().getSimpleName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(EventListener eventListener) {
        if (listeners.add(eventListener)) {
            Object listener = getActualListenerFrom(eventListener);
            statistics.listenerRegistered(listener.getClass().getSimpleName());
            logger.debug("EventListener [{}] subscribed successfully", eventListener.getClass().getSimpleName());
        } else {
            logger.info("EventListener [{}] not added. It was already subscribed",
                        eventListener.getClass().getSimpleName());
        }
    }

    private Object getActualListenerFrom(EventListener eventListener) {
        Object listener = eventListener;
        while (listener instanceof EventListenerProxy) {
            listener = ((EventListenerProxy) listener).getTarget();
        }
        return listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(Event event) {
        statistics.recordPublishedEvent();

        for (EventListener listener : listeners) {
            logger.debug("Dispatching Event [{}] to EventListener [{}]",
                         event.getClass().getSimpleName(),
                         listener.getClass().getSimpleName());
            listener.handle(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SimpleEventBusStatistics getStatistics() {
        return statistics;
    }
}
