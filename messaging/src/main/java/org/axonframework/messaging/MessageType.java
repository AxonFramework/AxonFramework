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
import org.axonframework.common.Assert;
import org.axonframework.common.StringUtils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Record combining a {@link QualifiedName qualified name} and {@code version}.
 * <p>
 * The {@code QualifiedName} is useful to provide clear names to {@link Message Messages},
 * {@link MessageHandler MessageHandlers}, and other components that require naming.
 * <p>
 * When you do not require a version for typing, consider using the {@code QualifiedName} directly instead.
 *
 * @param qualifiedName The {@code QualifiedName} of this {@code MessageType}.
 * @param version       the version of this {@code MessageType}.
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record MessageType(@Nonnull QualifiedName qualifiedName, @Nonnull String version) {

    /**
     * The default version of a {@code MessageType} when none is given. Set to {@code 0.0.1}.
     */
    public static final String DEFAULT_VERSION = "0.0.1";

    private static final String VERSION_DELIMITER = "#";
    private static final String DEBUG_STRING_REGEX = "^([^#]+)#([^#]+)$";
    private static final Pattern DEBUG_STRING_PATTERN = Pattern.compile(DEBUG_STRING_REGEX);
    private static final int QUALIFIED_NAME_GROUP = 1;
    private static final int VERSION_GROUP = 2;

    /**
     * Compact constructor validating that the given {@code qualifiedName} is non-null.
     */
    public MessageType {
        requireNonNull(qualifiedName, "The qualifiedName cannot be null.");
        Assert.assertThat(
                requireNonNull(version, "The given version is unsupported because it is null."),
                StringUtils::nonEmpty,
                () -> new IllegalArgumentException(
                        "The given version is unsupported because it is empty."
                )
        );
    }

    /**
     * A {@code MessageType} constructor setting the {@code version} to {@code null}.
     *
     * @param name The {@code QualifiedName} of this {@code MessageType}.
     */
    public MessageType(@Nonnull QualifiedName name) {
        this(name, DEFAULT_VERSION);
    }

    /**
     * A {@code MessageType} constructor using the given {@code qualifiedName} invoking the
     * {@link QualifiedName#QualifiedName(String)} constructor. The {@code version} is fixed to {@code null}.
     *
     * @param qualifiedName The qualifiedName for the {@link QualifiedName#QualifiedName(String) QualifiedName} for the
     *                      {@code MessageType} under construction.
     */
    public MessageType(@Nonnull String qualifiedName) {
        this(new QualifiedName(qualifiedName), DEFAULT_VERSION);
    }

    /**
     * A {@code MessageType} constructor using the given {@code qualifiedName} to invoke the
     * {@link QualifiedName#QualifiedName(String)} constructor to derive the {@code qualifiedName} for this
     * {@code MessageType}.
     *
     * @param qualifiedName The qualified qualifiedName used to invoke {@link QualifiedName#QualifiedName(String)}
     *                      constructor for the {@code MessageType} under construction.
     * @param version       The version for the {@code MessageType} under construction.
     */
    public MessageType(@Nonnull String qualifiedName,
                       @Nonnull String version) {
        this(new QualifiedName(qualifiedName), version);
    }

    /**
     * A {@code MessageType} constructor using the given {@code namespace} and {@code localName} to invoke the
     * {@link QualifiedName#QualifiedName(String, String)} constructor to derive the {@code qualifiedName} for this
     * {@code MessageType}.
     *
     * @param namespace The namespace used to invoke {@link QualifiedName#QualifiedName(String, String)} constructor for
     *                  the {@code MessageType} under construction.
     * @param localName The local qualifiedName used to invoke {@link QualifiedName#QualifiedName(String, String)}
     *                  constructor for the {@code MessageType} under construction.
     * @param version   The version for the {@code MessageType} under construction.
     */
    public MessageType(String namespace,
                       @Nonnull String localName,
                       @Nonnull String version) {
        this(new QualifiedName(namespace, localName), version);
    }

    /**
     * A {@code MessageType} constructor using the given {@code clazz} to invoke the
     * {@link QualifiedName#QualifiedName(Class)} constructor  to derive the {@code qualifiedName} for this
     * {@code MessageType}.
     *
     * @param clazz   The {@code Class} used to invoke {@link QualifiedName#QualifiedName(Class)} constructor for the
     *                {@code MessageType} under construction.
     * @param version The version for the {@code MessageType} under construction.
     */
    public MessageType(@Nonnull Class<?> clazz,
                       @Nonnull String version) {
        this(new QualifiedName(clazz), version);
    }

    /**
     * A {@code MessageType} constructor using the given {@code clazz} to invoke the
     * {@link QualifiedName#QualifiedName(Class)} constructor  to derive the {@code qualifiedName} for this
     * {@code MessageType}. The {@code version} is fixed to {@code null}.
     *
     * @param clazz The {@code Class} used to invoke {@link QualifiedName#QualifiedName(Class)} constructor for the
     *              {@code MessageType} under construction.
     */
    public MessageType(@Nonnull Class<?> clazz) {
        this(clazz, DEFAULT_VERSION);
    }

    public String name() {
        return qualifiedName.toString();
    }

    /**
     * Reconstruct a {@code MessageType} based on the output of {@link MessageType#toString()}.
     * <p>
     * The output of {@code MessageType#toString()} is a concatenation of the {@link #qualifiedName()} and
     * {@link #version()}, split by means of a hashtag ({@code #}).
     * <p>
     * Thus, if the given {@code String} equals {@code "my.context.BusinessName#1.0.5"}, the {@code #qualifiedName()} is
     * set to a {@link QualifiedName} of {@code "my.context.BusinessName"} and the {@link #version()} is set to
     * {@code "1.0.5"}.
     *
     * @param messageTypeString The output of {@link MessageType#toString()}, given to reconstruct it into a {@code MessageType}.
     * @return A reconstructed {@code MessageType} based on the expected output of {@link MessageType#toString()}.
     */
    public static MessageType fromString(@Nonnull String messageTypeString) {
        Matcher matcher = DEBUG_STRING_PATTERN.matcher(
                Objects.requireNonNull(messageTypeString, "Cannot construct a MessageType based on a null or empty String.")
        );
        Assert.isTrue(matcher.matches(),
                      () -> "The given simple String [" + messageTypeString + "] does not match the expected pattern.");
        return new MessageType(new QualifiedName(matcher.group(QUALIFIED_NAME_GROUP)), matcher.group(VERSION_GROUP));
    }

    /**
     * The output of {@code MessageType#toString()} is a concatenation of the {@link #qualifiedName()} and
     * {@link #version()}, split by means of a hashtag ({@code #}).
     * <p>
     * Thus, if {@code #qualifiedName()} returns {@code "my.context.BusinessName"} and the {@code #version()} returns
     * {@code "1.0.5"}, the result of <b>this</b> operation would be {@code "my.context.BusinessName#1.0.5"}.
     *
     * @return A combination of the {@link #qualifiedName()} and {@link #version()}, separated by a hashtag.
     */
    @Override
    public String toString() {
        return qualifiedName + VERSION_DELIMITER + version;
    }
}
