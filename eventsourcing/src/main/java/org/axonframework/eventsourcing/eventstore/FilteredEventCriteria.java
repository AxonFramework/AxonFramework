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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Filtered {@link EventCriteria} that takes both the tags and the event's type into consideration.
 * <p>
 * Events match if the following conditions are met:
 * <ol>
 *     <li>The event's type is in the {@code types} set, or the {@code types} set is empty.</li>
 *     <li>The event tags contain all tags in the {@code tags} set, or the {@code tags} set is empty.</li>
 * </ol>
 * <p>
 * To construct an instance of a filtered {@link EventCriteria}, use the {@link EventCriteria#match()} method.
 *
 * @param types The types that this criteria instance matches with.
 * @param tags  The tags that events are expected to have.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
record FilteredEventCriteria(@Nonnull Set<String> types,
                             @Nonnull Set<Tag> tags) implements EventCriteria, EventCriterion {

    /**
     * Constructs an {@link EventCriteria} that matches against given {@code types} and the given {@code tags}. If the
     * {@code types} set is empty, the criteria will match against any type. If the {@code tags} set is empty, the
     * criteria will match against any tags.
     *
     * @param types The types that this criteria instance matches with.
     * @param tags  The tags that events are expected to have.
     */
    FilteredEventCriteria(@Nonnull Set<String> types,
                          @Nonnull Set<Tag> tags) {
        this.types = Set.copyOf(requireNonNull(types, "The types cannot be null"));
        this.tags = Set.copyOf(requireNonNull(tags, "The tags cannot be null"));
    }

    @Override
    public Set<EventCriterion> flatten() {
        if (types.isEmpty() && tags.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.of(this);
    }

    @Override
    public Set<String> types() {
        return types;
    }

    @Override
    public Set<Tag> tags() {
        return tags;
    }

    @Override
    public boolean matches(@Nonnull String type, @Nonnull Set<Tag> tags) {
        return matchesType(type) && matchesTags(tags);
    }

    private boolean matchesType(String type) {
        return types.isEmpty() || types.contains(type);
    }

    private boolean matchesTags(Set<Tag> tags) {
        return tags.containsAll(this.tags);
    }

    @Override
    public EventCriteria or(EventCriteria criteria) {
        if (criteria instanceof AnyEvent) {
            return criteria;
        }
        if (criteria.equals(this)) {
            return this;
        }

        // If the given criteria is an OrEventCriteria, let it be flattened
        if (criteria instanceof OrEventCriteria orEventCriteria) {
            return orEventCriteria.or(this);
        }

        return new OrEventCriteria(Set.of(this, criteria));
    }

    @Override
    public boolean hasCriteria() {
        return !tags.isEmpty() || !types.isEmpty();
    }
}
