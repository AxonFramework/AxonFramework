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

import org.axonframework.common.Registration;

import java.util.Arrays;
import java.util.List;

/**
 * A event processor represents a group of Event Listeners that are treated as a single group by the {@link
 * ClusteringEventBus}. This allows attributes and behavior (e.g. transaction management, asynchronous processing,
 * distribution) to be applied over a whole group at once.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface EventProcessor extends EventProcessingMonitorSupport {

    /**
     * Returns the name of this event processor. This name is used to detect distributed instances of the
     * same event processor. Multiple instances referring to the same logical event processor (on different JVM's)
     * must have the same name.
     *
     * @return the name of this event processor
     */
    String getName();

    /**
     * Publishes the given Events to the members of this event processor.
     * <p/>
     * Implementations may do this synchronously or asynchronously. Although {@link EventListener EventListeners} are
     * discouraged to throw exceptions, it is possible that they are propagated through this method invocation. In that
     * case, no guarantees can be given about the delivery of Events at all EventProcessor members.
     *
     * @param events The Events to publish in the event processor
     */
    default void handle(EventMessage... events) {
        handle(Arrays.asList(events));
    }

    /**
     * Publishes the given Events to the members of this event processor.
     * <p/>
     * Implementations may do this synchronously or asynchronously. Although {@link EventListener EventListeners} are
     * discouraged to throw exceptions, it is possible that they are propagated through this method invocation. In that
     * case, no guarantees can be given about the delivery of Events at all EventProcessor members.
     *
     * @param events The Events to publish in the event processor
     */
    void handle(List<EventMessage<?>> events);

    /**
     * Subscribe the given {@code eventListener} to this cluster. If the listener is already subscribed, nothing
     * happens.
     * <p/>
     * While the Event Listeners is subscribed, it will receive all messages published to the event processor.
     *
     * @param eventListener the Event Listener instance to subscribe
     * @return a handle to unsubscribe the <code>eventListener</code>.
     * When unsubscribed it will no longer receive events from this cluster.
     */
    Registration subscribe(EventListener eventListener);

    default ProcessingToken lastProcessedToken() {
        return null;
    }

    /**
     * Returns the MetaData of this event processor.
     *
     * @return the MetaData of this event processor
     */
    EventProcessorMetaData getMetaData();
}
