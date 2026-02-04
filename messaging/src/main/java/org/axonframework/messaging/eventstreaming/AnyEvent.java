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

package org.axonframework.messaging.eventstreaming;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.QualifiedName;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of the {@link EventCriteria} allowing <b>any</b> event, regardless of its type or tags.
 * You can limit the types of events to be matched by using the {@link AnyEvent#andBeingOneOfTypes(Set)} method, which
 * will return a new {@link EventCriteria} that matches only the specified types.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
final class AnyEvent implements EventCriteria, EventTypeRestrictableEventCriteria {

    /**
     * Default instance of the {@link AnyEvent}.
     */
    static final AnyEvent INSTANCE = new AnyEvent();

    private AnyEvent() {
        // No-arg constructor to enforce use of INSTANCE constant.
    }

    @Override
    public Set<EventCriterion> flatten() {
        // AnyEvent does not have a criterion, as it matches all.
        return Collections.emptySet();
    }

    @Override
    public boolean matches(@Nonnull QualifiedName type, @Nonnull Set<Tag> tags) {
        return true;
    }

    @Override
    public String toString() {
        return "AnyEvent";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AnyEvent;
    }

    @Override
    public EventCriteria or(EventCriteria criteria) {
        // The AnyEvent is always matches, so the other criteria has no effect.
        return this;
    }

    @Override
    public boolean hasCriteria() {
        return false;
    }

    @Override
    public EventCriteria andBeingOneOfTypes(@Nonnull Set<QualifiedName> types) {
        Objects.requireNonNull(types, "The provided types should not be null");
        return new TagAndTypeFilteredEventCriteria(types, Collections.emptySet());
    }
}
