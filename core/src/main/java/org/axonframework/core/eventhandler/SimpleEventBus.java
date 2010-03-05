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

package org.axonframework.core.eventhandler;

import org.axonframework.core.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Implementation of the {@link org.axonframework.core.eventhandler.EventBus} that directly forwards all published
 * events (in the callers' thread) to all subscribed listeners.
 * <p/>
 * Listeners are expected to implement asynchronous handling themselves.
 *
 * @author Allard Buijze
 * @since 0.5
 * @see org.axonframework.core.eventhandler.AsynchronousEventHandlerWrapper
 */
public class SimpleEventBus implements EventBus {

    private final Set<EventListener> listeners = new CopyOnWriteArraySet<EventListener>();
    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBus.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        if (listeners.remove(eventListener)) {
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
            logger.debug("EventListener [{}] subscribed successfully", eventListener.getClass().getSimpleName());
        } else {
            logger.info("EventListener [{}] not added. It was already subscribed",
                        eventListener.getClass().getSimpleName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(Event event) {
        for (EventListener listener : listeners) {
            logger.debug("Dispatching Event [{}] to EventListener [{}]",
                         event.getClass().getSimpleName(),
                         listener.getClass().getSimpleName());
            listener.handle(event);
        }
    }
}
