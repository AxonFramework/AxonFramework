/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.upcasting;

import org.axonframework.serializer.SerializedObject;

import java.util.List;

/**
 * Represents a series of upcasters which are combined to upcast a {@link org.axonframework.serializer.SerializedObject}
 * to the most recent revision of that payload. The intermediate representation required by each of the upcasters is
 * converted using converters provided by a converterFactory.
 * <p/>
 * Upcasters for different object types may be merged into a single chain, as long as the order of related upcasters
 * can be guaranteed.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface UpcasterChain {

    /**
     * Pass the given <code>serializedObject</code> through the chain of upcasters. The result is a list of zero or
     * more serializedObjects representing the latest revision of the payload object.
     *
     * @param serializedObject  the serialized object to upcast
     * @param upcastingContext the container of properties of the Message transporting the object being upcast
     * @return the upcast SerializedObjects
     */
    List<SerializedObject> upcast(SerializedObject serializedObject, UpcastingContext upcastingContext);
}
