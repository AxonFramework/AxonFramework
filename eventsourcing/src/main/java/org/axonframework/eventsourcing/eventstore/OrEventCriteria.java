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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link EventCriteria} that matches when at least one of the provided criteria matches.
 *
 * @param criteria The set of {@link EventCriteria} to match against, of which at least one must match.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
record OrEventCriteria(Set<EventCriteria> criteria) implements EventCriteria {

    @Override
    public Set<EventCriterion> flatten() {
        return criteria.stream()
                       .map(EventCriteria::flatten)
                       .flatMap(Collection::stream)
                       .collect(Collectors.toSet());
    }

    @Override
    public boolean matches(@Nonnull String type, @Nonnull Set<Tag> tags) {
        return criteria.stream().anyMatch(criteria -> criteria.matches(type, tags));
    }

    @Override
    public EventCriteria or(EventCriteria criteria) {
        if(criteria instanceof AnyEvent) {
            return criteria;
        }
        Set<EventCriteria> newCriteria = new HashSet<>(this.criteria);
        if(criteria instanceof OrEventCriteria(Set<EventCriteria> otherCriteria)) {
            newCriteria.addAll(otherCriteria);
        } else {
            newCriteria.add(criteria);
        }
        return new OrEventCriteria(newCriteria);
    }

    @Override
    public boolean hasCriteria() {
        return criteria.stream().anyMatch(EventCriteria::hasCriteria);
    }
}
