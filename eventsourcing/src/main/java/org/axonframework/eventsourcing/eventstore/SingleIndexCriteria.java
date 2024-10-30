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

import java.util.Objects;
import java.util.Set;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * An {@link EventCriteria} implementation dedicated towards a single {@link Index}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
final class SingleIndexCriteria implements EventCriteria {

    private final Index index;

    /**
     * Construct a {@link SingleIndexCriteria} using the given {@code index} as the singular {@link Index} of this
     * {@link EventCriteria}.
     *
     * @param index The singular {@link Index} of this {@link EventCriteria}.
     */
    SingleIndexCriteria(@Nonnull Index index) {
        assertNonNull(index, "The Index cannot be null");

        this.index = index;
    }

    @Override
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Index> indices() {
        return Set.of(index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SingleIndexCriteria that = (SingleIndexCriteria) o;
        return Objects.equals(index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(index);
    }
}
