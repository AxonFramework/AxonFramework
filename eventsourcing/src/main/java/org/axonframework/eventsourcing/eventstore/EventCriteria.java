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
 * Interface describing criteria to be taken into account when
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing},
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending} events.
 * <p>
 * During sourcing or streaming, the {@link #types()} and {@link #tags()} are used as a filter. While appending
 * events, the {@code #types()} and {@code #tags()} are used to validate the consistency boundary of the event(s) to
 * append. The latter happens starting from the {@link AppendCondition#consistencyMarker()}.
 *
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public sealed interface EventCriteria permits NoEventCriteria, SingleTagCriteria, CombinedEventCriteria {

    /**
     * Construct a {@link EventCriteria} that contains no criteria at all.
     * <p>
     * Use this instance when all events are of interest during
     * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or when there are no consistency
     * boundaries to validate during {@link EventStoreTransaction#appendEvent(EventMessage) appending}. Note that this
     * {@link EventCriteria} does not make sense for
     * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing}, as it is <b>not</b>
     * recommended to source the entire event store.
     *
     * @return An {@link EventCriteria} that contains no criteria at all.
     */
    static EventCriteria noCriteria() {
        return NoEventCriteria.INSTANCE;
    }

    /**
     * Construct a simple {@link EventCriteria} based on the given {@code tag}.
     *
     * @param tag The singular {@link Tag} of the {@link EventCriteria} being constructed.
     * @return A simple {@link EventCriteria} based on the given {@code tag}.
     */
    static EventCriteria hasTag(@Nonnull Tag tag) {
        return new SingleTagCriteria(tag);
    }

    /**
     * A {@link Set} of {@link String} containing all the types of events applicable for sourcing, streaming, or
     * appending events.
     *
     * @return The {@link Set} of {@link String} containing all the types of events applicable for sourcing, streaming,
     * or appending events.
     */
    Set<String> types();

    /**
     * A {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events. An {@code Tag}
     * can, for example, refer to a model's (aggregate) identifier name and value.
     *
     * @return The {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events.
     */
    Set<Tag> tags();

    /**
     * Matches the given {@code tags} with the {@link #tags()} of this {@link EventCriteria}.
     * <p>
     * Returns {@code true} if they are deemed to be equal, {@code false} otherwise.
     *
     * @param tags The {@link Set} of {@link Tag Tags} to compare with {@code this EventCriteria} its
     *                {@link #tags()}.
     * @return {@code true} if they are deemed to be equal, {@code false} otherwise.
     */
    default boolean matchingTags(@Nonnull Set<Tag> tags) {
        return this.tags().equals(tags);
    }

    /**
     * Combines {@code this} {@link EventCriteria} with {@code that EventCriteria}.
     *
     * @param that The {@link EventCriteria} to combine with {@code this}.
     * @return A combined {@link EventCriteria}, consisting out of {@code this} and the given {@code that}.
     */
    default EventCriteria combine(@Nonnull EventCriteria that) {
        return new CombinedEventCriteria(this, that);
    }
}
