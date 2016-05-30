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

package org.axonframework.eventhandling.interceptors;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.serialization.SerializationAwareDomainEventMessage;
import org.axonframework.serialization.SerializationAwareEventMessage;

import java.util.List;
import java.util.function.Function;

/**
 * Event dispatch interceptor that wraps each EventMessage in a SerializationAware message. This allows for performance
 * optimizations in cases where storage (in the event store) and publication (on the event bus) use the same
 * serialization mechanism.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializationOptimizingInterceptor implements MessageDispatchInterceptor<EventMessage<?>> {

    @Override
    public EventMessage<?> handle(EventMessage<?> message) {
        if (message instanceof DomainEventMessage) {
            return SerializationAwareDomainEventMessage.wrap((DomainEventMessage<?>) message);
        } else {
            return SerializationAwareEventMessage.wrap(message);
        }
    }

    @Override
    public Function<Integer, EventMessage<?>> handle(List<EventMessage<?>> messages) {
        return (index) -> handle(messages.get(index));
    }
}
