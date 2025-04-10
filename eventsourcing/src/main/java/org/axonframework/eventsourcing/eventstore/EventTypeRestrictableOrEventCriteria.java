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
import org.axonframework.messaging.QualifiedName;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Or-version of the {@link EventTypeRestrictableEventCriteria} that can handle an OR condition on {@link EventCriteria}
 * that can still be further specified by the {@link EventTypeRestrictableEventCriteria}.
 *
 * @param buildingCriteria The {@link EventTypeRestrictableEventCriteria} that is being built. The
 *                         {@link EventTypeRestrictableEventCriteria} methods will apply to this instance.
 * @param otherCriteria    The {@link EventCriteria} that is being OR'ed with the {@code buildingCriteria}. This
 *                         instance will not be modified.
 */
record EventTypeRestrictableOrEventCriteria(
        EventTypeRestrictableEventCriteria buildingCriteria,
        EventCriteria otherCriteria
) implements EventTypeRestrictableEventCriteria {

    @Override
    public EventCriteria andBeingOfType(@Nonnull Set<QualifiedName> types) {
        EventCriteria builtEventCriteria = buildingCriteria.andBeingOfType(types);
        return otherCriteria.or(builtEventCriteria);
    }

    @Override
    public EventCriteria or(EventCriteria criteria) {
        return otherCriteria.or(buildingCriteria)
                            .or(criteria);
    }

    @Override
    public boolean matches(@Nonnull QualifiedName type, @Nonnull Set<Tag> tags) {
        return buildingCriteria.matches(type, tags)
                || otherCriteria.matches(type, tags);
    }

    @Override
    public Set<EventCriterion> flatten() {
        return Stream.of(buildingCriteria, otherCriteria)
                     .map(EventCriteria::flatten)
                     .flatMap(Set::stream)
                     .collect(Collectors.toSet());
    }

    @Override
    public boolean hasCriteria() {
        return Stream.of(buildingCriteria, otherCriteria)
                     .anyMatch(EventCriteria::hasCriteria);
    }
}
