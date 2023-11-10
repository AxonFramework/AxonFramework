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

package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for a component that processes Messages.
 *
 * @param <T> The message type this handler can process
 * @author Rene de Waele
 * @since 3.0
 */
public interface MessageHandler<T extends Message<?>> {

    /**
     * Handles the given {@code message}.
     *
     * @param message The message to be processed.
     * @return The result of the message processing.
     * @throws Exception any exception that occurs during message handling
     */
    // TODO replace this operation for the new handle method
    default Object handleSync(T message) throws Exception {
        return handle(message).get();
    }

    CompletableFuture<Object> handle(T message);

    /**
     * Indicates whether this handler can handle the given message
     *
     * @param message The message to verify
     * @return {@code true} if this handler can handle the message, otherwise {@code false}
     */
    default boolean canHandle(T message) {
        return true;
    }

    /**
     * Returns the instance type that this handler delegates to.
     *
     * @return Returns the instance type that this handler delegates to
     */
    default Class<?> getTargetType() {
        return getClass();
    }

    /**
     * Indicates whether this handler can handle messages of given type
     *
     * @param payloadType The payloadType to verify
     * @return {@code true} if this handler can handle the payloadType, otherwise {@code false}
     */
    default boolean canHandleType(Class<?> payloadType) {
        return true;
    }
}
