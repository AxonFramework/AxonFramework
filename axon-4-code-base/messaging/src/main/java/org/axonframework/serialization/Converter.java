/*
 * Copyright (c) 2010-2023. Axon Framework
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

/**
 * Interface describing a mechanism that can convert data from one to another type.
 *
 * @author Rene de Waele
 */
public interface Converter {

    /**
     * Indicates whether this converter is capable of converting the given {@code sourceType} to the {@code targetType}.
     *
     * @param sourceType The type of data to convert from
     * @param targetType The type of data to convert to
     * @return {@code true} if conversion is possible, {@code false} otherwise
     */
    boolean canConvert(Class<?> sourceType, Class<?> targetType);

    /**
     * Converts the given object into another.
     *
     * @param original   the value to convert
     * @param targetType The type of data to convert to
     * @param <T>        the target data type
     * @return the converted value
     */
    default <T> T convert(Object original, Class<T> targetType) {
        return convert(original, original.getClass(), targetType);
    }

    /**
     * Converts the given object into another using the source type to find the conversion path.
     *
     * @param original   the value to convert
     * @param sourceType the type of data to convert
     * @param targetType The type of data to convert to
     * @param <T>        the target data type
     * @return the converted value
     */
    <T> T convert(Object original, Class<?> sourceType, Class<T> targetType);

    /**
     * Converts the data format of the given {@code original} IntermediateRepresentation to the target data type.
     *
     * @param original   The source to convert
     * @param targetType The type of data to convert to
     * @param <T>        the target data type
     * @return the converted representation
     */
    @SuppressWarnings("unchecked")
    default <T> SerializedObject<T> convert(SerializedObject<?> original, Class<T> targetType) {
        if (original.getContentType().equals(targetType)) {
            return (SerializedObject<T>) original;
        }
        return new SimpleSerializedObject<>(convert(original.getData(), original.getContentType(), targetType),
                                            targetType, original.getType());
    }

}
