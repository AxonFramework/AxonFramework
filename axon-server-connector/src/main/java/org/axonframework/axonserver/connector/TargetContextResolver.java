/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector;

import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.Message;

/**
 * Interface towards a mechanism that is capable of resolving the context name to which a {@link Message} should be
 * routed. This is used in multi-context situations, where certain Messages need to be routed to another "Bounded
 * Context" than the one the application itself is registered in.
 *
 * @param <T> the type of {@link Message} to resolve the context for
 * @author Allard Buijze
 * @since 4.2
 */
@FunctionalInterface
public interface TargetContextResolver<T extends Message<?>> {

    /**
     * Provides the context to which a message should be routed.
     *
     * @param message the message to resolve the context for
     * @return the name of the context this message should be routed to or {@code null} if the message does not specify
     * any context
     */
    String resolveContext(T message);

    /**
     * Returns a TargetContextResolver that uses {@code this} instance to resolve a context for a given message and if
     * it returns {@code null}, returns the results of the {@code other} resolver instead.
     *
     * @param other the resolver that will provide a context if {@code this} doesn't return one
     * @return a TargetContextResolver that resolves the context from either {@code this}, or the {@code other}
     */
    default TargetContextResolver<T> orElse(TargetContextResolver<? super T> other) {
        return message -> ObjectUtils.getOrDefault(resolveContext(message), () -> other.resolveContext(message));
    }

    /**
     * Returns a no-op TargetContextResolver. This will default to returning {@code null} on any {@link Message}
     * provided.
     *
     * @return a no-op TargetContextResolver. This will default to returning {@code null} on any {@link Message}
     * provided
     */
    static TargetContextResolver<Message<?>> noOp() {
        return message -> null;
    }
}
