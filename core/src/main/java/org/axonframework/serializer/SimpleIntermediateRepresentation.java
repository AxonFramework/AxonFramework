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
 * Implementation of the IntermediateRepresentation that takes all properties as constructor parameters.
 *
 * @param <T> The type of representation used for the contents
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleIntermediateRepresentation<T> implements IntermediateRepresentation<T> {
    private final SerializedType type;
    private final Class<T> contentType;
    private final T contents;

    /**
     * Initializes a SimpleIntermediateRepresentation with given <code>type</code>, <code>contents</code> and
     * <code>contents</code>.
     *
     * @param type        The serialized type for this representation
     * @param contentType The data type of the contents
     * @param contents    The actual contents
     */
    public SimpleIntermediateRepresentation(SerializedType type, Class<T> contentType, T contents) {
        this.type = type;
        this.contentType = contentType;
        this.contents = contents;
    }

    @Override
    public Class<T> getContentType() {
        return contentType;
    }

    @Override
    public SerializedType getType() {
        return type;
    }

    @Override
    public T getData() {
        return contents;
    }
}
