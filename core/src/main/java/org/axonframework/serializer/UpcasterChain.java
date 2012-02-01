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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a series of upcasters which are combined to upcast a {@link SerializedObject} to the most recent revision
 * of that payload. The intermediate representation required by each of the upcasters is converted using converters
 * provided by a converterFactory.
 * <p/>
 * Upcasters for different object types may be merged into a single chain, as long as the order of related upcasters
 * can be guaranteed.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UpcasterChain {

    private final List<Upcaster> upcasters;
    private final ConverterFactory converterFactory;

    /**
     * Initialize a chain of the given <code>upcasters</code> and using the given <code>converterFactory</code> to
     * create converters for the intermediate representations used by the upcasters.
     *
     * @param converterFactory The factory providing ContentTypeConverter instances
     * @param upcasters        The upcasters forming the chain (in given order)
     */
    public UpcasterChain(ConverterFactory converterFactory, List<Upcaster> upcasters) {
        this.converterFactory = converterFactory;
        this.upcasters = new ArrayList<Upcaster>(upcasters);
    }

    /**
     * Initialize a chain of the given <code>upcasters</code> and using the given <code>converterFactory</code> to
     * create converters for the intermediate representations used by the upcasters.
     *
     * @param converterFactory The factory providing ContentTypeConverter instances
     * @param upcasters        The upcasters forming the chain (in given order)
     */
    public UpcasterChain(ConverterFactory converterFactory, Upcaster... upcasters) {
        this(converterFactory, Arrays.asList(upcasters));
    }

    /**
     * Pass the given <code>serializedObject</code> through the chain of upcasters. The result is a serializedObject
     * representing the latest revision of the payload object.
     *
     * @param serializedObject the serialized object to upcast
     * @return the upcast SerializedObject
     */
    @SuppressWarnings({"unchecked"})
    public SerializedObject upcast(SerializedObject<?> serializedObject) {
        SerializedObject<?> current = serializedObject;
        for (Upcaster upcaster : upcasters) {
            if (upcaster.canUpcast(current.getType())) {
                current = ensureCorrectContentType(current, upcaster.expectedRepresentationType());
                current = upcaster.upcast(current);
            }
        }
        return current;
    }

    /**
     * Upcast the given <code>serializedType</code> to represent the type of the latest revision of that object.
     * Changes in class names or packaging are typically reflected in a change in the SerializedType definition/
     *
     * @param serializedType The serialized type to upcast
     * @return The last known revision of the SerializedType
     */
    public SerializedType upcast(SerializedType serializedType) {
        SerializedType current = serializedType;
        for (Upcaster upcaster : upcasters) {
            if (upcaster.canUpcast(current)) {
                current = upcaster.upcast(current);
            }
        }
        return current;
    }

    @SuppressWarnings({"unchecked"})
    private <T> SerializedObject<T> ensureCorrectContentType(SerializedObject<?> current,
                                                                       Class<T> expectedContentType) {
        if (!expectedContentType.isAssignableFrom(current.getContentType())) {
            ContentTypeConverter converter = converterFactory.getConverter(current.getContentType(),
                                                                           expectedContentType);
            current = converter.convert(current);
        }
        return (SerializedObject<T>) current;
    }

}
