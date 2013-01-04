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
import org.axonframework.serializer.ContentTypeConverter;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Abstract implementation of the UpcasterChain interface. This implementation takes care of the iterative process and
 * provides utility functions to convert content types.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractUpcasterChain implements UpcasterChain {

    private final List<Upcaster> upcasters;
    private final ConverterFactory converterFactory;

    /**
     * Initializes the UpcasterChain with given <code>upcasters</code> and a {@link ChainingConverterFactory} to
     * convert between content types.
     *
     * @param upcasters the upcasters to form the chain
     */
    protected AbstractUpcasterChain(List<Upcaster> upcasters) {
        this(new ChainingConverterFactory(), upcasters);
    }

    /**
     * Initializes the UpcasterChain with given <code>converterFactory</code> and <code>upcasters</code>.
     *
     * @param converterFactory The factory providing the converters to convert between content types
     * @param upcasters        The upcasters to form this chain
     */
    protected AbstractUpcasterChain(ConverterFactory converterFactory, List<Upcaster> upcasters) {
        this.upcasters = upcasters;
        this.converterFactory = converterFactory;
    }

    @Override
    public List<SerializedObject> upcast(SerializedObject serializedObject, UpcastingContext upcastingContext) {
        if (upcasters.isEmpty()) {
            return singletonList(serializedObject);
        }
        Iterator<Upcaster> upcasterIterator = upcasters.iterator();
        return upcastInternal(singletonList(serializedObject), upcasterIterator, upcastingContext);
    }

    /**
     * Converts, if necessary, the given <code>serializedObject</code>, and returns a SerializedObject with given
     * <code>expectedContentType</code>. If the <code>serializedObject</code> already contains the given
     * <code>expectedContentType</code>, it is returned as-is.
     *
     * @param serializedObject    The object to convert
     * @param expectedContentType The content type of the SerializedObject to return
     * @param <S>                 The content type of the provided SerializedObject
     * @param <T>                 The content type of the SerializedObject to return
     * @return a SerializedObject containing data in the expected content type
     */
    protected <S, T> SerializedObject<T> ensureCorrectContentType(SerializedObject<S> serializedObject,
                                                                  Class<T> expectedContentType) {
        if (!expectedContentType.isAssignableFrom(serializedObject.getContentType())) {
            ContentTypeConverter<S, T> converter = converterFactory.getConverter(serializedObject.getContentType(),
                                                                                 expectedContentType);
            return converter.convert(serializedObject);
        }
        //noinspection unchecked
        return (SerializedObject<T>) serializedObject;
    }

    /**
     * Performs the actual upcasting by the given <code>upcaster</code> on the given <code>sourceObject</code>. The
     * returned list of serialized object must represent the upcast version of the given <code>sourceObject</code>.
     * <p/>
     * Each item in the returned List of SerializedObject must match the given list of <code>targetTypes</code>. These
     * types are returned by the invocation of {@link Upcaster#upcast(org.axonframework.serializer.SerializedType)}.
     *
     * @param upcaster              The upcaster to perform the upcasting with
     * @param sourceObject          The SerializedObject to upcast
     * @param targetTypes           The types expected in the returned List of SerializedObject
     * @param context The container of properties of the Domain Event Message being upcast
     * @param <T>                   The representation type expected by the upcaster
     * @return The List of SerializedObject representing the upcast <code>sourceObject</code>
     */
    protected abstract <T> List<SerializedObject<?>> doUpcast(Upcaster<T> upcaster, SerializedObject<?> sourceObject,
                                                              List<SerializedType> targetTypes,
                                                              UpcastingContext context);

    private List<SerializedObject> upcastInternal(List<SerializedObject> serializedObjects,
                                                  Iterator<Upcaster> upcasterIterator,
                                                  UpcastingContext context) {
        if (!upcasterIterator.hasNext()) {
            return serializedObjects;
        }
        List<SerializedObject> upcastObjects = new ArrayList<SerializedObject>();
        Upcaster<?> currentUpcaster = upcasterIterator.next();
        for (SerializedObject serializedObject : serializedObjects) {
            if (currentUpcaster.canUpcast(serializedObject.getType())) {
                List<SerializedType> upcastTypes = currentUpcaster.upcast(serializedObject.getType());
                upcastObjects.addAll(doUpcast(currentUpcaster, serializedObject, upcastTypes, context));
            } else {
                upcastObjects.add(serializedObject);
            }
        }
        return upcastInternal(upcastObjects, upcasterIterator, context);
    }
}
