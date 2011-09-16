/*
 * Copyright (c) 2010-2011. Axon Framework
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link EventBus} implementation that supports clustering of Event Listeners. Clusters are connected using {@link
 * EventBusTerminal EventBus Terminals}, which may either distribute Events locally, or remotely.
 * <p/>
 * The separation of Eventlisteners in clusters allows for a more flexible method of distribution of Events to their
 * listeners. Some clusters may be connected using a remote distribution mechanism, while other clusters handle their
 * events asynchronously.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class ClusteringEventBus implements EventBus {

    private final EventBusTerminal terminal;
    private final ClusterSelector clusterSelector;

    // guarded by "this"
    private final Set<Cluster> clusters = new HashSet<Cluster>();

    /**
     * Initializes a <code>ClusteringEventBus</code> with a {@link SimpleEventBusTerminal} and a {@link
     * DefaultClusterSelector}. This causes dispatching to happen synchronously and places all subscribed event
     * handlers in a single cluster, making its default behavior similar to that of the {@link SimpleEventBus}.
     */
    public ClusteringEventBus() {
        this(new DefaultClusterSelector(), new SimpleEventBusTerminal());
    }

    /**
     * Initializes a <code>ClusteringEventBus</code> with the given <code>terminal</code> and a {@link
     * DefaultClusterSelector}. This places all subscribed event handlers in a single cluster. The
     * <code>terminal</code> is responsible for publishing incoming events in this cluster.
     *
     * @param terminal The terminal responsible for dispatching events to the clusters
     */
    public ClusteringEventBus(EventBusTerminal terminal) {
        this(new DefaultClusterSelector(), terminal);
    }

    /**
     * Initializes a <code>ClusteringEventBus</code> with the given <code>clusterSelector</code> and a {@link
     * SimpleEventBusTerminal}, which dispatches all events to all local clusters synchronously.
     *
     * @param clusterSelector The Cluster Selector that chooses the cluster for each of the subscribed event listeners
     */
    public ClusteringEventBus(ClusterSelector clusterSelector) {
        this(clusterSelector, new SimpleEventBusTerminal());
    }

    /**
     * Initializes a <code>ClusteringEventBus</code> with the given <code>clusterSelector</code> and a
     * <code>terminal</code>.
     *
     * @param clusterSelector The Cluster Selector that chooses the cluster for each of the subscribed event listeners
     * @param terminal        The terminal responsible for publishing incoming events to each of the clusters
     */
    public ClusteringEventBus(ClusterSelector clusterSelector, EventBusTerminal terminal) {
        this.clusterSelector = clusterSelector;
        this.terminal = terminal;
    }

    @Override
    public void publish(Event event) {
        terminal.publish(event);
    }

    @Override
    public void subscribe(EventListener eventListener) {
        clusterFor(eventListener).subscribe(eventListener);
    }

    @Override
    public void unsubscribe(EventListener eventListener) {
        clusterFor(eventListener).unsubscribe(eventListener);
    }

    private synchronized Cluster clusterFor(EventListener eventListener) {
        Cluster cluster = clusterSelector.selectCluster(eventListener);
        if (clusters.add(cluster)) {
            terminal.onClusterCreated(cluster);
        }
        return cluster;
    }

    private static class SimpleEventBusTerminal implements EventBusTerminal {

        private List<Cluster> clusters = new CopyOnWriteArrayList<Cluster>();

        @Override
        public void publish(Event event) {
            for (Cluster cluster : clusters) {
                cluster.publish(event);
            }
        }

        @Override
        public void onClusterCreated(Cluster cluster) {
            clusters.add(cluster);
        }
    }
}
