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
 * Marker interface for messages that have special serialization awareness. Typically, implementations will optimize
 * the serialization process by reusing serialized formats across invocations. This is particularly useful in cases
 * where an object needs to be serialized more than once using the same serializer.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface SerializationAware {

    /**
     * Serialize the payload of this message using given <code>serializer</code>, using given
     * <code>expectedRepresentation</code>. This method <em>should</em> return the same SerializedObject instance when
     * invoked multiple times using the same serializer.
     *
     * @param serializer             The serializer to serialize payload with
     * @param expectedRepresentation The type of data to serialize to
     * @param <T>                    The type of data to serialize to
     * @return a SerializedObject containing the serialized representation of the message's payload
     */
    <T> SerializedObject<T> serializePayload(Serializer serializer, Class<T> expectedRepresentation);

    /**
     * Serialize the meta data of this message using given <code>serializer</code>, using given
     * <code>expectedRepresentation</code>. This method <em>should</em> return the same SerializedObject instance when
     * invoked multiple times using the same serializer.
     *
     * @param serializer             The serializer to serialize meta data with
     * @param expectedRepresentation The type of data to serialize to
     * @param <T>                    The type of data to serialize to
     * @return a SerializedObject containing the serialized representation of the message's meta data
     */
    <T> SerializedObject<T> serializeMetaData(Serializer serializer, Class<T> expectedRepresentation);
}
