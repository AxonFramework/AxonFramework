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

import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.Serializer;

import java.util.Arrays;
import java.util.Collections;
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
 * @author Frank Versnel
 * @since 2.0
 */
public class SimpleUpcasterChain extends AbstractUpcasterChain {

    /**
     * Represents an empty UpcasterChain.
     */
    public static final UpcasterChain EMPTY = new SimpleUpcasterChain(Collections.<Upcaster>emptyList());

    /**
     * Initializes the UpcasterChain with given <code>upcasters</code> and a {@link
     * org.axonframework.serializer.ChainingConverterFactory} to convert between content types.
     *
     * @param upcasters the upcasters to form the chain
     */
    public SimpleUpcasterChain(List<Upcaster> upcasters) {
        super(upcasters);
    }

    /**
     * Initializes the UpcasterChain with given <code>serializer</code> and <code>upcasters</code>. The
     * <code>serializer</code> is used to fetch the ConverterFactory instance it uses. This ConverterFactory is
     * generally adapted to the exact form of serialization used by the serializer.
     *
     * @param serializer The serializer used to serialize the data
     * @param upcasters  The upcasters to form this chain
     */
    public SimpleUpcasterChain(Serializer serializer, List<Upcaster> upcasters) {
        super(serializer.getConverterFactory(), upcasters);
    }

    /**
     * Initializes the UpcasterChain with given <code>converterFactory</code> and <code>upcasters</code>.
     *
     * @param converterFactory The factory providing the converters to convert between content types
     * @param upcasters        The upcasters to form this chain
     */
    public SimpleUpcasterChain(ConverterFactory converterFactory, List<Upcaster> upcasters) {
        super(converterFactory, upcasters);
    }

    /**
     * Initialize a chain of the given <code>upcasters</code> and using the given <code>converterFactory</code> to
     * create converters for the intermediate representations used by the upcasters.
     *
     * @param converterFactory The factory providing ContentTypeConverter instances
     * @param upcasters        The upcasters forming the chain (in given order)
     */
    public SimpleUpcasterChain(ConverterFactory converterFactory, Upcaster... upcasters) {
        this(converterFactory, Arrays.asList(upcasters));
    }

    @Override
    protected <T> List<SerializedObject<?>> doUpcast(Upcaster<T> upcaster, SerializedObject<?> sourceObject,
                                                     List<SerializedType> targetTypes,
                                                     UpcastingContext context) {
        SerializedObject<T> converted = ensureCorrectContentType(sourceObject,
                                                                 upcaster.expectedRepresentationType());
        return upcaster.upcast(converted, targetTypes, context);
    }
}
