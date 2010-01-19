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

import org.axonframework.core.DomainEvent;
import org.axonframework.core.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
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

    private final static int DEFAULT_CORE_POOL_SIZE = 5;
    private final static int DEFAULT_MAX_POOL_SIZE = 25;
    private final static long DEFAULT_KEEP_ALIVE_TIME = 5;
    private final static TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MINUTES;

    private ExecutorService executorService;
    private final ConcurrentMap<EventListener, EventHandlingSequenceManager> listenerManagers =
            new ConcurrentHashMap<EventListener, EventHandlingSequenceManager>();
    private boolean shutdownExecutorServiceOnStop = false;
    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(DomainEvent event) {
        Assert.state(running.get(), "The EventBus is currently not running.");
        for (EventHandlingSequenceManager eventHandlingSequencing : listenerManagers.values()) {
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
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        listenerManagers.remove(eventListener);
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
        running.set(true);
        if (executorService == null) {
            shutdownExecutorServiceOnStop = true;
            executorService = new ThreadPoolExecutor(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE,
                                                     DEFAULT_KEEP_ALIVE_TIME, DEFAULT_TIME_UNIT,
                                                     new LinkedBlockingQueue<Runnable>());
        }
    }

    /**
     * Stops this event bus. All subscriptions are removed and incoming events are rejected.
     */
    @PreDestroy
    public void stop() {
        running.set(false);
        listenerManagers.clear();
        if (executorService != null && shutdownExecutorServiceOnStop) {
            executorService.shutdown();
        }
    }

    /**
     * Creates a new EventHandlingSequenceManager for the given event listener.
     *
     * @param eventListener The event listener that the EventHandlingSequenceManager should manage
     * @return a new EventHandlingSequenceManager instance
     */
    protected EventHandlingSequenceManager newEventHandlingSequenceManager(EventListener eventListener) {
        return new EventHandlingSequenceManager(eventListener, getExecutorService());
    }

    /**
     * Accessor for the executor service used to dispatch events in this event bus
     *
     * @return the executor service used to dispatch events in this event bus
     */
    protected ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Sets the ExecutorService instance to use to handle events. Typically, this will be a ThreadPoolExecutor
     * implementation with an unbounded blocking queue.
     * <p/>
     * Defaults to a ThreadPoolExecutor with 5 core threads and a max pool size of 25 threads with a timeout of 5
     * minutes.
     *
     * @param executorService the executor service to use for event handling
     */
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Defines whether or not to shutdown the executor service when the EventBus is stopped. This value is ignored when
     * the default ExecutorService is used. Defaults to <code>false</code> if a custom executor service is defined using
     * the {@link #setExecutorService(java.util.concurrent.ExecutorService)} method.
     *
     * @param shutdownExecutorServiceOnStop Whether or not to shutdown the executor service when the event bus is
     *                                      stopped
     */
    public void setShutdownExecutorServiceOnStop(boolean shutdownExecutorServiceOnStop) {
        this.shutdownExecutorServiceOnStop = shutdownExecutorServiceOnStop;
    }
}
