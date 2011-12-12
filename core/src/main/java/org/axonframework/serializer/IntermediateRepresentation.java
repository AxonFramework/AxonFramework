/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.serializer;

/**
 * Interface describing the intermediate representation of a serialized object, which is used by Upcasters to change
 * the data structure, to reflect changes made in the structure of serialized objects.
 *
 * @param <T> The type of representation used for the contents
 * @author Allard Buijze
 * @since 2.0
 */
public interface IntermediateRepresentation<T> {

    /**
     * Returns the type of this representation's data.
     *
     * @return the type of this representation's data
     */
    Class<T> getContentType();

    /**
     * Returns the description of the serialized object, represented by the data.
     *
     * @return the description of the serialized object, represented by the data
     */
    SerializedType getType();

    /**
     * Returns a view of the actual data contained in this representation. This may either be a copy, or access to
     * mutable internal state.
     *
     * @return a view of the actual data contained in this representation
     */
    T getData();
}
