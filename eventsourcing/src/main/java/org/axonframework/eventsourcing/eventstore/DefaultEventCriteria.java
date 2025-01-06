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

import static java.util.Objects.requireNonNull;

/**
 * Implementation of the {@link EventCriteria} that takes both message types and their tags into consideration.
 *
 * @author Steven van Beelen
 * @author Allard Buijze
 * @since 5.0.0
 */
record DefaultEventCriteria(Set<String> types, Set<Tag> tags) implements EventCriteria {

    /**
     * Constructs Event Criteria that matches against given {@code types} if they match with given {@code tags}
     * @param types The types that this criteria instance matches with
     * @param tags The tags that events are expected to have
     */
    DefaultEventCriteria(@Nonnull Set<String> types,
                         @Nonnull Set<Tag> tags) {
        this.types = Set.copyOf(requireNonNull(types, "The types cannot be null"));
        this.tags = Set.copyOf(requireNonNull(tags, "The tags cannot be null"));
    }

    @Override
    public Set<String> types() {
        return Set.copyOf(types);
    }

    @Override
    public Set<Tag> tags() {
        return Set.copyOf(tags);
    }
}
