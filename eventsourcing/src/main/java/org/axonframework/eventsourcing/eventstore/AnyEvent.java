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
import org.axonframework.eventhandling.EventMessage;

import java.util.Collections;
import java.util.Set;

/**
 * Implementation of the {@link EventCriteria} allowing <b>any</b> event, regardless of its type or tags.
 * <p>
 * Use this instance when all events are of interest during
 * {@link StreamableEventSource#open(String, org.axonframework.eventsourcing.eventstore.StreamingCondition) streaming}
 * or when there are no consistency boundaries to validate during
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending}. Note that {@code AnyEvent} criteria does not make
 * sense for {@link EventStoreTransaction#source(SourcingCondition) sourcing}, as it is
 * <b>not</b> recommended to source the entire event store.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
final class AnyEvent implements EventCriteria {

    /**
     * Default instance of the {@link AnyEvent}.
     */
    static final AnyEvent INSTANCE = new AnyEvent();

    private AnyEvent() {
        // No-arg constructor to enforce use of INSTANCE constant.
    }

    @Override
    public Set<EventCriterion> flatten() {
        // AnyEvent does not have any criteria to flatten
        return Collections.emptySet();
    }

    @Override
    public boolean matches(@Nonnull String type, @Nonnull Set<Tag> tags) {
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
        return this;
    }
}
