/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link Internal} class that maintain a "conversion cache", to back operations such as
 * {@link Message#payloadAs(Type, Converter)} and {@link Message#withConvertedPayload(Type, Converter)}, to avoid
 * executing several conversions to the same target type with the same converter.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
class ConversionCache {

    // Allow system-wide disabling of the cache using an environment variable
    private final boolean conversionEnabled = Boolean.parseBoolean(
            System.getProperty("AXON_CONVERSION_CACHE_ENABLED", "true")
    );
    // Replacement value to allow null values in the map
    private static final Object NULL = new Object();

    private final ConcurrentHashMap<Target, Object> conversionCache;
    private final Object original;

    /**
     * Constructs a {@code ConversionCache} with the {@code original} object for which conversions will be stored in
     * this cache.
     *
     * @param original The original object for which conversions will be stored in this cache.
     */
    public ConversionCache(@Nullable Object original) {
        this.original = original;
        this.conversionCache = conversionEnabled ? new ConcurrentHashMap<>() : null;
    }

    /**
     * Converts the given {@code input} into the given {@code type} with the given {@code converter} <b>only</b> if this
     * conversion combination has not been performed before.
     * <p>
     * Will first validate if there are any previous conversions for the given {@code type}. When this is the case, will
     * search for previous conversions into the given {@code type} <b>by</b> the given {@code converter}.
     * <p>
     * If that's {@code true}, the previous conversion result will be returned. Whenever this is {@code false},
     * {@link Converter#convert(Object, Type)} will be invoked with the {@code input} and {@code type} respectively. The
     * conversion result is kept in this cache for subsequent invocations.
     * <p>
     * The result may be {@code null} since the result of {@link Converter#convert(Object, Type) conversion} can also be
     * {@code null}.
     *
     * @param targetType The type to convert the given {@code input} into, <b>if</b> the given type-and-converter
     *                   combination has not been seen before.
     * @param converter  The converter to convert the given {@code input with}, <b>if</b> the given type-and-converter
     *                   combination has not been seen before.
     * @param <T>        The generic type to return.
     * @return The converted {@code input}, either directly from this cache or as the result from
     * {@link Converter#convert(Object, Type) converting}.
     */
    @Nullable
    <T> T convertIfAbsent(@Nonnull Type targetType,
                          @Nonnull Converter converter) {
        if (conversionEnabled) {
            //noinspection unchecked
            return (T) unwrapNull(conversionCache.computeIfAbsent(
                    new Target(targetType, converter),
                    t -> wrapNull(converter.convert(original, targetType))
            ));
        }
        // No caching, direct conversion
        return converter.convert(original, targetType);
    }

    @Nonnull
    private Object wrapNull(@Nullable Object obj) {
        return obj == null ? NULL : obj;
    }

    @Nullable
    private Object unwrapNull(@Nonnull Object wrapped) {
        return wrapped == NULL ? null : wrapped;
    }

    private record Target(Type targetType, Converter converter) {

    }
}
