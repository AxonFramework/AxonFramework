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

import java.util.HashSet;
import java.util.Set;

/**
 * Describes the criteria for {@link EventStoreTransaction#source(SourcingCondition) sourcing} or
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} events. The criteria are used to filter the
 * events to read from the {@link AsyncEventStore event store}.
 * <p>
 * The criteria can be singular, or a combination of multiple criteria. Criteria can be combined using
 * {@link #or(EventCriteria)} or {@link #or()}, after which events will match either of the multiple criteria. For
 * example, given the following set of criteria in which we load the events required to decide if "student A" can be
 * added to "course B":
 * <pre>
 *     {@code
 *     EventCriteria criteria = EventCriteria
 *         .match()
 *         .eventTypes("StudentRegistered")
 *         .withTags(Tag.of("student", "studentA"))
 *         .or()
 *         .eventTypes("StudentAssignedToCourse", "CourseRegistered")
 *         .withTags(Tag.of("course", "courseB"))
 *
 *    }
 *    </pre>
 * <p>
 * The following events will match:
 * <ul>
 *     <li> Event [StudentRegistered, student -> studentA]</li>
 *     <li> Event [StudentAssignedToCourse, student -> studentB, course -> courseB]</li>
 *     <li> Event [CourseRegistered, course -> courseB]</li>
 * </ul>
 * <p>
 * The following events do not match:
 * <ul>
 *     <li> Event [StudentRegistered, student -> studentB]</li>
 *     <li> Event [StudentAssignedToCourse, student -> studentA, course -> courseA]</li>
 *     <li> Event [CourseRegistered, course -> courseA]</li>
 *     <li> Event [StudentAssignedToCourse, student -> studentB, course -> courseA]</li>
 * </ul>
 * <p>
 * The criteria can be flattened into a {@link Set} of {@link EventCriterion} instances, which are a specialized
 * representation of the criteria that are guaranteed to be non-nested. As such, these instances can be used to read
 * their {@link EventCriterion#tags()} and {@link EventCriterion#types()} without the need to interpret conditions,
 * as this is already done by the {@code flatten} method.
 *
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public sealed interface EventCriteria permits OrEventCriteria, FilteredEventCriteria, AnyEvent, EventCriterion {

    /**
     * Construct a {@code EventCriteria} that allows <b>any</b> events.
     * <p>
     * Use this instance when all events are of interest during
     * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or when there are no consistency
     * boundaries to validate during {@link EventStoreTransaction#appendEvent(EventMessage) appending}. Note that this
     * {@code EventCriteria} does not make sense for {@link EventStoreTransaction#source(SourcingCondition) sourcing},
     * as it is <b>not</b> recommended to source the entire event store.
     *
     * @return An {@code EventCriteria} that contains no criteria at all.
     */
    static EventCriteria anyEvent() {
        return AnyEvent.INSTANCE;
    }

    /**
     * Create a builder to construct a {@link FilteredEventCriteria} instance. This criteria will match events that
     * match the given type and tags.
     * <p>
     * The event will match on type if it is contained in the {@link EventCriteriaBuilder#eventTypes(String...)} set, or
     * if the set is empty.
     * <p>
     * The event will match on tags if it contains all tags in the
     * {@link EventCriteriaBuilderTagStage#withTags(Tag...)}} set. If the set is empty, the event will match on all
     * events. For example, given the following set of criteria :
     * <ul>
     *      <li>STUDENT -> A</li>
     *      <li>COURSE -> X</li>
     * </ul>
     * The following events will match:
     * <ul>
     *     <li> Event [STUDENT -> A, COURSE -> X]</li>
     *     <li> Event [STUDENT -> A, COURSE -> X, FACULTY -> 1]</li>
     * </ul>
     * But the following events do not:
     * <ul>
     *     <li> Event [STUDENT -> A]</li>
     *     <li> Event [STUDENT -> A, COURSE -> Z]</li>
     *     <li> Event [STUDENT -> B, COURSE -> Z]</li>
     *     <li> Event [STUDENT -> B, COURSE -> X]</li>
     *     <li> Event [STUDENT -> Z]</li>
     * </ul>
     * <p>
     * Note that constructing a {@link FilteredEventCriteria} makes most sense when Event-Sourcing entities.
     * For example, when sourcing events for an Aggregate, the criteria could be constructed as follows:
     *
     * <pre>
     *     {@code
     *     EventCriteria criteria = EventCriteria.filtered()
     *     .matchingEventTypes("StudentRegistered", "CourseRegistered")
     *     .matchingAllTags(Tag.of("studentId", "A"))
     *    }
     *    </pre>
     *
     * @return A builder to construct a {@link FilteredEventCriteria} instance.
     */
    static EventCriteriaBuilderEventTypeStage match() {
        return EventCriteriaBuilder.match();
    }

    /**
     * Create an {@code EventCriteria} that matches events that match either this {@code EventCriteria} or the given
     * {@code EventCriteria}.
     *
     * @param criteria The {@code EventCriteria} to match in addition to this {@code EventCriteria}.
     * @return An {@code EventCriteria} that matches events that match either this {@code EventCriteria} or the given
     * {@code EventCriteria}.
     */
    EventCriteria or(EventCriteria criteria);


    /**
     * Start a builder to construct an additional {@link FilteredEventCriteria} instance that when constructed matches
     * both the events as defined by the builder and the events as defined by the current criteria. Once construction is
     * complete, returns an {@link OrEventCriteria} that matches event that match either this {@code EventCriteria} or
     * the built one. See {@link EventCriteriaBuilder} for more details.
     *
     * @return A builder to construct a {@link FilteredEventCriteria} instance that, once built, will match events that
     * match either this {@code EventCriteria} or the built one.
     */
    default EventCriteriaBuilderEventTypeStage or() {
        return EventCriteriaBuilder.or(this);
    }

    /**
     * Indicates whether the given {@code type} and {@code tags} matches the types and tags defined in this or these
     * criteria. If no types are defined, any given {@code type} will be considered a match.
     * <p/>
     * See {@link #match()} for more details about how events are matched.
     *
     * @param type The type to match against this criteria instance.
     * @param tags The tags to match against this criteria instance.
     * @return {@code true} if the type matches, otherwise {@code false}.
     * @see #match()
     */
    boolean matches(@Nonnull String type, @Nonnull Set<Tag> tags);

    /**
     * Flatten this, possibly nested, {@code EventCriteria} into a {@link Set} of {@link EventCriterion}. These
     * {@code EventCriterion} instances can be used to construct queries against a data store, without the need to
     * interpret the criteria.
     *
     * @return The flattened set of {@code EventCriteria}.
     */
    Set<EventCriterion> flatten();
}
