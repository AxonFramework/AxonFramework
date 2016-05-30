/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization;

/**
 * Interface describing a mechanism that provides instances of ContentTypeConverter for a given source and target data
 * type.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface ConverterFactory {


    /**
     * Indicates whether this factory contains a converter capable of converting the given
     * <code>sourceContentType</code> into the <code>targetContentType</code>.
     *
     * @param sourceContentType The type of source data for which a converter must be available
     * @param targetContentType The type of target data for which a converter must be available
     * @param <S>               The type of source data for which a converter must be available
     * @param <T>               The type of target data for which a converter must be available
     * @return <code>true</code> if a converter is available, otherwise <code>false</code>.
     */
    <S, T> boolean hasConverter(Class<S> sourceContentType, Class<T> targetContentType);

    /**
     * Returns a converter that is capable of converting IntermediateRepresentation object containing the given
     * <code>sourceContentType</code> to the given <code>targetContentType</code>.
     *
     * @param sourceContentType The type of data the converter accepts as input
     * @param targetContentType The type of data the converter produces
     * @param <S>               The source content type
     * @param <T>               The target content type
     * @return a converter capable of converting from the given <code>sourceContentType</code> to
     *         <code>targetContentType</code>
     *
     * @throws CannotConvertBetweenTypesException
     *          when no suitable converter can be found
     */
    <S, T> ContentTypeConverter<S, T> getConverter(Class<S> sourceContentType, Class<T> targetContentType);
}
