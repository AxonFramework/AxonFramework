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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;

/**
 * Represents a position from which sourcing may start.
 *
 * @author John Hendrikx
 * @since 5.0.0
 */
public sealed interface Position permits GlobalIndexPosition, AggregateSequenceNumberPosition, StartPosition {

    /**
     * Represents the smallest possible position.
     */
    static final Position START = new StartPosition();

    /**
     * Returns the smallest of the two positions. If one of the positions is {@link #START},
     * this function will always return this value. Implementors must ensure this operation
     * is symmetric, and so should always return {@link #START} when called with it.
     *
     * @param other Another position, cannot be {@code null}.
     * @return The smallest of the two positions, never {@code null}.
     * @throws NullPointerException When any argument is {@code null}.
     * @throws IllegalArgumentException When the given position is incompatible with this position.
     */
    @Nonnull Position min(@Nonnull Position other);
}
