/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Abstract {@code EventProcessor} implementation that keeps track of event processor members
 * ({@link EventListener EventListeners}). This implementation is thread-safe.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public abstract class AbstractEventProcessor implements EventProcessor, EventProcessingMonitorSupport {

    private final String name;
    private final Set<EventListener> eventListeners;
    private final Set<EventListener> immutableEventListeners;
    private final Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors;
    private final EventProcessingMonitorCollection subscribedMonitors = new EventProcessingMonitorCollection();
    private final MultiplexingEventProcessingMonitor eventProcessingMonitor = new MultiplexingEventProcessingMonitor(subscribedMonitors);

    /**
     * Initializes the event processor with given <code>name</code>. The order in which listeners are organized in the
     * event processor is undefined.
     *
     * @param name The name of this event processor
     */
    protected AbstractEventProcessor(String name) {
        Assert.notNull(name, "name may not be null");
        this.name = name;
        eventListeners = new CopyOnWriteArraySet<>();
        immutableEventListeners = Collections.unmodifiableSet(eventListeners);
        interceptors = new CopyOnWriteArraySet<>();
    }

    /**
     * Initializes the event processor with given <code>name</code>. The order in which listeners are organized in the
     * event processor is undefined.
     *
     * @param name The name of this event processor
     */
    protected AbstractEventProcessor(String name, EventListener... initialListeners) {
        this(name);
        eventListeners.addAll(Arrays.asList(initialListeners));
    }

    /**
     * Initializes the event processor with given <code>name</code>, using given <code>comparator</code> to order the
     * listeners in the event processor. The order of invocation of the members in this event processor is according
     * the order provided by the comparator.
     *
     * @param name       The name of this event processor
     * @param comparator The comparator providing the ordering of the Event Listeners
     */
    protected AbstractEventProcessor(String name, Comparator<EventListener> comparator) {
        Assert.notNull(name, "name may not be null");
        this.name = name;
        eventListeners = new ConcurrentSkipListSet<>(comparator);
        immutableEventListeners = Collections.unmodifiableSet(eventListeners);
        interceptors = new CopyOnWriteArraySet<>();
    }

    @Override
    public void handle(List<EventMessage<?>> events) {
        doPublish(events, immutableEventListeners, interceptors, eventProcessingMonitor);
    }

    /**
     * Publish the given list of <code>events</code> to the given set of <code>eventListeners</code>, and notify the
     * given <code>eventProcessingMonitor</code> after completion. The given set of <code>eventListeners</code> is a
     * live view on the memberships of the event processor. Any subscription changes are immediately visible in this set.
     * Iterators created on the set iterate over an immutable view reflecting the state at the moment the iterator was
     * created.
     * <p/>
     * Before each event is given to the <code>eventListeners</code> the event message should be passed through
     * a chain of given <code>interceptors</code>. Each of the interceptors may modify the event message, or stop
     * publication altogether. Additionally, Interceptors are able to interact with the unit of work that is created
     * to process each event message.
     * <p/>
     * When this method is invoked as part of a Unit of Work (see
     * {@link CurrentUnitOfWork#isStarted()}), the monitor invocation should be postponed
     * until the Unit of Work is committed or rolled back, to ensure any transactions are properly propagated when the
     * monitor is invoked.
     * <p/>
     * It is the implementation's responsibility to ensure that &ndash;eventually&ndash; the each of the given
     * <code>events</code> is provided to the <code>eventProcessingMonitor</code>, either to the {@link
     * org.axonframework.eventhandling.EventProcessingMonitor#onEventProcessingCompleted(java.util.List)} or the {@link
     * org.axonframework.eventhandling.EventProcessingMonitor#onEventProcessingFailed(java.util.List, Throwable)}
     * method.
     *
     * @param events                 The events to publish
     * @param eventListeners         Immutable real-time view on subscribed event listeners
     * @param interceptors           Registered interceptors that need to intercept each event before it's handled
     * @param eventProcessingMonitor The monitor to notify after completion.
     */
    protected abstract void doPublish(List<EventMessage<?>> events, Set<EventListener> eventListeners,
                                      Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors,
                                      MultiplexingEventProcessingMonitor eventProcessingMonitor) ;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Registration subscribe(EventListener eventListener) {
        eventListeners.add(eventListener);
        Registration monitorSubscription =
                eventListener instanceof EventProcessingMonitorSupport
                        ? ((EventProcessingMonitorSupport) eventListener)
                          .subscribeEventProcessingMonitor(eventProcessingMonitor)
                        : null;
        return () -> {
            if (eventListeners.remove(eventListener)) {
                if (monitorSubscription != null) {
                    monitorSubscription.cancel();
                }
                return true;
            }
            return false;
        };
    }

    @Override
    public Registration registerInterceptor(MessageHandlerInterceptor<EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public Registration subscribeEventProcessingMonitor(EventProcessingMonitor monitor) {
        return subscribedMonitors.subscribeEventProcessingMonitor(monitor);
    }
}
