/*
 * Copyright (c) 2010-2017. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model;

import org.axonframework.messaging.Message;

import java.util.function.Supplier;

/**
 * Interface describing the required functionality to forward a message.
 * An example implementation is the {@link ForwardAll}, which forwards all incoming messages.
 */
public interface ForwardingMode<T extends Message<?>> {

    /**
     * Creates an instance of an implementation of a {@link ForwardingMode}.
     *
     * @return an implementation of a {@link ForwardingMode}.
     */
    ForwardingMode getInstance(Supplier<ForwardingMode> forwardingModeConstructor);

    /**
     * Check whether the given {@code message} should be forwarded to the given {@code target}.
     *
     * @param message the message of type {@code T} to be forwarded.
     * @param target  the target of type {@code E} where the {@code message} shoudl be forwarded to
     * @param <E>     the type of the {@code target}
     * @return true if the {@code message} should be forwarded; false if it should not.
     */
    <E> boolean forwardMessage(T message, E target);
}
