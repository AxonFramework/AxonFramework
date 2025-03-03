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
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.ModelIdentifierResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * Implementation of a {@link ModelIdentifierResolver} that inspects the payload of a {@link Message} for an identifier. The
 * identifier is resolved by looking for a field or method with the given {@code property} name. Methods will
 * automatically be resolved by looking for a method with the name {@code get<Property>} or {@code <Property>}.
 * <p>
 * For performance reasons, the resolved identifier members are cached per payload type.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class PropertyBasedModelIdResolver implements ModelIdentifierResolver<Object> {

    private final Map<Class<?>, Property<Object>> memberCache = new ConcurrentHashMap<>();

    private final String property;

    /**
     * Initialize the resolver with the given {@code property} name.
     *
     * @param property The name of the property to resolve the identifier from
     */
    public PropertyBasedModelIdResolver(String property) {
        this.property = property;
    }

    @Nullable
    @Override
    public Object resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        Object payload = message.getPayload();
        return getMember(payload.getClass()).getValue(payload);
    }

    /**
     * Returns the member that represents the identifier of the given {@code payloadClass}.
     *
     * @param payloadClass The class of the payload
     * @return The member that represents the identifier
     */
    private Property<Object> getMember(Class<?> payloadClass) {
        return memberCache.computeIfAbsent(payloadClass, (n) ->
                PropertyAccessStrategy.getProperty(payloadClass, property)
        );
    }
}