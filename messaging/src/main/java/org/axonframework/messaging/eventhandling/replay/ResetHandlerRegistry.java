/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.replay;

import jakarta.annotation.Nonnull;

/**
 * Registry for subscribing {@link ResetHandler} instances.
 * <p>
 * Components implementing this interface accept reset handler subscriptions, allowing dynamic
 * registration of reset behavior following the same pattern as event handler registration.
 * <p>
 * Example usage:
 * <pre>{@code
 * ResetHandlerRegistry registry = ...;
 * registry.subscribe((resetContext, context) -> {
 *     repository.deleteAll();
 *     return MessageStream.empty();
 * });
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see ResetHandler
 * @see org.axonframework.messaging.eventhandling.EventHandlingComponent
 * @since 5.0.0
 */
public interface ResetHandlerRegistry {

    /**
     * Subscribes a reset handler to this registry. The handler will be invoked when a reset
     * operation is triggered.
     * <p>
     * Multiple handlers can be subscribed, and all will be invoked during reset.
     *
     * @param resetHandler the reset handler to subscribe, must not be {@code null}
     * @return This registry instance for method chaining.
     */
    @Nonnull
    ResetHandlerRegistry subscribe(@Nonnull ResetHandler resetHandler);
}
