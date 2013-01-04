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

import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.Serializer;

import java.util.ArrayList;
import java.util.List;

/**
 * UpcasterChain implementation that delays the actual upcasting until the data inside the returned SerializedObject is
 * actually fetched. This makes this implementation perform better in cases where not all events are used.
 * <p/>
 * <em>Ordering guarantees:</em><br/>
 * This upcasterChain only guarantees that for each of the configured upcasters, all previous upcasters have had the
 * opportunity to upcast the SerializedObject first. This UpcasterChain does <em>not</em> guarantee that, for each
 * upcaster, all events are upcast in the order they are provided in the stream.
 * <p/>
 * Note that this implementation is not suitable if you require each upcaster to upcast all events in a stream in
 * order. See {@link SimpleUpcasterChain} instead for these cases.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class LazyUpcasterChain extends AbstractUpcasterChain {

    /**
     * Initializes the UpcasterChain with given <code>upcasters</code> and a {@link ChainingConverterFactory} to
     * convert between content types.
     *
     * @param upcasters the upcasters to form the chain
     */
    public LazyUpcasterChain(List<Upcaster> upcasters) {
        this(new ChainingConverterFactory(), upcasters);
    }

    /**
     * Initializes the UpcasterChain with given <code>serializer</code> and <code>upcasters</code>. The
     * <code>serializer</code> is used to fetch the ConverterFactory instance it uses. This ConverterFactory is
     * generally adapted to the exact form of serialization used by the serializer.
     *
     * @param serializer The serializer used to serialize the data
     * @param upcasters  The upcasters to form this chain
     */
    public LazyUpcasterChain(Serializer serializer, List<Upcaster> upcasters) {
        super(serializer.getConverterFactory(), upcasters);
    }

    /**
     * Initializes the UpcasterChain with given <code>converterFactory</code> and <code>upcasters</code>.
     *
     * @param converterFactory The factory providing the converters to convert between content types
     * @param upcasters        The upcasters to form this chain
     */
    public LazyUpcasterChain(ConverterFactory converterFactory, List<Upcaster> upcasters) {
        super(converterFactory, upcasters);
    }

    @Override
    protected <T> List<SerializedObject<?>> doUpcast(Upcaster<T> upcaster,
                                                     SerializedObject<?> sourceObject,
                                                     List<SerializedType> targetTypes,
                                                     UpcastingContext context) {
        LazyUpcastObject<T> lazyUpcastObject = new LazyUpcastObject<T>(sourceObject, targetTypes, upcaster, context);
        List<SerializedObject<?>> upcastObjects = new ArrayList<SerializedObject<?>>(targetTypes.size());
        int t = 0;
        for (SerializedType serializedType : targetTypes) {
            upcastObjects.add(new LazyUpcastingSerializedObject<T>(t, lazyUpcastObject, serializedType));
            t++;
        }
        return upcastObjects;
    }

    private class LazyUpcastObject<T> {

        private final SerializedObject<?> serializedObject;
        private final List<SerializedType> upcastTypes;
        private final Upcaster<T> currentUpcaster;
        private volatile List<SerializedObject<?>> upcastObjects = null;
        private final UpcastingContext properties;

        public LazyUpcastObject(SerializedObject<?> serializedObject, List<SerializedType> upcastTypes,
                                Upcaster<T> currentUpcaster, UpcastingContext properties) {
            this.serializedObject = serializedObject;
            this.upcastTypes = upcastTypes;
            this.currentUpcaster = currentUpcaster;
            this.properties = properties;
        }

        public List<SerializedObject<?>> getUpcastSerializedObjects() {
            if (upcastObjects == null) {
                SerializedObject<T> converted = ensureCorrectContentType(serializedObject,
                                                                         currentUpcaster.expectedRepresentationType());
                upcastObjects = currentUpcaster.upcast(converted, upcastTypes, properties);
            }
            return upcastObjects;
        }
    }

    private static class LazyUpcastingSerializedObject<T> implements SerializedObject {

        private final int index;
        private final LazyUpcastObject<T> lazyUpcastObject;
        private final SerializedType type;

        public LazyUpcastingSerializedObject(int index, LazyUpcastObject<T> lazyUpcastObject, SerializedType type) {
            this.index = index;
            this.lazyUpcastObject = lazyUpcastObject;
            this.type = type;
        }

        @Override
        public Class<?> getContentType() {
            return lazyUpcastObject.getUpcastSerializedObjects().get(index).getContentType();
        }

        @Override
        public SerializedType getType() {
            return type;
        }

        @Override
        public Object getData() {
            return lazyUpcastObject.getUpcastSerializedObjects().get(index).getData();
        }
    }
}
