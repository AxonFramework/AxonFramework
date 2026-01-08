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
import org.axonframework.common.infra.DescribableComponent;

import java.lang.reflect.Type;

/**
 * Interface describing a mechanism that can convert data from one to another type.
 * <p>
 * Used when object are added/retrieved from a storage solution or put on/received from a network. Clear example of this
 * is the {@link org.axonframework.messaging.Message}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @since 3.0.0
 */
public interface Converter extends DescribableComponent {

    /**
     * Indicates whether this {@code Converter} is capable of converting the given {@code sourceType} to the
     * {@code targetType}.
     *
     * @param sourceType The type of data to convert from.
     * @param targetType The type of data to convert to.
     * @return {@code true} if conversion is possible, {@code false} otherwise.
     */
    default boolean canConvert(@Nonnull Class<?> sourceType, @Nonnull Class<?> targetType) {
        return canConvert(sourceType, (Type) targetType);
    }

    /**
     * Indicates whether this {@code Converter} is capable of converting the given {@code sourceType} to the
     * {@code targetType}.
     *
     * @param sourceType The type of data to convert from.
     * @param targetType The type of data to convert to.
     * @return {@code true} if conversion is possible, {@code false} otherwise.
     */
    boolean canConvert(@Nonnull Type sourceType, @Nonnull Type targetType);

    /**
     * Converts the given {@code input} object into an object of the given {@code targetType}.
     *
     * @param input      The value to convert.
     * @param targetType The type to convert the given {@code input} into.
     * @param <T>        The target data type.
     * @return A converted version of the given {@code input} into the given {@code targetType}.
     * @throws ConversionException If the {@code input} cannot be converted to the given {@code targetType}.
     */
    @Nullable
    default <T> T convert(@Nullable Object input, @Nonnull Class<T> targetType) {
        return convert(input, (Type) targetType);
    }

    /**
     * Converts the given {@code input} object into an object of the given {@code targetType}.
     *
     * @param input      The value to convert.
     * @param targetType The type to convert the given {@code input} into.
     * @param <T>        The target data type.
     * @return A converted version of the given {@code input} into the given {@code targetType}.
     * @throws ConversionException If the {@code input} cannot be converted to the given {@code targetType}.
     */
    @Nullable
    <T> T convert(@Nullable Object input, @Nonnull Type targetType);
}
