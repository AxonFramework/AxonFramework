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

package org.axonframework.serialization;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;

/**
 * A {@link Converter} implementation that only "passes through" input object if the {@code sourceType} and
 * {@code targetType} are the identical.
 * <p>
 * As such, no conversion is performed by this {@code Converter}! The {@link #canConvert(Class, Class)} operation will
 * <b>only</b> return {@code true} whenever both types are identical. Furthermore, both {@link #convert(Object, Class)}
 * and {@link #convert(Object, Class, Class)} will expect identical typing too, otherwise resulting in an
 * {@link IllegalArgumentException}.
 * <p>
 * As such, this {@code Converter} is only useful when conversion is not necessary (e.g. during testing) for the
 * component at hand.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public final class PassThroughConverter implements Converter {

    /**
     * The single instance of the {@code PassThroughConverter}.
     */
    public static final PassThroughConverter INSTANCE = new PassThroughConverter();

    private PassThroughConverter() {
        // Private constructor to enforce use of constant.
    }

    @Override
    public boolean canConvert(@Nonnull Class<?> sourceType, @Nonnull Class<?> targetType) {
        return sourceType.equals(targetType);
    }

    @Override
    @Nullable
    public <S, T> T convert(@Nullable S input, @Nonnull Class<T> targetType) {
        return this.convert(input, ObjectUtils.nullSafeTypeOf(input), targetType);
    }

    @Override
    @Nullable
    public <S, T> T convert(@Nullable S input, @Nonnull Class<S> sourceType, @Nonnull Class<T> targetType) {
        if (input == null) {
            return null;
        }
        if (sourceType.equals(targetType)) {
            return targetType.cast(input);
        }
        throw new IllegalArgumentException(
                "This Converter only supports same-type conversion, while the unidentical source type ["
                        + sourceType.getName() + "] and target type [" + targetType.getName() + "] have been given."
        );
    }
}
