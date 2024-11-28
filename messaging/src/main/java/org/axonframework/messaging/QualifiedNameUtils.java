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

import static org.axonframework.common.BuilderUtils.assertNonEmpty;

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

    /**
     * Construct a {@link QualifiedName} based on the given {@code dottedName}, defaulting the
     * {@link QualifiedName#revision()} to {@link QualifiedName#DEFAULT_REVISION}.
     * <p>
     * All information <em>before</em> the last dot ({@code .}) in the given {@code dottedName} will be set as the
     * {@link QualifiedName#namespace()}. In turn, all text <em>after</em> the last dot in the given {@code dottedName}
     * will become the {@link QualifiedName#localName()}.
     * <p>
     * For example, given a {@link String} of {@code "my.context.BusinessOperation"}, the
     * {@link QualifiedName#namespace()} would become {@code "my.context"} and the {@link QualifiedName#localName()}
     * would be {@code "BusinessOperation"}.
     *
     * @param dottedName The {@link String} to retrieve the {@link QualifiedName#namespace()} and
     *                   {@link QualifiedName#localName()} from.
     * @return A {@link QualifiedName} based on the given {@code dottedName}.
     * @throws org.axonframework.common.AxonConfigurationException If the substring representing the
     *                                                             {@link QualifiedName#localName()} is {@code null} or
     *                                                             empty.
     */
    public static QualifiedName fromDottedName(@Nonnull String dottedName) {
        return fromDottedName(dottedName, QualifiedName.DEFAULT_REVISION);
    }

    /**
     * Construct a {@link QualifiedName} based on the given {@code dottedName}, using the given {@code revision} as the
     * {@link QualifiedName#revision()}.
     * <p>
     * All information <em>before</em> the last dot ({@code .}) in the given {@code dottedName} will be set as the
     * {@link QualifiedName#namespace()}. In turn, all text <em>after</em> the last dot in the given {@code dottedName}
     * will become the {@link QualifiedName#localName()}.
     * <p>
     * For example, given a {@link String} of {@code "my.context.BusinessOperation"}, the
     * {@link QualifiedName#namespace()} would become {@code "my.context"} and the {@link QualifiedName#localName()}
     * would be {@code "BusinessOperation"}.
     *
     * @param dottedName The {@link String} to retrieve the {@link QualifiedName#namespace()} and
     *                   {@link QualifiedName#localName()} from.
     * @param revision   The {@link String} resulting in the {@link QualifiedName#revision()}.
     * @return A {@link QualifiedName} based on the given {@code dottedName}.
     * @throws org.axonframework.common.AxonConfigurationException If the substring representing the
     *                                                             {@link QualifiedName#localName()} is {@code null} or
     *                                                             empty.
     */
    public static QualifiedName fromDottedName(@Nonnull String dottedName,
                                               @Nonnull String revision) {
        assertNonEmpty(dottedName, "Cannot construct a QualifiedName based on a null or empty String.");
        int lastDot = dottedName.lastIndexOf('.');
        String namespace = dottedName.substring(0, Math.max(lastDot, 0));
        String localName = dottedName.substring(lastDot + 1);
        return new QualifiedName(namespace, localName, revision);
    }
}
