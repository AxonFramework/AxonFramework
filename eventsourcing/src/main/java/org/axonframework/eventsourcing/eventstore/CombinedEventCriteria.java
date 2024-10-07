/*
 * Copyright (c) 2010-2024. Axon Framework
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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of the {@link EventCriteria} combining two different {@code EventCriteria} instances into a single
 * {@code EventCriteria}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class CombinedEventCriteria implements EventCriteria {

    private final Set<String> types;
    private final Set<Index> indices;

    /**
     * Constructs a {@link CombinedEventCriteria} combining the {@link #types()} and {@link #indices()} of the given
     * {@code first} and {@code second} {@link EventCriteria}.
     *
     * @param first  The {@link EventCriteria} to combine with the {@code second} into a single {@code EventCriteria}.
     * @param second The {@link EventCriteria} to combine with the {@code first} into a single {@code EventCriteria}.
     */
    CombinedEventCriteria(@Nonnull EventCriteria first,
                          @Nonnull EventCriteria second) {
        this.types = new HashSet<>(first.types());
        this.types.addAll(second.types());

        this.indices = new HashSet<>(first.indices());
        this.indices.addAll(second.indices());
    }

    @Override
    public Set<String> types() {
        return Set.copyOf(types);
    }

    @Override
    public Set<Index> indices() {
        return Set.copyOf(indices);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CombinedEventCriteria that = (CombinedEventCriteria) o;
        return Objects.equals(types, that.types) && Objects.equals(indices, that.indices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(types, indices);
    }
}
