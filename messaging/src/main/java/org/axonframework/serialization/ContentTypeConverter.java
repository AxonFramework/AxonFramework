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
 * Interface describing a mechanism that converts the data type of IntermediateRepresentations of SerializedObjects for
 * Upcasters. Different upcasters may require different data type (e.g. {@code byte[]} or
 * {@code InputStream}), or may produce a different data type than they consume.
 *
 * @param <S> The expected source type
 * @param <T> The output type
 * @author Allard Buijze
 * @since 2.0
 */
public interface ContentTypeConverter<S, T> {

    /**
     * The expected type of input data.
     *
     * @return the expected data format in IntermediateRepresentation
     */
    Class<S> expectedSourceType();

    /**
     * The returned type of IntermediateRepresentation
     *
     * @return the output data format in IntermediateRepresentation
     */
    Class<T> targetType();

    /**
     * Converts the given object into another. Typically, these values are contained by a {@link SerializedObject}
     * instance.
     *
     * @param original the value to convert
     * @return the converted value
     */
    T convert(S original);
}
