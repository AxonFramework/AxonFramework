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

import java.util.Set;
import java.util.function.Function;

/**
 * Functional interface towards resolving a {@link Set} of {@link Tag Tags} for a given event of type {@code E}.
 *
 * @param <E> The event for which to resolve a {@link Set} of {@link Tag Tags} for.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface TagResolver<E> extends Function<E, Set<Tag>> {

    /**
     * Resolves a {@link Set} of {@link Tag Tags} for the given {@code event} of type {@code E}.
     *
     * @param event The event to resolve a {@link Set} of {@link Tag Tags} for.
     * @return A {@link Set} of {@link Tag Tags} for the given {@code event} of type {@code E}.
     */
    Set<Tag> resolve(@Nonnull E event);

    @Override
    default Set<Tag> apply(@Nonnull E e) {
        return resolve(e);
    }
}
