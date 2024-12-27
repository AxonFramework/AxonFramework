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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * Implementation of the {@link EventCriteria} allowing <b>any</b> event.
 * <p>
 * Use this instance when all events are of interest during
 * {@link StreamableEventSource#open(String, org.axonframework.eventsourcing.eventstore.StreamingCondition) streaming}
 * or when there are no consistency boundaries to validate during
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending}. Note that {@code EventCriteria} criteria does not
 * make sense for {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing}, as it is
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
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Tag> tags() {
        return Set.of();
    }

    @Override
    public boolean matchingTags(@Nonnull Set<Tag> tags) {
        return true;
    }
}
