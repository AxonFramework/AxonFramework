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
import org.axonframework.serializer.SerializedType;

import java.util.List;

/**
 * Interface for Upcasters. An upcaster is the mechanism used to convert deprecated (typically serialized) objects and
 * convert them into the current format. If the serializer itself is not able to cope with the changed formats
 * (XStream, for example, will allow for some changes by using aliases), the Upcaster will allow you to configure more
 * complex structural transformations.
 * <p/>
 * Upcasters work on intermediate representations of the object to upcast. In some cases, this representation is a byte
 * array, while in other cases an object structure is used. The Serializer is responsible for converting the
 * intermediate representation form to one that is compatible with the upcaster. For performance reasons, it is
 * advisable to ensure that all upcasters in the same chain (where one's output is another's input) use the same
 * intermediate representation type.
 *
 * @param <T> The data format that this upcaster uses to represent the event
 * @author Allard Buijze
 * @author Frank Versnel
 * @since 2.0
 */
public interface Upcaster<T> {

    /**
     * Indicates whether this upcaster is capable of upcasting the given <code>type</code>. Unless this method returns
     * <code>true</code>, the {@link #upcast(org.axonframework.serializer.SerializedType)} and {@link
     * #upcast(org.axonframework.serializer.SerializedType)} methods should not be
     * invoked on this Upcaster instance.
     *
     * @param serializedType The type under investigation
     * @return <code>true</code> if this upcaster can upcast the given serialized type, <code>false</code> otherwise.
     */
    boolean canUpcast(SerializedType serializedType);

    /**
     * Returns the type of intermediate representation this upcaster expects. The serializer must ensure the
     * intermediate representation is offered in a compatible format.
     *
     * @return the type of intermediate representation expected
     */
    Class<T> expectedRepresentationType();

    /**
     * Upcasts the given <code>intermediateRepresentation</code> into zero or more other representations. The returned
     * list of Serialized Objects must match the given list of serialized types.
     *
     * @param intermediateRepresentation The representation of the object to upcast
     * @param expectedTypes              The expected types of the returned serialized objects.
     * @param context                    An instance describing the context of the object to upcast
     * @return the new representations of the object
     */
    List<SerializedObject<?>> upcast(SerializedObject<T> intermediateRepresentation,
                                     List<SerializedType> expectedTypes, UpcastingContext context);

    /**
     * Upcast the given <code>serializedType</code> into its new format. Generally, this involves increasing the
     * revision. Sometimes, it is also necessary to alter the type's name (in case of a renamed class, for example).
     * The order and the size of the list returned has to match with the order and size of the list of the upcast
     * IntermediateRepresentations by this upcaster.
     *
     * @param serializedType The serialized type to upcast
     * @return the upcast serialized type
     */
    List<SerializedType> upcast(SerializedType serializedType);
}
