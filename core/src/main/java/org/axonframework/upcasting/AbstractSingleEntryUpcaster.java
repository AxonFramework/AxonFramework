/*
 * Copyright (c) 2010-2013. Axon Framework
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
import org.axonframework.serializer.SimpleSerializedObject;

import java.util.Collections;
import java.util.List;

/**
 * Abstract implementation of an upcaster that only needs to convert one serialized object to another using the same
 * representation.
 * <p/>
 * This class is not suitable when an upcaster needs to convert a single serialized object to multiple new serialized
 * objects, or when the output representation type is not the same as the expected input representation type.
 *
 * @param <T> The data format that this upcaster uses to represent the event
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractSingleEntryUpcaster<T> implements Upcaster<T> {

    @Override
    public final List<SerializedObject<?>> upcast(SerializedObject<T> intermediateRepresentation,
                                                  List<SerializedType> expectedTypes, UpcastingContext context) {
        final T upcastObject = doUpcast(intermediateRepresentation, context);
        if (upcastObject == null) {
            return Collections.emptyList();
        }
        return Collections.<SerializedObject<?>>singletonList(
                new SimpleSerializedObject<T>(upcastObject, expectedRepresentationType(), expectedTypes.get(0)));
    }

    @Override
    public final List<SerializedType> upcast(SerializedType serializedType) {
        final SerializedType upcastType = doUpcast(serializedType);
        if (upcastType == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(upcastType);
    }

    /**
     * Upcasts the given <code>intermediateRepresentation</code> into zero or more other representations. The returned
     * list of Serialized Objects must match the given list of serialized types.
     * <p/>
     * This method may return <code>null</code> to indicate a deprecated object.
     *
     * @param intermediateRepresentation The representation of the object to upcast
     * @param context                    An instance describing the context of the object to upcast
     * @return the new representation of the object
     */
    protected abstract T doUpcast(SerializedObject<T> intermediateRepresentation,
                                  UpcastingContext context);

    /**
     * Upcast the given <code>serializedType</code> into its new format. Generally, this involves increasing the
     * revision. Sometimes, it is also necessary to alter the type's name (in case of a renamed class, for example).
     * <p/>
     * If <code>null</code> is returned, the Serialized object of this type is considered deprecated and will not be
     * subject to upcasting.
     *
     * @param serializedType The serialized type to upcast
     * @return the upcast serialized type
     */
    protected abstract SerializedType doUpcast(SerializedType serializedType);
}
