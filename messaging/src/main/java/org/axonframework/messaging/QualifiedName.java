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
import org.axonframework.common.Assert;
import org.axonframework.common.StringUtils;

import java.io.Serializable;

import static java.util.Objects.requireNonNull;

/**
 * Interface describing a qualified {@code qualifiedName}, providing space for a {@link #namespace() namespace},
 * {@link #localName() local qualifiedName}.
 * <p>
 * The given {@code qualifiedName} uses a dot {@code .} as the separator between the {@link #namespace() namespace} and
 * {@link #localName() local qualifiedName}. When multiple dots are present in the {@code qualifiedName}, the
 * {@link #localName() local qualifiedName} constitutes the trailing text <em>after</em> the last dot. Thus, the
 * {@link #namespace() namespace} as all the text <em>before</em> the last dot.
 * <p>
 * The {@code QualifiedName} is useful to provide clear names to {@link Message Messages},
 * {@link MessageHandler MessageHandlers}, and other components that require naming. To combine a qualified
 * qualifiedName with a version, consider using the {@link MessageType}.
 *
 * @param name The qualifiedName of this {@code QualifiedName}. Must not be {@code null} or empty.
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @see MessageType
 * @since 5.0.0
 */
public record QualifiedName(@Nonnull String name) implements Serializable {

    private static final String DELIMITER = ".";

    /**
     * Compact constructor asserting whether the {@code qualifiedName} is non-null and not empty.
     */
    public QualifiedName {
        Assert.assertThat(
                requireNonNull(name, "The given name is unsupported because it is null."),
                StringUtils::nonEmpty,
                () -> new IllegalArgumentException("The given name is unsupported because it is empty.")
        );
    }

    /**
     * Constructor providing combining the given {@code namespace} and {@code localName}, delimited by a dot {@code .}.
     * <p>
     * If the given {@code namespace} is an empty or {@code null} String, the {@link #name()} becomes the
     * {@code localName}. If the {@code localName} is empty or {@code null}, an {@link IllegalArgumentException} will be
     * thrown.
     *
     * @param namespace The first part of the {@link #name()}.
     * @param localName The last part of the {@link #name()}.
     */
    public QualifiedName(String namespace, @Nonnull String localName) {
        this(combineNames(namespace, localName));
    }

    private static String combineNames(String namespace, String localName) {
        Assert.assertThat(
                requireNonNull(
                        localName, "The given local name [" + localName + "] is unsupported because it is null."
                ),
                StringUtils::nonEmptyOrNull,
                () -> new IllegalArgumentException(
                        "The given local name [" + localName + "] is unsupported because it is empty."
                )
        );
        return StringUtils.nonEmptyOrNull(namespace) ? namespace + DELIMITER + localName : localName;
    }

    /**
     * Constructor taking the {@link Class#getName()} as the {@link #name()} of the {@code QualifiedName} under
     * construction.
     *
     * @param clazz The {@code Class} from which to use the {@link Class#getName()} as the {@link #name()}.
     */
    public QualifiedName(@Nonnull Class<?> clazz) {
        this(requireNonNull(clazz, "The given Class cannot be null.").getName());
    }

    @Nullable
    public String namespace() {
        int lastDelimiterIndex = name.lastIndexOf(DELIMITER);
        return lastDelimiterIndex != -1 ? name.substring(0, lastDelimiterIndex) : null;
    }

    @Nonnull
    public String localName() {
        return name.substring(name.lastIndexOf(DELIMITER) + 1);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the full name of this QualifiedName, consisting of the namespace and localName, separated by a "."
     * (dot).
     *
     * @return the full name of this QualifiedName
     */
    public String fullName() {
        return namespace() + "." + localName();
    }
}
