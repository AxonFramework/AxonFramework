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
import org.axonframework.common.ObjectUtils;

import java.util.Optional;

/**
 * Functional interface describing a resolver from {@link Message#getPayload() Message payload} to it's
 * {@link MessageType type}. Used to set the {@link Message#type() type} when putting the given payload on its
 * respective bus.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface MessageTypeResolver {

    /**
     * Resolves a {@link MessageType type} for the given {@code payload}. If the given {@code payload} is already a
     * {@link Message} implementation, the {@link Message#type() Message Type} is returned.
     *
     * @param payload The {@link Message#getPayload() Message payload} to resolve a {@link MessageType type} for.
     * @return The {@link MessageType type} for the given {@code payload}.
     * @throws MessageTypeNotResolvedException if the {@link MessageType type} could not be resolved.
     */
    default MessageType resolveOrThrow(Object payload) {
        if (payload instanceof Message<?>) {
            return ((Message<?>) payload).type();
        }
        return resolveOrThrow(ObjectUtils.nullSafeTypeOf(payload));
    }

    /**
     * Resolves a {@link MessageType type} for the given {@code payloadType}.
     *
     * @param payloadType The {@link Class type} of the {@link Message#getPayload() Message payload} to resolve a
     *                    {@link MessageType type} for.
     * @return The {@link MessageType type} for the given {@code payloadType}.
     * @throws MessageTypeNotResolvedException if the {@link MessageType type} could not be resolved.
     */
    default MessageType resolveOrThrow(@Nonnull Class<?> payloadType){
        return resolve(payloadType)
                .orElseThrow(() -> new MessageTypeNotResolvedException("Cannot resolve MessageType for the payload type [" + payloadType.getName() + "]"));
    }

    /**
     * Resolves a {@link MessageType type} for the given {@code payload}. If the given {@code payload} is already a
     * {@link Message} implementation, the {@link Message#type() Message Type} is returned.
     * <p>
     * This method returns an {@link Optional} that will be empty if the
     * {@link MessageType type} could not be resolved.
     *
     * @param payload The {@link Message#getPayload() Message payload} to resolve a {@link MessageType type} for.
     * @return An {@link Optional} containing the {@link MessageType type} for the given {@code payload},
     *         or empty if the type could not be resolved.
     */
    default Optional<MessageType> resolve(@Nonnull Object payload) {
        if (payload instanceof Message<?>) {
            return Optional.of(((Message<?>) payload).type());
        }
        return resolve(ObjectUtils.nullSafeTypeOf(payload));
    }

    /**
     * Resolves a {@link MessageType type} for the given {@code payloadType}.
     * <p>
     * This method returns an {@link Optional} that will be empty if the
     * {@link MessageType type} could not be resolved.
     *
     * @param payloadType The {@link Class type} of the {@link Message#getPayload() Message payload} to resolve a
     *                    {@link MessageType type} for.
     * @return An {@link Optional} containing the {@link MessageType type} for the given {@code payloadType},
     *         or empty if the type could not be resolved.
     */
    Optional<MessageType> resolve(@Nonnull Class<?> payloadType);
}
