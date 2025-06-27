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

/**
 * A Converter implementation that only passes through values if the source and target types are the same. No conversion
 * is performed; otherwise, null is returned.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * TODO #3102 - Validate this Converter as part of #3102
 */
public final class PassThroughConverter implements Converter {

    @Override
    public boolean canConvert(@Nonnull Class<?> sourceType, @Nonnull Class<?> targetType) {
        return sourceType.equals(targetType);
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
                "PassThroughConverter only supports conversion when source and target types are the same. Source: "
                        + sourceType.getName() + ", Target: " + targetType.getName());
    }
}
