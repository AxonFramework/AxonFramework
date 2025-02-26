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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.ModelIdResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * Implementation of a {@link ModelIdResolver} that inspects the payload of a {@link Message} for an identifier. The
 * identifier is resolved by looking for a field or method with the given {@code property} name. Methods will
 * automatically be resolved by looking for a method with the name {@code get<Property>} or {@code <Property>}.
 * <p>
 * For performance reasons, the resolved identifier members are cached per payload type.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class PropertyBasedModelIdResolver implements ModelIdResolver<Object> {

    private final Map<Class<?>, Member> memberCache = new ConcurrentHashMap<>();

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
        Member member = getMember(payload.getClass());
        return ReflectionUtils.getMemberValue(member, payload);
    }

    /**
     * Returns the member that represents the identifier of the given {@code payloadClass}.
     *
     * @param payloadClass The class of the payload
     * @return The member that represents the identifier
     */
    private Member getMember(Class<?> payloadClass) {
        return memberCache.computeIfAbsent(payloadClass, this::findMember);
    }

    /**
     * Finds the member that represents the identifier of the given {@code payloadClass}.
     *
     * @param payloadClass The class of the payload
     * @return The member that represents the identifier
     */
    private Member findMember(Class<?> payloadClass) {

        Optional<Field> foundField = StreamSupport.stream(ReflectionUtils.fieldsOf(payloadClass).spliterator(), false)
                                                  .filter(f -> f.getName().equals(property))
                                                  .findFirst();
        if (foundField.isPresent()) {
            return foundField.get();
        }

        // No field found, try to find a method
        String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        Optional<Method> foundMethod = StreamSupport.stream(ReflectionUtils.methodsOf(payloadClass).spliterator(),
                                                            false)
                                                    .filter(m -> m.getName().equals(getterName) || m.getName()
                                                                                                    .equals(property))
                                                    .findFirst();
        if (foundMethod.isPresent()) {
            return foundMethod.get();
        }

        throw new IdentifierFieldNotFoundException(property, payloadClass);
    }
}