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

package org.axonframework.serialization;

import jakarta.annotation.Nonnull;

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
public interface Converter {

    /**
     * Indicates whether this {@code Converter} is capable of converting the given {@code sourceType} to the
     * {@code targetType}.
     *
     * @param sourceType The type of data to convert from.
     * @param targetType The type of data to convert to.
     * @return {@code true} if conversion is possible, {@code false} otherwise.
     */
    boolean canConvert(@Nonnull Class<?> sourceType, @Nonnull Class<?> targetType);

    /**
     * Converts the given {@code original} object into an object of the given {@code targetType}.
     *
     * @param original   The value to convert.
     * @param targetType The type to convert the given {@code original} into.
     * @param <T>        The target data type.
     * @param <S>        The source data type.
     * @return A converted version of the given {@code original} into the given {@code targetType}.
     */
    default <S, T> T convert(@Nonnull S original, @Nonnull Class<T> targetType) {
        //noinspection unchecked
        return convert(original, (Class<S>) original.getClass(), targetType);
    }

    /**
     * Converts the given {@code original} object into an object of the given {@code targetType}, using the given
     * {@code sourceType} to deduce the conversion path.
     *
     * @param original   The value to convert.
     * @param sourceType The type of data to convert.
     * @param targetType The type to convert the given {@code original} into.
     * @param <T>        The target data type.
     * @param <S>        The source data type.
     * @return A converted version of the given {@code original} into the given {@code targetType}.
     */
    <S, T> T convert(@Nonnull S original, @Nonnull Class<S> sourceType, @Nonnull Class<T> targetType);

    /**
     * Converts the data format of the given {@code original} IntermediateRepresentation to the target data type.
     *
     * @param original   The source to convert
     * @param targetType The type of data to convert to
     * @param <T>        the target data type
     * @return the converted representation
     * @deprecated As we will stop using the {@link SerializedObject}.
     */
    @Deprecated(forRemoval = true, since = "5.0.0")
    @SuppressWarnings("unchecked")
    default <T, S> SerializedObject<T> convert(SerializedObject<S> original, Class<T> targetType) {
        if (original.getContentType().equals(targetType)) {
            return (SerializedObject<T>) original;
        }
        return new SimpleSerializedObject<>(convert(original.getData(), original.getContentType(), targetType),
                                            targetType, original.getType());
    }
}
