/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling.command.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.EntityIdResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a {@link EntityIdResolver} that inspects the payload of a {@link Message} for an identifier. The
 * identifier is resolved by looking for a field or method with the given {@code property} name. Methods will
 * automatically be resolved by looking for a method with the name {@code get<Property>} or {@code <Property>}.
 * <p>
 * This field or method needs to have or return a non-null value. If a {@code null} value is found, a
 * {@link NullEntityIdInPayloadException} is thrown. If no member is found at all, a
 * {@link TargetEntityIdMemberMismatchException} is thrown.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class PropertyBasedEntityIdResolver implements EntityIdResolver<Object> {

    private final Map<Class<?>, Property<Object>> propertyCache = new ConcurrentHashMap<>();
    private final String property;

    /**
     * Initialize the resolver with the given {@code property} name.
     *
     * @param property The name of the property to resolve the identifier from.
     */
    public PropertyBasedEntityIdResolver(@Nonnull String property) {
        Assert.nonEmpty(property, "Property cannot be empty or null");
        this.property = property;
    }

    @Nonnull
    @Override
    public Object resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        Object payload = message.getPayload();
        Class<?> payloadClass = payload.getClass();
        var property = propertyCache.computeIfAbsent(payloadClass, this::getObjectProperty);
        Object value = property.getValue(payload);
        if (value == null) {
            throw new NullEntityIdInPayloadException(payloadClass);
        }
        return value;
    }

    private Property<Object> getObjectProperty(Class<?> payloadClass) {
        Property<Object> foundProperty = PropertyAccessStrategy.getProperty(payloadClass, property);
        if (foundProperty == null) {
            throw new TargetEntityIdMemberMismatchException(property, payloadClass);
        }
        return foundProperty;
    }
}