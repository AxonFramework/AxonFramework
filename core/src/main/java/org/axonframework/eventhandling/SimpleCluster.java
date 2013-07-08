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

import org.axonframework.domain.EventMessage;

/**
 * A simple Cluster implementation that invokes each of the members of a cluster when an Event is published. When an
 * Event Listener raises an exception, publication of the Event is aborted and the exception is propagated. No
 * guarantees are given about the order of invocation of Event Listeners.
 *
 * @author ALlard Buijze
 * @since 1.2
 */
public class SimpleCluster extends AbstractCluster {

    /**
     * Initializes the cluster with given <code>name</code>.
     *
     * @param name The name of this cluster
     */
    public SimpleCluster(String name) {
        super(name);
    }

    /**
     * Initializes the cluster with given <code>name</code>, using given <code>orderResolver</code> to define the
     * order in which listeners need to be invoked.
     * <p/>
     * Listeners are invoked with the lowest order first.
     *
     * @param name          The name of this cluster
     * @param orderResolver The resolver defining the order in which listeners need to be invoked
     */
    public SimpleCluster(String name, OrderResolver orderResolver) {
        super(name, new EventListenerOrderComparator(orderResolver));
    }

    @Override
    public void publish(EventMessage... events) {
        for (EventMessage event : events) {
            for (EventListener eventListener : getMembers()) {
                eventListener.handle(event);
            }
        }
    }
}
