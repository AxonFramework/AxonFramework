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

/**
 * Registry for subscribing {@link ReplayStatusChangeHandler} instances.
 * <p>
 * Components implementing this interface accept replay status changed handler subscriptions, allowing dynamic
 * registration of replay status change behavior following the same pattern as event handler registration.
 * <p>
 * Example usage:
 * <pre>{@code
 * ReplayStatusChangeHandlerRegistry registry = ...;
 * registry.subscribe((statusChange, context) -> {
 *     if (statusChange.status() == ReplayStatus.REPLAY) {
 *         repository.deleteAll();
 *     }
 *     return MessageStream.empty();
 * });
 * }</pre>
 *
 * @param <S> the type of the registry itself, used for fluent interfacing
 * @author Simon Zambrovski
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @see ReplayStatusChangeHandler
 * @see org.axonframework.messaging.eventhandling.EventHandlingComponent
 * @since 5.1.0
 */
public interface ReplayStatusChangeHandlerRegistry<S extends ReplayStatusChangeHandlerRegistry<S>> {

    /**
     * Subscribes a replay status change handler to this registry.
     * <p>
     * The handler will be invoked when the {@link ReplayStatus} is about to change. Multiple handlers can be
     * subscribed, and all will be invoked when the replay status changes.
     *
     * @param replayStatusChangeHandler the replay status change handler to subscribe, must not be {@code null}
     * @return this registry instance for method chaining
     */
    S subscribe(ReplayStatusChangeHandler replayStatusChangeHandler);
}
