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
import org.axonframework.common.Assert;
import org.axonframework.common.StringUtils;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface describing a qualified name, providing space for a {@link #namespace() namespace},
 * {@link #localName() local name}, and {@link #revision() revision}.
 * <p>
 * Useful to provide clear names to {@link Message Messages}, {@link MessageHandler MessageHandlers}, and other
 * components that require naming.
 * <p>
 * Note that all characters <b>except</b> for the semicolon ({@code :}) are allowed for any of the three parameters. The
 * semicolon acts as a delimiter for the {@link #toString()} and thus is a reserved character.
 *
 * @param namespace The {@link String} representing the {@link #namespace()} of this {@link QualifiedName}. The
 *                  {@code namespace} may represent a (bounded) context, package, or whatever other "space" this
 *                  {@link QualifiedName} applies too.
 * @param localName The {@link String} representing the {@link #localName()} of this {@link QualifiedName}. The
 *                  {@code localName} may represent a {@link Message Message's} name, a {@link MessageHandler} its name,
 *                  or whatever other business specific concept this {@link QualifiedName} should represent.
 * @param revision  The {@link String} representing the {@link #revision()} of this {@link QualifiedName}. The
 *                  {@code revision} typically represents the version of the {@link Message Message's}
 *                  {@link Message#getPayload() payload}. Or, for a {@link MessageHandler}, it specifies the version or
 *                  versions of a {@code Message} that the handler is able to handle.
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record QualifiedName(@Nonnull String namespace,
                            @Nonnull String localName,
                            @Nonnull String revision) implements Serializable {

    /**
     * The semantic version is retrieved from <a href="https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string">semver.org</a>.
     */
    private static final String SEMVER_REGEX = "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?";
    private static final String DEBUG_STRING_REGEX = "^([^:]+):([^:]+):(" + SEMVER_REGEX + ")$";
    private static final Pattern DEBUG_STRING_PATTERN = Pattern.compile(DEBUG_STRING_REGEX);
    private static final int NAMESPACE_GROUP = 1;
    private static final int LOCAL_NAME_GROUP = 2;
    private static final int REVISION_GROUP = 3;

    /**
     * The delimiter, a semicolon ({@code :}), used between the {@link #namespace()}, {@link #localName()}, and
     * {@link #revision()}.
     */
    public static final String DELIMITER = ":";

    /**
     * Compact constructor asserting whether the {@code namespace}, {@code localName}, and {@code revision} are non-null
     * and not empty.
     */
    public QualifiedName {
        Assert.assertThat(
                namespace,
                n -> StringUtils.nonEmptyOrNull(n) && !n.contains(DELIMITER),
                () -> new IllegalArgumentException(
                        "The given namespace [" + namespace
                                + "] is unsupported because it is null, empty, or contains a semicolon (:)."
                )
        );
        Assert.assertThat(
                localName,
                l -> StringUtils.nonEmptyOrNull(l) && !l.contains(DELIMITER),
                () -> new IllegalArgumentException(
                        "The given local name [" + localName
                                + "] is unsupported because it is null, empty, or contains a semicolon (:)."
                )
        );
        Assert.assertThat(
                revision,
                r -> StringUtils.nonEmptyOrNull(r) && !r.contains(DELIMITER),
                () -> new IllegalArgumentException(
                        "The given revision [" + revision
                                + "] is unsupported because it is null, empty, or contains a semicolon (:)."
                )
        );
    }

    /**
     * Reconstruct a {@link QualifiedName} based on the output of {@link QualifiedName#toString()}.
     * <p>
     * The output of {@code QualifiedName#toString()} is a concatenation of  the {@link #namespace()},
     * {@link #localName()}, and {@link #revision()}, split by means of a semicolon ({@code :}).
     * <p>
     * Thus, if {@code #namespace()} returns {@code "my.context"}, the {@code #localName()} returns
     * {@code "BusinessName"}, and the {@code #revision()} returns {@code "1.0.5"}, the result of <b>this</b> operation
     * would be {@code "my.context:BusinessName:1.0.5"}.
     *
     * @param s The output of {@link QualifiedName#toString()}, given to reconstruct it into a {@link QualifiedName}.
     * @return A reconstructed {@link QualifiedName} based on the expected output of {@link QualifiedName#toString()}.
     */
    public static QualifiedName fromString(@Nonnull String s) {
        Assert.nonEmpty(s, "Cannot construct a QualifiedName based on a null or empty String.");
        Matcher matcher = DEBUG_STRING_PATTERN.matcher(s);
        Assert.isTrue(matcher.matches(),
                      () -> "The given simple String [" + s + "] does not match the expected pattern.");

        return new QualifiedName(matcher.group(NAMESPACE_GROUP),
                                 matcher.group(LOCAL_NAME_GROUP),
                                 matcher.group(REVISION_GROUP));
    }

    /**
     * Prints the {@link QualifiedName} in a simplified {@link String}.
     * <p>
     * The {@link #namespace()}, {@link #localName()}, and {@link #revision()} are split by means of a semicolon
     * ({@code :}).
     * <p>
     * Thus, if {@code #namespace()} returns {@code "my.context"}, the {@code #localName()} returns
     * {@code "BusinessName"}, and the {@code #revision()} returns {@code "1.0.5"}, the result of <b>this</b> operation
     * would be {@code "my.context:BusinessName:1.0.5"}.
     *
     * @return A simple {@link String} based on the {@link #localName()}, {@link #namespace()}, and {@link #revision()},
     * delimited by a semicolon ({@code :}).
     */
    @Override
    public String toString() {
        return namespace + DELIMITER + localName + DELIMITER + revision;
    }
}
