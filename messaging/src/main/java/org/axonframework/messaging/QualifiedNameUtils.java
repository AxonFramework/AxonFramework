/*
 * Copyright (c) 2010-2024. Axon Framework
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

/**
 * Utility class containing factory methods for the {@link QualifiedName} that align closely with the previous major
 * version of Axon.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO move to axon-legacy / axon-migration
public final class QualifiedNameUtils {

    private QualifiedNameUtils() {
        // Utility class
    }

    /**
     * Construct a {@link QualifiedName} based on the given {@code clazz}.
     * <p>
     * The {@link Class#getPackageName()} will become the {@link QualifiedName#namespace()}, and the
     * {@link Class#getSimpleName()} will be the {@link QualifiedName#localName()}. The {@link QualifiedName#revision()}
     * becomes the {@link QualifiedName#DEFAULT_REVISION}.
     *
     * @param clazz The {@link Class} to extract a {@link QualifiedName#namespace()} and
     *              {@link QualifiedName#localName()} from.
     * @return A {@link QualifiedName} based on the given {@code clazz}.
     */
    public static QualifiedName fromClassName(@Nonnull Class<?> clazz) {
        return new QualifiedName(clazz.getPackageName(), clazz.getSimpleName(), QualifiedName.DEFAULT_REVISION);
    }
}
