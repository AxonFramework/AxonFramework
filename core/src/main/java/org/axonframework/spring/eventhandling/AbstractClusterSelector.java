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

package org.axonframework.spring.eventhandling;

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventListenerProxy;

/**
 * Abstract implementation of the ClusterSelector interface that detects proxies and passes the actual Class of the
 * Event Listener implementation. Typically, this occurs when annotated event handlers are used. The {@link
 * #doSelectCluster(org.axonframework.eventhandling.EventListener, Class)} method, in such case, returns the Class of
 * the annotated listener.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractClusterSelector implements ClusterSelector {

    @Override
    public Cluster selectCluster(EventListener eventListener) {
        Class<?> listenerType;
        if (eventListener instanceof EventListenerProxy) {
            listenerType = ((EventListenerProxy) eventListener).getTargetType();
        } else {
            listenerType = eventListener.getClass();
        }
        return doSelectCluster(eventListener, listenerType);
    }

    /**
     * Select a cluster for the given <code>eventListener</code>, which has the actual class <code>listenerType</code>.
     * Note that the given <code>listenerType</code> does not have to be assignable to <code>EventListener</code>, as
     * it is possible that the <code>eventListener</code> acts as a proxy to an instance of <code>listenerType</code>.
     *
     * @param eventListener The listener instance handling the events, possibly a proxy
     * @param listenerType  The actual type of the Event Listener
     * @return the cluster to assign the Event Listener to
     */
    protected abstract Cluster doSelectCluster(EventListener eventListener, Class<?> listenerType);
}
