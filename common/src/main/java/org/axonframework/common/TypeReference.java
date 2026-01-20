/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.common;

import jakarta.annotation.Nonnull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Represents a reference to a type of component, allowing for generic types to be specified without casting errors.
 *
 * @param <E> The type of the component.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public abstract class TypeReference<E> {

    protected final Type type;

    protected TypeReference() {
        Type superClass = this.getClass().getGenericSuperclass();
        if (superClass instanceof Class) {
            throw new IllegalArgumentException(
                    "Internal error: TypeReference constructed without actual type information");
        } else {
            this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        }
    }

    private TypeReference(@Nonnull Type type) {
        this.type = Objects.requireNonNull(type, "The given type may not be null.");
    }

    /**
     * Constructs a {@code TypeReference} for the given {@code clazz}.
     *
     * @param clazz The clazz of the {@code TypeReference} under construction.
     * @param <C>   The clazz this {@code TypeReference} reflects.
     * @return A new {@code TypeReference} instance of the given {@code clazz}.
     */
    public static <C> TypeReference<C> fromClass(@Nonnull Class<C> clazz) {
        return new TypeReference<>(clazz) {
        };
    }

    /**
     * Constructs a {@code TypeReference} for the given {@code type}.
     *
     * @param type The type of the {@code TypeReference} under construction.
     * @param <C>  The type this {@code TypeReference} reflects.
     * @return A new {@code TypeReference} instance of the given {@code type}.
     */
    public static <C> TypeReference<C> fromType(@Nonnull Type type) {
        return new TypeReference<>(type) {
        };
    }

    /**
     * Returns the class of the type of the component represented by this {@code TypeReference}.
     *
     * @return The class of the component.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public Class<E> getTypeAsClass() {
        if (type instanceof Class<?> clazz) {
            return (Class<E>) clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return (Class<E>) parameterizedType.getRawType();
        }
        throw new IllegalArgumentException(
                "Internal error: TypeReference constructed with unsupported type: %s".formatted(type)
        );
    }

    /**
     * Returns the type of the component represented by this {@code TypeReference}.
     *
     * @return The type of the component.
     */
    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeReference<?> that = (TypeReference<?>) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }
}
