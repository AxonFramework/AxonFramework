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
 * A simple Cluster implementation that invokes each of the members of a cluster when an Event is published. When an
 * Event Listener raises an exception, publication of the Event is aborted and the exception is propagated. No
 * guarantees are given about the order of invocation of Event Listeners.
 *
 * @author ALlard Buijze
 * @since 1.2
 */
public class SimpleCluster extends AbstractCluster {

    @Override
    public void publish(EventMessage... events) {
        for(EventMessage event : events) {
            for (EventListener eventListener : getMembers()) {
                eventListener.handle(event);
            }
        }
    }
}
