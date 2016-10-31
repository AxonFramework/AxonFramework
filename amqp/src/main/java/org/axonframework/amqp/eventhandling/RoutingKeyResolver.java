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

package org.axonframework.amqp.eventhandling;

import org.axonframework.eventhandling.EventMessage;

/**
 * Interface toward a mechanism that provides the AMQP Routing Key for a given EventMessage. AMQP Message Brokers use
 * the routing key to decide which Queues will receive a copy of eah message, depending on the type of Exchange used.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@FunctionalInterface
public interface RoutingKeyResolver {

    /**
     * Returns the Routing Key to use when sending the given {@code eventMessage} to the Message Broker.
     *
     * @param eventMessage The EventMessage to resolve the routing key for
     * @return the routing key for the event message
     */
    String resolveRoutingKey(EventMessage<?> eventMessage);
}
