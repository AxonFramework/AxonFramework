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

import org.axonframework.common.Assert;
import org.axonframework.common.Subscription;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Abstract {@code Cluster} implementation that keeps track of Cluster members ({@link EventListener EventListeners}).
 * This implementation is thread-safe. The {@link #getMembers()} method returns a read-only runtime view of the members
 * in the cluster.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public abstract class AbstractCluster implements Cluster {

    private final String name;
    private final Set<EventListener> eventListeners;
    private final Set<EventListener> immutableEventListeners;
    private final ClusterMetaData clusterMetaData = new DefaultClusterMetaData();
    private final EventProcessingMonitorCollection subscribedMonitors = new EventProcessingMonitorCollection();
    private final MultiplexingEventProcessingMonitor eventProcessingMonitor = new MultiplexingEventProcessingMonitor(subscribedMonitors);

    /**
     * Initializes the cluster with given <code>name</code>. The order in which listeners are organized in the cluster
     * is undefined.
     *
     * @param name The name of this cluster
     */
    protected AbstractCluster(String name) {
        Assert.notNull(name, "name may not be null");
        this.name = name;
        eventListeners = new CopyOnWriteArraySet<>();
        immutableEventListeners = Collections.unmodifiableSet(eventListeners);
    }

    /**
     * Initializes the cluster with given <code>name</code>. The order in which listeners are organized in the cluster
     * is undefined.
     *
     * @param name The name of this cluster
     */
    protected AbstractCluster(String name, EventListener... initialListeners) {
        this(name);
        eventListeners.addAll(Arrays.asList(initialListeners));
    }

    /**
     * Initializes the cluster with given <code>name</code>, using given <code>comparator</code> to order the listeners
     * in the cluster. The order of invocation of the members in this cluster is according the order provided by the
     * comparator.
     *
     * @param name       The name of this cluster
     * @param comparator The comparator providing the ordering of the Event Listeners
     */
    protected AbstractCluster(String name, Comparator<EventListener> comparator) {
        Assert.notNull(name, "name may not be null");
        this.name = name;
        eventListeners = new ConcurrentSkipListSet<>(comparator);
        immutableEventListeners = Collections.unmodifiableSet(eventListeners);
    }

    @Override
    public void handle(List<EventMessage<?>> events) {
        doPublish(events, eventListeners, eventProcessingMonitor);
    }

    /**
     * Publish the given list of <code>events</code> to the given set of <code>eventListeners</code>, and notify the
     * given <code>eventProcessingMonitor</code> after completion. The given set of <code>eventListeners</code> is a
     * live view on the memberships of the cluster. Any subscription changes are immediately visible in this set.
     * Iterators created on the set iterate over an immutable view reflecting the state at the moment the iterator was
     * created.
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
     * @param eventListeners         The event listeners subscribed at the moment the event arrived
     * @param eventProcessingMonitor The monitor to notify after completion.
     */
    protected abstract void doPublish(List<EventMessage<?>> events, Set<EventListener> eventListeners,
                                      MultiplexingEventProcessingMonitor eventProcessingMonitor);

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Subscription subscribe(EventListener eventListener) {
        eventListeners.add(eventListener);
        Subscription monitorSubscription =
                eventListener instanceof EventProcessingMonitorSupport
                        ? ((EventProcessingMonitorSupport) eventListener)
                          .subscribeEventProcessingMonitor(eventProcessingMonitor)
                        : null;
        return () -> {
            if (eventListeners.remove(eventListener)) {
                if (monitorSubscription != null) {
                    monitorSubscription.stop();
                }
                return true;
            }
            return false;
        };
    }

    @Override
    public ClusterMetaData getMetaData() {
        return clusterMetaData;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation returns a real-time view on the actual members, which changes when members join or leave the
     * cluster. Iterators created from the returned set are thread-safe and iterate over the members available at the
     * time the iterator was created. The iterator does not allow the {@link java.util.Iterator#remove()} method to be
     * invoked.
     */
    public Set<EventListener> getMembers() {
        return immutableEventListeners;
    }

    @Override
    public Subscription subscribeEventProcessingMonitor(EventProcessingMonitor monitor) {
        return subscribedMonitors.subscribeEventProcessingMonitor(monitor);
    }
}
