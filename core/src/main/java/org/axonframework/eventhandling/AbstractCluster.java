/*
 * Copyright (c) 2010-2012. Axon Framework
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

import java.util.Collections;
import java.util.Set;
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
    private final Set<EventListener> eventListeners = new CopyOnWriteArraySet<EventListener>();
    private final Set<EventListener> immutableEventListeners = Collections.unmodifiableSet(eventListeners);
    private final ClusterMetaData clusterMetaData = new DefaultClusterMetaData();

    /**
     * Initializes the cluster with given <code>name</code>.
     *
     * @param name The name of this cluster
     */
    protected AbstractCluster(String name) {
        Assert.notNull(name, "name may not be null");
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void subscribe(EventListener eventListener) {
        eventListeners.add(eventListener);
    }

    @Override
    public void unsubscribe(EventListener eventListener) {
        eventListeners.remove(eventListener);
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
    @Override
    public Set<EventListener> getMembers() {
        return immutableEventListeners;
    }
}
