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

package org.axonframework.serialization.upcasting;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;

import java.util.List;

/**
 * Extension of the Upcaster interface that allows type upcasting to be based on the contents of the
 * serialized object. UpcasterChain implementations should invoke the {@link #upcast(org.axonframework.serialization.SerializedType,
 * org.axonframework.serialization.SerializedObject)} on upcasters that implement this interface, instead of {@link
 * #upcast(org.axonframework.serialization.SerializedType)}
 *
 * @param <T> The data format that this upcaster uses to represent the event
 * @author Allard Buijze
 * @since 2.2
 */
public interface ExtendedUpcaster<T> extends Upcaster<T> {

    /**
     * Upcast the given <code>serializedType</code> into its new format. Generally, this involves increasing the
     * revision. Sometimes, it is also necessary to alter the type's name (in case of a renamed class, for example).
     * The order and the size of the list returned has to match with the order and size of the list of the upcast
     * IntermediateRepresentations by this upcaster.
     * <p/>
     * Unlike the {@link #upcast(org.axonframework.serialization.SerializedType)} method, this gives you access to the
     * serialized object to upcast. This may be used to choose the SerializedType based on the contents of a Message's
     * payload.
     * <p/>
     * Implementations aware of the ExtendedUpcaster interface must use this method instead of {@link
     * #upcast(org.axonframework.serialization.SerializedType)}
     *
     * @param serializedType             The serialized type to upcast
     * @param intermediateRepresentation The intermediate representation of the object to define the type for
     * @return the upcast serialized type
     */
    List<SerializedType> upcast(SerializedType serializedType, SerializedObject<T> intermediateRepresentation);

    /**
     * {@inheritDoc}
     * <p/>
     * Implementing this method is optional.
     *
     * @throws java.lang.UnsupportedOperationException if the implementation requires the intermediate representation
     * to upcast the serialized type.
     */
    @Override
    List<SerializedType> upcast(SerializedType serializedType);
}
