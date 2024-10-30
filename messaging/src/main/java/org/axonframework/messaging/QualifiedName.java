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
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Interface describing a qualified name.
 * <p>
 * Useful to provide clear names to {@link Message Messages}, {@link MessageHandler MessageHandlers}, and other
 * components that require naming.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface QualifiedName {

    /**
     * Construct a {@link QualifiedName} based on the given {@code clazz}.
     * <p>
     * The {@link Class#getPackageName()} will become the {@link #namespace()}, and the {@link Class#getSimpleName()}
     * will be the {@link #localName()}.
     *
     * @param clazz The {@link Class} to extract a {@link #namespace()} and {@link #localName()} from.
     * @return A {@link QualifiedName} based on the given {@code clazz}.
     */
    static QualifiedName fromClass(@Nonnull Class<?> clazz) {
        assertNonNull(clazz, "Cannot construct a QualifiedName based on a null Class.");
        return new SimpleQualifiedName(clazz.getPackageName(), clazz.getSimpleName());
    }

    /**
     * Construct a {@link QualifiedName} based on the given {@code dottedName}.
     * <p>
     * All information <em>before</em> the last dot ({@code .}) in the given {@code dottedName} will be set as the
     * {@link #namespace()}. In turn, all text <em>after</em> the last dot in the given {@code dottedName} will become
     * the {@link #localName()}.
     * <p>
     * For example, given a {@link String} of {@code "my.context.BusinessOperation"}, the {@link #namespace()} would
     * become {@code "my.context"} and the {@link #localName()} would be {@code "BusinessOperation"}.
     *
     * @param dottedName The {@link String} to retrieve the {@link #namespace()} and {@link #localName()} from.
     * @return A {@link QualifiedName} based on the given {@code dottedName}.
     * @throws org.axonframework.common.AxonConfigurationException If the substring representing the
     *                                                             {@link #localName()} is {@code null} or empty.
     */
    static QualifiedName fromDottedName(@Nonnull String dottedName) {
        assertNonEmpty(dottedName, "Cannot construct a QualifiedName based on a null or empty String.");
        int lastDot = dottedName.lastIndexOf('.');
        String namespace = dottedName.substring(0, Math.max(lastDot, 0));
        String localName = dottedName.substring(lastDot + 1);
        return new SimpleQualifiedName(namespace, localName);
    }

    /**
     * Returns the namespace this {@link QualifiedName} refers too.
     * <p>
     * The namespace may represent a (bounded) context, package, or whatever other "space" this name applies too.
     *
     * @return The namespace this {@link QualifiedName} refers too.
     */
    @Nonnull
    String namespace();

    /**
     * Returns the local name this {@link QualifiedName} refers too.
     * <p>
     * The local name may represent a {@link Message Message's} name, a {@link MessageHandler} its name, or whatever
     * other business specific concept this name should represent.
     *
     * @return The local name this {@link QualifiedName} refers too.
     */
    @Nonnull
    String localName();

    /**
     * Returns the revision of this {@link QualifiedName}.
     *
     * @return The revision of this {@link QualifiedName}.
     */
    @Nonnull
    String revision();

    /**
     * Prints the {@link QualifiedName} in a simplified {@link String}.
     * <p>
     * If there is only a non-empty {@link #localName()}, only the {@code localName} will be printed. When the
     * {@link #namespace()} is non-empty, the {@code localName} will be post-fixed with {@code "@({namespace})"}. And
     * when the {@link #revision()} is non-empty, the {@code localName} (and possibly {@code namespace}) will be
     * post-fixed with {@code "#[{revision}]"}.
     * <p>
     * Thus, if {@code localName()} returns {@code "BusinessName"}, the {@code namespace()} returns
     * {@code "my.context"}, and the {@code revision()} returns {@code "3"}, the result of this operation would be
     * {@code "BusinessName @(my.context) #[3]"}.
     *
     * @return A simple {@link String} based on the {@link #localName()}, and {@link #namespace()} (if non-empty) and
     * {@link #revision()} (if non-empty).
     */
    default String toSimpleString() {
        return localName()
                + (namespace().isBlank() ? "" : " @(" + namespace() + ")")
                + (revision().isBlank() ? "" : " #[" + revision() + "]");
    }
}
