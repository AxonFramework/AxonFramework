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

package org.axonframework.modelling.entity.child;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;

/**
 * Functional interface to test whether a given entity should be invoked for a given message.
 *
 * @param <E> The type of the entity.
 * @param <M> The type of the message.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
public interface ChildEntityMatcher<E, M extends Message<?>> {

    /**
     * Tests whether the given {@code entity} should be invoked for the given {@code message}.
     *
     * @param entity  The entity to test.
     * @param message The message to test.
     * @return {@code true} if the entity should be invoked for the message, {@code false} otherwise.
     */
    boolean matches(@Nonnull E entity, @Nonnull M message);
}
