/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.model.inspection.EntityModel;
import org.axonframework.common.property.Property;
import org.axonframework.messaging.Message;

import java.util.Objects;
import java.util.function.Supplier;

import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * Only forward messages of type {@code T} if the routing key of the message matches the identifier of the entity.
 *
 * @param <T> the implementation {@code T} of the {@link org.axonframework.messaging.Message} being forwarded.
 */
public class ForwardMatchingInstances<T extends Message<?>> implements ForwardingMode<T> {

    private static final String EMPTY_STRING = "";

    private final String routingKey;
    private final EntityModel childEntity;

    public ForwardMatchingInstances(String routingKey,
                                    EntityModel childEntity) {
        this.routingKey = routingKey;
        this.childEntity = childEntity;
    }

    @Override
    public ForwardingMode getInstance(Supplier<ForwardingMode> forwardingModeConstructor) {
        return forwardingModeConstructor.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> boolean forwardMessage(T message, E target) {
        Property routingProperty = getProperty(message.getPayloadType(), routingKey());

        Object routingValue = routingProperty.getValue(message.getPayload());
        Object identifier = childEntity.getIdentifier(target);

        return Objects.equals(routingValue, identifier);
    }

    private String routingKey() {
        return Objects.equals(routingKey, EMPTY_STRING) ? childEntity.routingKey() : routingKey;
    }
}
