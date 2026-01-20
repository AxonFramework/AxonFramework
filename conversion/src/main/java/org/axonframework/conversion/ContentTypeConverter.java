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

package org.axonframework.conversion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Interface describing a mechanism that converts an object from a specified {@link #expectedSourceType() source type} to
 * the defined {@link #targetType() target type}.
 *
 * @param <S> The expected source type for this {@code ContentTypeConverter} to {@link #convert(Object) convert}.
 * @param <T> The output type of this {@code ContentTypeConverter's} {@link #convert(Object) convert} method.
 * @author Allard Buijze
 * @since 2.0.0
 */
public interface ContentTypeConverter<S, T> {

    /**
     * Returns the expected type of input data for this {@code ContentTypeConverter} to {@link #convert(Object)}.
     *
     * @return The expected type of input data for this {@code ContentTypeConverter} to {@link #convert(Object)}.
     */
    @Nonnull
    Class<S> expectedSourceType();

    /**
     * Returns the type of output for this {@code ContentTypeConverter} to {@link #convert(Object)} into.
     *
     * @return The type of output for this {@code ContentTypeConverter} to {@link #convert(Object)} into.
     */
    @Nonnull
    Class<T> targetType();

    /**
     * Converts the given {@code input} object of generic type {@code S} into an object of generic type {@code T}.
     *
     * @param input The object of generic type {@code S} to convert into an object of generic type {@code T}.
     * @return The converted version of the given {@code input} in type {@code T}.
     */
    @Nullable
    T convert(@Nullable S input);
}
