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
 * During sourcing or streaming, the {@link #types()} and {@link #indices()} are used as a filter. While appending
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
public interface EventCriteria {

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
     * Construct a simple {@link EventCriteria} based on the given {@code index}.
     *
     * @param index The singular {@link Index} of the {@link EventCriteria} being constructed.
     * @return A simple {@link EventCriteria} based on the given {@code index}.
     */
    static EventCriteria hasIndex(@Nonnull Index index) {
        return new SingleIndexCriteria(index);
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
     * A {@link Set} of {@link Index Indices} applicable for sourcing, streaming, or appending events. An {@code Index}
     * can, for example, refer to a model's (aggregate) identifier name and value.
     *
     * @return The {@link Set} of {@link Index Indices} applicable for sourcing, streaming, or appending events.
     */
    Set<Index> indices();

    /**
     * Matches the given {@code indices} with the {@link #indices()} of this {@link EventCriteria}.
     * <p>
     * Returns {@code true} if they are deemed to be equal, {@code false} otherwise.
     *
     * @param indices The {@link Set} of {@link Index Indices} to compare with {@code this EventCriteria} its
     *                {@link #indices()}.
     * @return {@code true} if they are deemed to be equal, {@code false} otherwise.
     */
    default boolean matchingIndices(@Nonnull Set<Index> indices) {
        return this.indices().equals(indices);
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
