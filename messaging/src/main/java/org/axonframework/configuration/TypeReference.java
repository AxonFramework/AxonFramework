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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Represents a reference to a type of component, allowing for generic types to be specified without casting errors.
 *
 * @param <E> The type of the component.
 */
public abstract class TypeReference<E> {
    protected final Class<E> type;

    @SuppressWarnings("unchecked")
    protected TypeReference() {
        Type superClass = this.getClass().getGenericSuperclass();
        if (superClass instanceof Class) {
            throw new IllegalArgumentException("Internal error: TypeReference constructed without actual type information");
        } else {
            var type = ((ParameterizedType)superClass).getActualTypeArguments()[0];
            if(type instanceof Class<?> clazz) {
                this.type = (Class<E>) clazz;
            } else if (type instanceof ParameterizedType parameterizedType) {
                this.type = (Class<E>) parameterizedType.getRawType();
            } else {
                throw new IllegalArgumentException("Internal error: TypeReference constructed with unsupported type: " + type);
            }
        }
    }

    /**
     * Returns the type of the component represented by this {@code TypeReference}.
     *
     * @return the type of the component.
     */
    @Nonnull
    public Class<E> getType() {
        return type;
    }
}
