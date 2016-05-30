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
 * Abstract implementation of the ContentTypeConverter for convenience purposes. It implements the {@link
 * #convert(org.axonframework.serialization.SerializedObject)} method, based on information available through the other
 * methods.
 *
 * @param <S> The source data type representing the serialized object
 * @param <T> The target data type representing the serialized object
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractContentTypeConverter<S, T> implements ContentTypeConverter<S, T> {

    @Override
    public SerializedObject<T> convert(SerializedObject<S> original) {
        return new SimpleSerializedObject<>(convert(original.getData()), targetType(), original.getType());
    }
}
