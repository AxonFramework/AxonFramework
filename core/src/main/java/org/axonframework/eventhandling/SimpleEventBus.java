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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.monitoring.MonitorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class SimpleEventBus implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBus.class);
    private final Set<EventListener> listeners = new CopyOnWriteArraySet<EventListener>();
    private final SimpleEventBusStatistics statistics = new SimpleEventBusStatistics();

    /**
     * Initializes the SimpleEventBus and registers the mbeans for management information.
     */
    public SimpleEventBus() {
        MonitorRegistry.registerMonitoringBean(statistics, SimpleEventBus.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        String listenerType = classNameOf(eventListener);
        if (listeners.remove(eventListener)) {
            statistics.recordUnregisteredListener(listenerType);
            logger.debug("EventListener {} unsubscribed successfully", listenerType);
        } else {
            logger.info("EventListener {} not removed. It was already unsubscribed", listenerType);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(EventListener eventListener) {
        String listenerType = classNameOf(eventListener);
        if (listeners.add(eventListener)) {
            statistics.listenerRegistered(listenerType);
            logger.debug("EventListener [{}] subscribed successfully", listenerType);
        } else {
            logger.info("EventListener [{}] not added. It was already subscribed", listenerType);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(EventMessage... events) {
        statistics.recordPublishedEvent();
        if (!listeners.isEmpty()) {
            for (EventMessage event : events) {
                for (EventListener listener : listeners) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Dispatching Event [{}] to EventListener [{}]",
                                     event.getPayloadType().getSimpleName(), classNameOf(listener));
                    }
                    listener.handle(event);
                }
            }
        }
    }

    private String classNameOf(EventListener eventListener) {
        Class<?> listenerType;
        if (eventListener instanceof EventListenerProxy) {
            listenerType = ((EventListenerProxy) eventListener).getTargetType();
        } else {
            listenerType = eventListener.getClass();
        }
        return listenerType.getSimpleName();
    }
}
