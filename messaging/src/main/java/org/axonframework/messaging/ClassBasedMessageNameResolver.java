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

import org.axonframework.common.ObjectUtils;

import javax.annotation.Nonnull;

/**
 * A {@link MessageNameResolver} using the {@link Class} of the given {@code payload} to base th e
 * {@link QualifiedName type} on.
 * <p>
 * The {@link Class#getPackageName()} becomes the {@link QualifiedName#namespace()} and the
 * {@link Class#getSimpleName()} becomes the {@link QualifiedName#localName()}. The {@link QualifiedName#revision()} is
 * defaulted to {@link QualifiedNameUtils#DEFAULT_REVISION}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class ClassBasedMessageNameResolver implements MessageNameResolver {

    private final String revision;

    /**
     * Constructs a {@link ClassBasedMessageNameResolver} using {@link QualifiedNameUtils#DEFAULT_REVISION} as the
     * revision for all resolved {@link QualifiedName names}.
     */
    public ClassBasedMessageNameResolver() {
        this(QualifiedNameUtils.DEFAULT_REVISION);
    }

    /**
     * Constructs a {@link ClassBasedMessageNameResolver} using the given {@code revision} as the revision for all
     * resolved {@link QualifiedName names}. If payload is already a message the {@link QualifiedName name} of the message is used without any changes.
     *
     * @param revision The revision for all resolved {@link QualifiedName names} by this {@link MessageNameResolver}
     *                 implementation
     */
    public ClassBasedMessageNameResolver(String revision) {
        this.revision = revision;
    }

    @Override
    public QualifiedName resolve(@Nonnull Object payload) {
        if (payload instanceof Message<?>) {
            return ((Message<?>) payload).name();
        }
        Class<Object> payloadClass = ObjectUtils.nullSafeTypeOf(payload);
        return new QualifiedName(payloadClass.getPackageName(),
                payloadClass.getSimpleName(),
                revision);
    }
}
