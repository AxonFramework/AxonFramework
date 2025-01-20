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
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * Interface describing criteria to be taken into account when
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing},
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending} events.
 * <p>
 * During sourcing or streaming, the criteria are used as a filter for events to read. While appending events, the
 * criteria are used to detect conflicts when appending events beyond the consistency boundary.
 *
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public sealed interface EventCriteria permits AnyEvent, DefaultEventCriteria {

    /**
     * Construct a {@code EventCriteria} that allows <b>any</b> events.
     * <p>
     * Use this instance when all events are of interest during
     * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or when there are no consistency
     * boundaries to validate during {@link EventStoreTransaction#appendEvent(EventMessage) appending}. Note that this
     * {@code EventCriteria} does not make sense for
     * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing}, as it is <b>not</b>
     * recommended to source the entire event store.
     *
     * @return An {@code EventCriteria} that contains no criteria at all.
     */
    static EventCriteria anyEvent() {
        return AnyEvent.INSTANCE;
    }

    /**
     * Create a builder for criteria that match events with any of the given {@code types}. Events with types not
     * matching any of the given types will not match against criteria built by the returned builder.
     *
     * @param types The types of messages to build the criteria for.
     * @return a builder that allows criteria to be built for the given event types.
     */
    static EventCriteria.Builder forEventTypes(@Nonnull String... types) {
        if (types.length == 0) {
            return EventCriteriaBuilder.NO_TYPES;
        }
        return new EventCriteriaBuilder(types);
    }

    /**
     * Create a builder for criteria that match events with any type.
     *
     * @return a builder that allows criteria to be built matching against any event type.
     */
    static EventCriteria.Builder forAnyEventType() {
        return EventCriteriaBuilder.NO_TYPES;
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
     * A {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events. An {@code Tag} can,
     * for example, refer to a model's (aggregate) identifier name and value.
     *
     * @return The {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events.
     */
    Set<Tag> tags();

    /**
     * Indicates whether the given {@code type} matches the types defined in this instance. If no types are defined, any
     * given {@code type} will be considered a match.
     *
     * @param type The type to match against this criteria instance.
     * @return {@code true} if the type matches, otherwise {@code false}.
     */
    default boolean matchingType(String type) {
        return types().isEmpty() || types().contains(type);
    }

    /**
     * Matches the given {@code tags} with the {@link #tags()} of this {@code EventCriteria}. An event matches against
     * this criteria instance if the tags in one of the criteria in this set are all found in that event.
     * <p>
     * For example, given the following set of criteria :
     * <ul>
     *     <li>
     *         1:
     *         <ul>
     *             <li>STUDENT -> A</li>
     *             <li>COURSE -> X</li>
     *         </ul>
     *     </li>
     *     <li>
     *         2:
     *         <ul>
     *             <li>STUDENT -> A</li>
     *             <li>COURSE -> Y</li>
     *         </ul>
     *     </li>
     *     <li>
     *         3:
     *         <ul>
     *             <li>COURSE -> Z</li>
     *         </ul>
     *     </li>
     * </ul>
     * The following events will match:
     * <ul>
     *     <li> Event [STUDENT -> A, COURSE -> X]</li>
     *     <li> Event [STUDENT -> A, COURSE -> X, FACULTY -> 1]</li>
     *     <li> Event [STUDENT -> A, COURSE -> Z]</li>
     *     <li> Event [STUDENT -> B, COURSE -> Z]</li>
     *     <li> Event [COURSE -> Z, FACULTY -> 1]</li>
     * </ul>
     * But the following events do not:
     * <ul>
     *     <li> Event [STUDENT -> B, COURSE -> X]</li>
     *     <li> Event [STUDENT -> A]</li>
     *     <li> Event [STUDENT -> Z]</li>
     * </ul>
     *
     * @param tags The {@link Set} of {@link Tag Tags} to compare with {@code this EventCriteria} its {@link #tags()}.
     * @return {@code true} if they are deemed to be equal, {@code false} otherwise.
     */
    default boolean matchingTags(@Nonnull Set<Tag> tags) {
        return tags.containsAll(this.tags());
    }

    /**
     * Indicates whether the given {@code type} and {@code tags} matches the types and tags defined in this instance. If
     * no types are defined, any given {@code type} will be considered a match.
     * <p/>
     * See {@link #matchingTags(Set)} for more details about how Tags are matched.
     *
     * @param type The type to match against this criteria instance.
     * @param tags The tags to match against this criteria instance.
     * @return {@code true} if the type matches, otherwise {@code false}.
     * @see #matchingType(String)
     * @see #matchingTags(Set)
     */
    default boolean matches(@Nonnull String type, @Nonnull Set<Tag> tags) {
        return matchingType(type) && matchingTags(tags);
    }

    /**
     * Interface providing operations during an intermediate state of creating an {@link EventCriteria} instance.
     */
    interface Builder {

        /**
         * Create a {@link EventCriteria} expecting events containing the given {@code tags}.
         *
         * @param tags The tags that events must have to match.
         * @return a criteria object that matches against the given tags.
         */
        EventCriteria withTags(@Nonnull Set<Tag> tags);

        /**
         * Create a {@link EventCriteria} expecting events containing the given {@code tags}.
         *
         * @param tags The tags that events must have to match.
         * @return a criteria object that matches against the given tags.
         */
        EventCriteria withTags(@Nonnull Tag... tags);

        /**
         * Create a {@link EventCriteria} expecting events containing the given {@code tagKeyValuePairs}. The given
         * values are used in pairs to construct a tag with the first parameter as key, and the second as value,
         * repeating until all parameters are used to create tags.
         *
         * @param tagKeyValuePairs The tags that events must have to match.
         * @return a criteria object that matches against the given tags.
         * @throws IllegalArgumentException if an odd number of parameters are provided.
         */
        EventCriteria withTags(@Nonnull String... tagKeyValuePairs);

        /**
         * Create a {@link EventCriteria} that doesn't require any specific tags on events to match.
         *
         * @return a criteria object that matches against all events.
         */
        EventCriteria withAnyTags();
    }
}
