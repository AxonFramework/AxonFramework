/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.serialization.Converter;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An {@link Internal} class wrapping a {@link ConcurrentHashMap} of {@link Type} to a {@link CopyOnWriteArrayList} of
 * conversions. Useful to maintain a "conversion cache", for example by the
 * {@link GenericMessage#payloadAs(Type, Converter)} and {@link GenericMessage#withConvertedPayload(Type, Converter)}
 * operations.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
class ConversionCache {

    private final ConcurrentHashMap<Type, CopyOnWriteArrayList<ConverterConversionTuple>> conversionCache = new ConcurrentHashMap<>();

    /**
     * Converts the given {@code input} into the given {@code type} with the given {@code converter} <b>only</b> if this
     * conversion combination has not been performed before.
     * <p>
     * Will first validate if there are any previous conversions for the given {@code type}. When this is the case, will
     * search for previous conversions into the given {@code type} <b>by</b> the given {@code converter}.
     * <p>
     * If that's {@code true}, the previous conversion result will be returned. Whenever this is {@code false},
     * {@link Converter#convert(Object, Type)} will be invoked with the {@code input} and {@code type} respectively. The
     * conversion results is kept in this cache for subsequent invocations.
     * <p>
     * The result may be {@code null} since the result of {@link Converter#convert(Object, Type) conversion} can also be
     * {@code null}.
     *
     * @param convertedType The type to convert the given {@code input} into, <b>if</b> the given type-and-converter
     *                      combination has not been seen before.
     * @param converter     The converter to convert the given {@code input with}, <b>if</b> the given
     *                      type-and-converter combination has not been seen before.
     * @param input         The input to convert into the given {@code type} by the given {@code converter}, <b>if</b>
     *                      the given type-and-converter combination has not been seen before.
     * @param <T>           The generic type to return.
     * @return The converted {@code input}, either directly from this cache or as the result from
     * {@link Converter#convert(Object, Type) converting}.
     */
    @Nullable
    <T> T convertIfAbsent(@Nonnull Type convertedType,
                          @Nonnull Converter converter,
                          @Nullable Object input) {
        CopyOnWriteArrayList<ConverterConversionTuple> conversions =
                conversionCache.computeIfAbsent(convertedType, t -> new CopyOnWriteArrayList<>());

        for (ConverterConversionTuple conversionTuple : conversions) {
            Converter converterRef = conversionTuple.converterReference().get();
            if (converterRef == null) {
                // Converter no longer exists, so let's remove this entry
                conversions.remove(conversionTuple);
            } else if (converterRef.equals(converter)) {
                // Given Type and Converter combination occurred before! Let's return the conversion
                //noinspection unchecked
                return (T) conversionTuple.conversion();
            }
        }

        // No previous conversion found for Type and Converter. Let's convert ourselves
        T conversion = converter.convert(input, convertedType);
        conversions.add(new ConverterConversionTuple(new WeakReference<>(converter), conversion));
        return conversion;
    }

    private record ConverterConversionTuple(WeakReference<Converter> converterReference, Object conversion) {

    }
}
