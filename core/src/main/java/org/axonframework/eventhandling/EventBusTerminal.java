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

import org.axonframework.domain.EventMessage;

/**
 * Interface describing a mechanism that connects Event Bus clusters. The terminal is responsible for delivering
 * published Events with all of the clusters available in the Event Bus (either locally, or remotely).
 * <p/>
 * Terminals are typically bound to a single Event Bus instance, but may be aware that multiple instances exist in
 * order to form a bridge between these Event Buses.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface EventBusTerminal {

    /**
     * Publishes the given <code>event</code> to all clusters on the Event Bus. The terminal is responsible for the
     * delivery process, albeit local or remote.
     *
     * @param event the event to publish
     */
    void publish(EventMessage event);

    /**
     * Invoked when an Event Listener has been assigned to a cluster that was not yet known to the Event Bus. This
     * method is invoked only once for each cluster that was assigned an Event Listener. Subsequent Event Listeners
     * are added to the cluster. Cluster remain "live" when all event listeners have been removed from them.
     *
     * @param cluster the newly created cluster
     */
    void onClusterCreated(Cluster cluster);
}
