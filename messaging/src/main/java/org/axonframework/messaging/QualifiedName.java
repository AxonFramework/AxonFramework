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
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Interface describing a qualified name, providing space for a {@link #namespace() namespace},
 * {@link #localName() local name}, and {@link #revision() revision}.
 * <p>
 * Useful to provide clear names to {@link Message Messages}, {@link MessageHandler MessageHandlers}, and other
 * components that require naming.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public final class QualifiedName {

    private final String namespace;
    private final String localName;
    private final String revision;

    /**
     * Constructs a {@link QualifiedName} based on the given {@code namespace}, {@code localName}, and
     * {@code revision}.
     * <p>
     * A {@code null} given {@code namespace} is defaulted to an empty {@link String}. The given {@code revision} will
     * be wrapped in an {@link Optional#ofNullable(Object)} at all times when retrieved through the {@link #revision()}
     * operation.
     *
     * @param namespace The {@link String} representing the {@link #namespace()} of this {@link QualifiedName}.
     * @param localName The {@link String} representing the {@link #localName()} of this {@link QualifiedName}.
     * @param revision  The {@link String} representing the {@link #revision()} of this {@link QualifiedName}.
     */
    public QualifiedName(@Nullable String namespace,
                         @Nonnull String localName,
                         @Nullable String revision) {
        assertNonEmpty(localName, "The localName must not be null or empty.");
        this.namespace = Objects.requireNonNullElse(namespace, "");
        this.localName = localName;
        this.revision = revision;
    }

    /**
     * Construct a {@link QualifiedName} based on the given {@code clazz}.
     * <p>
     * The {@link Class#getPackageName()} will become the {@link #namespace()}, and the {@link Class#getSimpleName()}
     * will be the {@link #localName()}.
     *
     * @param clazz The {@link Class} to extract a {@link #namespace()} and {@link #localName()} from.
     * @return A {@link QualifiedName} based on the given {@code clazz}.
     */
    public static QualifiedName className(@Nonnull Class<?> clazz) {
        assertNonNull(clazz, "Cannot construct a QualifiedName based on a null Class.");
        return new QualifiedName(clazz.getPackageName(), clazz.getSimpleName(), null);
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
    public static QualifiedName dottedName(@Nonnull String dottedName) {
        assertNonEmpty(dottedName, "Cannot construct a QualifiedName based on a null or empty String.");
        int lastDot = dottedName.lastIndexOf('.');
        String namespace = dottedName.substring(0, Math.max(lastDot, 0));
        String localName = dottedName.substring(lastDot + 1);
        return new QualifiedName(namespace, localName, null);
    }

    /**
     * Reconstruct a {@link QualifiedName} based on the output of {@link QualifiedName#toSimpleString()}.
     * <p>
     * The output of the {@code QualifiedName#toSimpleString()} depends on which fields are set in the
     * {@code QualifiedName}. If there is only a non-empty {@link #localName()}, only the {@code localName} will be
     * printed. When the {@link #namespace()} is non-empty, the {@code localName} will be post-fixed with
     * {@code "@({namespace})"}. And when the {@link #revision()} is non-empty, the {@code localName} (and possibly
     * {@code namespace}) will be post-fixed with {@code "#[{revision}]"}.
     * <p>
     * Thus, if {@code localName()} returns {@code "BusinessName"}, the {@code namespace()} returns
     * {@code "my.context"}, and the {@code revision()} returns {@code "3"}, a simple {@link String} would be
     * {@code "BusinessName @(my.context) #[3]"}.
     *
     * @param simpleString The output of {@link QualifiedName#toSimpleString()}, given to reconstruct it into a
     *                     {@link QualifiedName}.
     * @return A reconstructed {@link QualifiedName} based on the expected output of
     * {@link QualifiedName#toSimpleString()}.
     */
    @SuppressWarnings("DuplicateExpressions")
    public static QualifiedName simpleStringName(@Nonnull String simpleString) {
        assertNonEmpty(simpleString, "Cannot construct a QualifiedName based on a null or empty String.");
        int bracketOffset = 1;
        int signAndBracketOffset = 2;

        String namespace = null;
        String localName;
        String revision = null;
        int lastAt = simpleString.lastIndexOf('@');
        int lastHash = simpleString.lastIndexOf('#');
        int length = simpleString.length();

        if (onlyHasLocalName(lastAt, lastHash)) {
            localName = simpleString;
        } else if (hasLocalNameAndNamespace(lastAt, lastHash)) {
            localName = simpleString.substring(0, lastAt - bracketOffset);
            namespace = simpleString.substring(lastAt + signAndBracketOffset, length - bracketOffset);
        } else if (hasLocalNameAndRevision(lastAt, lastHash)) {
            localName = simpleString.substring(0, lastHash - bracketOffset);
            revision = simpleString.substring(lastHash + signAndBracketOffset, length - bracketOffset);
        } else {
            localName = simpleString.substring(0, lastAt - bracketOffset);
            namespace = simpleString.substring(lastAt + signAndBracketOffset, lastHash - signAndBracketOffset);
            revision = simpleString.substring(lastHash + signAndBracketOffset, length - bracketOffset);
        }

        return new QualifiedName(namespace, localName, revision);
    }

    private static boolean hasLocalNameAndRevision(int lastAt, int lastHash) {
        return lastAt == -1 && lastHash != -1;
    }

    private static boolean hasLocalNameAndNamespace(int lastAt, int lastHash) {
        return lastAt != -1 && lastHash == -1;
    }

    private static boolean onlyHasLocalName(int lastAt, int lastHash) {
        return lastAt == -1 && lastHash == -1;
    }

    /**
     * Returns the namespace this {@link QualifiedName} refers too.
     * <p>
     * The namespace may represent a (bounded) context, package, or whatever other "space" this name applies too.
     *
     * @return The namespace this {@link QualifiedName} refers too.
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the local name this {@link QualifiedName} refers too.
     * <p>
     * The local name may represent a {@link Message Message's} name, a {@link MessageHandler} its name, or whatever
     * other business specific concept this name should represent.
     *
     * @return The local name this {@link QualifiedName} refers too.
     */
    public String localName() {
        return localName;
    }

    /**
     * Returns the revision of this {@link QualifiedName} as an {@link Optional}.
     *
     * @return The revision of this {@link QualifiedName}.
     */
    public Optional<String> revision() {
        return Optional.ofNullable(revision);
    }

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
    public String toSimpleString() {
        return localName()
                + (namespace.isBlank() ? "" : " @(" + namespace + ")")
                + (revision == null || revision.isBlank() ? "" : " #[" + revision + "]");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QualifiedName that = (QualifiedName) o;
        return Objects.equals(namespace, that.namespace)
                && Objects.equals(localName, that.localName)
                && Objects.equals(revision, that.revision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, localName, revision);
    }

    @Override
    public String toString() {
        return "QualifiedName{" +
                "namespace='" + namespace + '\'' +
                ", localName='" + localName + '\'' +
                ", revision='" + revision + '\'' +
                '}';
    }
}
