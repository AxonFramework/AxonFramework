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

import static org.axonframework.common.BuilderUtils.assertNonEmpty;

/**
 * Simple implementation of the {@link QualifiedName}.
 *
 * @param namespace The {@link String} representing the {@link #namespace()} of this {@link QualifiedName}.
 * @param localName The {@link String} representing the {@link #localName()} of this {@link QualifiedName}.
 * @param revision  The {@link String} representing the {@link #revision()} of this {@link QualifiedName}.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
record SimpleQualifiedName(@Nullable String namespace,
                           @Nonnull String localName,
                           @Nullable String revision) implements QualifiedName {

    SimpleQualifiedName(String namespace, String localName) {
        this(namespace, localName, null);
    }

    SimpleQualifiedName(String namespace, String localName, String revision) {
        assertNonEmpty(localName, "The localName must not be null or empty.");
        this.namespace = Objects.requireNonNullElse(namespace, "");
        this.localName = localName;
        this.revision = Objects.requireNonNullElse(revision, "");
    }
}
