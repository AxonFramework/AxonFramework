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
import org.axonframework.common.StringUtils;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Interface describing a qualified name, providing space for a {@link #namespace() namespace},
 * {@link #localName() local name}, and {@link #revision() revision}.
 * <p>
 * Useful to provide clear names to {@link Message Messages}, {@link MessageHandler MessageHandlers}, and other
 * components that require naming.
 * <p>
 * Note that all characters <b>except</b> for the semicolon ({@code :}) are allowed for any of the three parameters. The
 * semicolon acts as a delimiter for the {@link #toSimpleString()} and thus is a special character delimited on in the
 * {@link QualifiedName#simpleStringName(String)} method.
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

    private static final Pattern SIMPLE_STRING_PATTERN = Pattern.compile("^([^:]+):([^:]+):([^:]+)$");
    private static final int NAMESPACE_GROUP = 1;
    private static final int LOCAL_NAME_GROUP = 2;
    private static final int REVISION_GROUP = 3;

    /**
     * The default {@link #revision()} to use when none is present. Defaults to {@code "0.0.1"} as the revision.
     */
    public static final String DEFAULT_REVISION = "0.0.1";
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
        assertThat(namespace,
                   n -> StringUtils.nonEmptyOrNull(n) && !n.contains(DELIMITER),
                   "The given namespace [" + namespace
                           + "] is unsupported because it is null, empty, or contains a semicolon (:).");
        assertThat(localName,
                   l -> StringUtils.nonEmptyOrNull(l) && !l.contains(DELIMITER),
                   "The given local name [" + localName
                           + "] is unsupported because it is null, empty, or contains a semicolon (:).");
        assertThat(revision,
                   r -> StringUtils.nonEmptyOrNull(r) && !r.contains(DELIMITER),
                   "The given revision [" + revision
                           + "] is unsupported because it is null, empty, or contains a semicolon (:).");
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
        Matcher matcher = SIMPLE_STRING_PATTERN.matcher(simpleString);
        assertThat(matcher, Matcher::matches,
                   "The given simple String [" + simpleString + "] does not match the expected pattern.");

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
    public String toSimpleString() {
        return namespace + DELIMITER + localName + DELIMITER + revision;
    }
}
