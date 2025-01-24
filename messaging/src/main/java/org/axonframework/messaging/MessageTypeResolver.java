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
     */
    <P> MessageType resolve(P payload);
}
