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

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.axonframework.common.BuilderUtils.*;

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
public final class QualifiedName implements Serializable {

    private static final Pattern SIMPLE_STRING_PATTERN = Pattern.compile("(\\w+)(\\s@\\[\\w+])?(\\s#\\[\\w+])?");

    /**
     * The default {@link #revision()} to use when none is present. Defaults to {@code "0.0.1"} as the revision.
     */
    public static final String DEFAULT_REVISION = "0.0.1";

    private final String namespace;
    private final String localName;
    private final String revision;

    /**
     * Constructs a {@link QualifiedName} based on the given {@code namespace}, {@code localName}, and
     * {@code revision}.
     *
     * @param namespace The {@link String} representing the {@link #namespace()} of this {@link QualifiedName}.
     * @param localName The {@link String} representing the {@link #localName()} of this {@link QualifiedName}.
     * @param revision  The {@link String} representing the {@link #revision()} of this {@link QualifiedName}.
     */
    public QualifiedName(@Nonnull String namespace,
                         @Nonnull String localName,
                         @Nonnull String revision) {
        assertNonEmpty(namespace, "The namespace must not be null or empty.");
        assertNonEmpty(localName, "The localName must not be null or empty.");
        assertNonEmpty(revision, "The revision must not be null or empty.");
        this.namespace = namespace;
        this.localName = localName;
        this.revision = revision;
    }

    /**
     * Construct a {@link QualifiedName} based on the given {@code dottedName}, defaulting the {@link #revision()} to
     * {@link #DEFAULT_REVISION}.
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
        return dottedName(dottedName, DEFAULT_REVISION);
    }

    /**
     * Construct a {@link QualifiedName} based on the given {@code dottedName}, using the given {@code revision} as the
     * {@link #revision()}.
     * <p>
     * All information <em>before</em> the last dot ({@code .}) in the given {@code dottedName} will be set as the
     * {@link #namespace()}. In turn, all text <em>after</em> the last dot in the given {@code dottedName} will become
     * the {@link #localName()}.
     * <p>
     * For example, given a {@link String} of {@code "my.context.BusinessOperation"}, the {@link #namespace()} would
     * become {@code "my.context"} and the {@link #localName()} would be {@code "BusinessOperation"}.
     *
     * @param dottedName The {@link String} to retrieve the {@link #namespace()} and {@link #localName()} from.
     * @param revision   The {@link String} resulting in the {@link #revision()}.
     * @return A {@link QualifiedName} based on the given {@code dottedName}.
     * @throws org.axonframework.common.AxonConfigurationException If the substring representing the
     *                                                             {@link #localName()} is {@code null} or empty.
     */
    public static QualifiedName dottedName(@Nonnull String dottedName,
                                           @Nonnull String revision) {
        assertNonEmpty(dottedName, "Cannot construct a QualifiedName based on a null or empty String.");
        int lastDot = dottedName.lastIndexOf('.');
        String namespace = dottedName.substring(0, Math.max(lastDot, 0));
        String localName = dottedName.substring(lastDot + 1);
        return new QualifiedName(namespace, localName, revision);
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
    public static QualifiedName simpleStringName(@Nonnull String simpleString) {
        assertNonEmpty(simpleString, "Cannot construct a QualifiedName based on a null or empty String.");
        Matcher simpleStringMatcher = SIMPLE_STRING_PATTERN.matcher(simpleString);
        assertThat(simpleStringMatcher,
                   Matcher::matches,
                   "The given simple String [" + simpleString + "] does not match the expected pattern.");

        String namespace = getNamespace(simpleStringMatcher.group(2));
        String localName = simpleStringMatcher.group(1);
        String revision = getRevision(simpleStringMatcher.group(3));
        return new QualifiedName(namespace, localName, revision);
    }

    private static String getNamespace(String namespaceGroup) {
        return namespaceGroup != null ? removeBrackets(namespaceGroup).replace("@", "").trim() : null;
    }

    private static String getRevision(String revisionGroup) {
        return revisionGroup != null ? removeBrackets(revisionGroup).replace("#", "").trim() : null;
    }

    private static String removeBrackets(String stringWithBrackets) {
        return stringWithBrackets.replace("[", "").replace("]", "");
    }

    /**
     * Returns the namespace this {@link QualifiedName} refers too.
     * <p>
     * The namespace may represent a (bounded) context, package, or whatever other "space" this {@link QualifiedName}
     * applies too.
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
     * other business specific concept this {@link QualifiedName} should represent.
     *
     * @return The local name this {@link QualifiedName} refers too.
     */
    public String localName() {
        return localName;
    }

    /**
     * Returns the revision of this {@link QualifiedName}.
     * <p>
     * The revision typically represents the version of the {@link Message Message's}
     * {@link Message#getPayload() payload}. Or, for a {@link MessageHandler}, it specifies the version or versions of a
     * {@code Message} that the handler is able to handle.
     *
     * @return The revision of this {@link QualifiedName}.
     */
    public String revision() {
        return revision;
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
