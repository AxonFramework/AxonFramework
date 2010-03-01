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
import org.axonframework.core.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TODO (issue #21): Most of the logic associated with the async behavior is moved towards the EventHandlers themselves.
 * Remove from here.
 * <p/>
 * EventBus implementation that uses an ExecutorService to dispatch events asynchronously. This dispatcher takes into
 * account the {@link EventSequencingPolicy} provided by the {@link EventListener} for sequential handling
 * requirements.
 *
 * @author Allard Buijze
 * @see EventSequencingPolicy
 * @see org.axonframework.core.eventhandler.EventListener
 * @since 0.3
 */
public class AsyncEventBus implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(AsyncEventBus.class);

    private static final int DEFAULT_CORE_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 25;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 5;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MINUTES;

    private Executor executor;
    private final ConcurrentMap<EventListener, EventHandlingSequenceManager> listenerManagers =
            new ConcurrentHashMap<EventListener, EventHandlingSequenceManager>();
    private boolean shutdownExecutorServiceOnStop = false;
    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(Event event) {
        Assert.state(running.get(), "The EventBus is currently not running.");
        logger.info("Publishing event of type {} with identifier {}",
                    event.getClass().getSimpleName(),
                    event.getEventIdentifier());
        for (EventHandlingSequenceManager eventHandlingSequencing : listenerManagers.values()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Dispatching event [{}] to listener [{}]",
                             event.getClass().getSimpleName(),
                             eventHandlingSequencing.getEventListener().toString());
            }
            eventHandlingSequencing.addEvent(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(EventListener eventListener) {
        Assert.state(running.get(), "The EventBus is currently not running.");
        if (!listenerManagers.containsKey(eventListener)) {
            listenerManagers.putIfAbsent(eventListener, newEventHandlingSequenceManager(eventListener));
            logger.info("Subscribed event listener [{}]", eventListener.toString());
        } else {
            logger.info("Event listener [{}] was already subscribed.", eventListener.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        if (listenerManagers.remove(eventListener) != null) {
            logger.info("Event listener [{}] unsubscribed.", eventListener.toString());
        } else {
            logger.info("Event listener [{}] not unsubscribed. It wasn't subscribed.",
                        eventListener.toString());
        }
    }

    /**
     * Starts the EventBus, opening it for incoming subscription requests and events.
     * <p/>
     * Will configure a default executor service if none has been wired. This method must be called after initialization
     * of all properties.
     * <p/>
     * {@inheritDoc}
     */
    @PostConstruct
    public void start() {
        logger.info("Starting the AsyncEventBus.");
        running.set(true);
        if (executor == null) {
            shutdownExecutorServiceOnStop = true;
            logger.info("Initializing default ThreadPoolExecutor to process incoming events");
            ScheduledThreadPoolExecutor defaultExecutor = new ScheduledThreadPoolExecutor(DEFAULT_CORE_POOL_SIZE);
            defaultExecutor.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
            defaultExecutor.setKeepAliveTime(DEFAULT_KEEP_ALIVE_TIME, DEFAULT_TIME_UNIT);
            executor = defaultExecutor;
        } else {
            logger.info("Using provided [{}] to process incoming events", executor.getClass().getSimpleName());
        }
        logger.info("AsyncEventBus started.");
    }

    /**
     * Stops this event bus. All subscriptions are removed and incoming events are rejected.
     */
    @PreDestroy
    public void stop() {
        logger.info("Stopping AsyncEventBus...");
        running.set(false);
        listenerManagers.clear();
        if (shutdownExecutorServiceOnStop && executor instanceof ExecutorService) {
            logger.info("Shutting down the executor...");
            ((ExecutorService) executor).shutdown();
        }
        logger.info("AsyncEventBus stopped.");
    }

    /**
     * Creates a new EventHandlingSequenceManager for the given event listener.
     *
     * @param eventListener The event listener that the EventHandlingSequenceManager should manage
     * @return a new EventHandlingSequenceManager instance
     */
    protected EventHandlingSequenceManager newEventHandlingSequenceManager(EventListener eventListener) {
        return new EventHandlingSequenceManager(eventListener, getExecutor());
    }

    /**
     * Accessor for the executor service used to dispatch events in this event bus
     *
     * @return the executor service used to dispatch events in this event bus
     */
    protected Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the ExecutorService instance to use to handle events. Typically, this will be a ThreadPoolExecutor
     * implementation with an unbounded blocking queue.
     * <p/>
     * Defaults to a ThreadPoolExecutor with 5 core threads and a max pool size of 25 threads with a timeout of 5
     * minutes.
     *
     * @param executor the executor service to use for event handling
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * Defines whether or not to shutdown the executor service when the EventBus is stopped. This value is ignored when
     * the default ExecutorService is used. Defaults to <code>false</code> if a custom executor service is defined using
     * the {@link #setExecutor(java.util.concurrent.Executor)} method.
     *
     * @param shutdownExecutorServiceOnStop Whether or not to shutdown the executor service when the event bus is
     *                                      stopped
     */
    public void setShutdownExecutorServiceOnStop(boolean shutdownExecutorServiceOnStop) {
        this.shutdownExecutorServiceOnStop = shutdownExecutorServiceOnStop;
    }
}
