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

import org.axonframework.common.ObjectUtils;

import javax.annotation.Nonnull;

/**
 * A {@link MessageTypeResolver} using the {@link Class} of the given {@code payload} to base the
 * {@link MessageType type} on.
 * <p>
 * The {@link Class#getPackageName()} becomes the {@link QualifiedName#namespace()} and the
 * {@link Class#getSimpleName()} becomes the {@link QualifiedName#localName()} of the
 * {@link MessageType#qualifiedName()}. The {@link MessageType#revision()} is defaulted to {@link #DEFAULT_REVISION}
 * when not specified differently through this class' constructor.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class ClassBasedMessageTypeResolver implements MessageTypeResolver {

    private final String revision;

    /**
     * Constructs a {@link ClassBasedMessageTypeResolver} using {@link MessageType#DEFAULT_VERSION} as the revision for
     * all resolved {@link MessageType types}.
     */
    public ClassBasedMessageTypeResolver() {
        this(MessageType.DEFAULT_VERSION);
    }

    /**
     * Constructs a {@link ClassBasedMessageTypeResolver} using the given {@code revision} as the revision for all
     * resolved {@link MessageType types}. If payload is already a message the {@code type} of the message is used
     * without any changes.
     *
     * @param revision The revision for all resolved {@link MessageType types} by this {@link MessageTypeResolver}
     *                 implementation.
     */
    public ClassBasedMessageTypeResolver(String revision) {
        this.revision = revision;
    }

    @Override
    public MessageType resolve(@Nonnull Object payload) {
        if (payload instanceof Message<?>) {
            return ((Message<?>) payload).type();
        }
        return new MessageType(ObjectUtils.nullSafeTypeOf(payload), revision);
    }
}
