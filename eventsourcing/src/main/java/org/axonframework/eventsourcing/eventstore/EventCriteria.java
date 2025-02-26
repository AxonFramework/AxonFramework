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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Interface describing criteria to be taken into account when
 * {@link EventStoreTransaction#source(SourcingCondition) sourcing},
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
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public sealed interface EventCriteria
        permits EventCriteria.TagsCriteria, EventCriteria.EventTypesCriteria, EventCriteria.AndEventCriteria,
        EventCriteria.OrEventCriteria, AnyEvent {

    /**
     * Construct a new {@code EventCriteria} instance that matches events with the given {@code tags}. Any matching tag
     * will be considered a match.
     *
     * @param tags The tags to match against.
     * @return A new {@code EventCriteria} instance that matches events with the given {@code tags}.
     */
    static EventCriteria forTags(@Nonnull Set<Tag> tags) {
        return new TagsCriteria(tags);
    }

    /**
     * Construct a new {@code EventCriteria} instance that matches events with the given {@code tags}. Any matching tag
     * will be considered a match.
     *
     * @param tags The tags to match against.
     * @return A new {@code EventCriteria} instance that matches events with the given {@code tags}.
     */
    static EventCriteria.TagsCriteria forTags(@Nonnull Tag... tags) {
        return new TagsCriteria(Set.of(tags));
    }

    /**
     * Construct a new {@code EventCriteria} instance that matches events with the given {@code types}.
     *
     * @param types The types to match against.
     * @return A new {@code EventCriteria} instance that matches events with the given {@code types}.
     */
    static EventCriteria forTypes(@Nonnull Set<String> types) {
        return new EventTypesCriteria(types);
    }

    /**
     * Construct a new {@code EventCriteria} instance that matches events with the given {@code types}.
     *
     * @param types The types to match against.
     * @return A new {@code EventCriteria} instance that matches events with the given {@code types}.
     */
    static EventCriteria forTypes(@Nonnull String... types) {
        return new EventTypesCriteria(Set.of(types));
    }

    /**
     * Construct a new {@code EventCriteria} instance that matches events with the given {@code types}. All criteria
     * need to match for the event to be considered a match.
     *
     * @param criteria The criteria to match against.
     * @return A new {@code EventCriteria} instance that matches events with the given {@code types}.
     */
    static EventCriteria and(EventCriteria... criteria) {
        return new AndEventCriteria(criteria);
    }

    /**
     * Construct a new {@code EventCriteria} instance that matches events with the given {@code types}. All criteria
     * need to match for the event to be considered a match.
     *
     * @param criteria The criteria to match against.
     * @return A new {@code EventCriteria} instance that matches events with the given {@code types}.
     */
    static EventCriteria or(EventCriteria... criteria) {
        return new OrEventCriteria(criteria);
    }

    /**
     * Construct a {@code EventCriteria} that allows <b>any</b> events.
     * <p>
     * Use this instance when all events are of interest during
     * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or when there are no consistency
     * boundaries to validate during {@link EventStoreTransaction#appendEvent(EventMessage) appending}. Note that this
     * {@code EventCriteria} does not make sense for
     * {@link EventStoreTransaction#source(SourcingCondition) sourcing}, as it is <b>not</b>
     * recommended to source the entire event store.
     *
     * @return An {@code EventCriteria} that contains no criteria at all.
     */
    static EventCriteria anyEvent() {
        return AnyEvent.INSTANCE;
    }

    /**
     * Indicates whether the given {@code tags} match the conditions defined in this instance. If no tags are defined,
     * any given {@code tags} will be considered a match.
     *
     * @param tags The tags to match against this criteria instance.
     * @return {@code true} if the tags match, otherwise {@code false}.
     */
    boolean matchingTags(@Nonnull Set<Tag> tags);

    /**
     * Indicates whether the given {@code type} matches the type defined in this instance. If no type is defined, any
     * given {@code type} will be considered a match.
     *
     * @param type The type to match against this criteria instance.
     * @return {@code true} if the type matches, otherwise {@code false}.
     */
    boolean matchingType(@Nonnull String type);

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
     * Criteria that matches when any of the given delegate criteria match.
     */
    final class OrEventCriteria implements EventCriteria {

        private final List<EventCriteria> delegates;

        public OrEventCriteria(
                EventCriteria... delegates
        ) {
            this.delegates = Arrays.asList(delegates);
        }

        @Override
        public boolean matchingTags(@Nonnull Set<Tag> tags) {
            return delegates.stream().anyMatch(criteria -> criteria.matchingTags(tags));
        }

        @Override
        public boolean matchingType(@Nonnull String type) {
            return delegates.stream().anyMatch(criteria -> criteria.matchingType(type));
        }

        public List<EventCriteria> getDelegates() {
            return delegates;
        }
    }

    /**
     * Criteria that matches when all of the given delegate criteria match.
     */
    final class AndEventCriteria implements EventCriteria {

        private final List<EventCriteria> delegates;

        public AndEventCriteria(
                EventCriteria... delegates
        ) {
            this.delegates = Arrays.asList(delegates);
        }

        @Override
        public boolean matchingTags(@Nonnull Set<Tag> tags) {
            return delegates.stream().allMatch(criteria -> criteria.matchingTags(tags));
        }

        @Override
        public boolean matchingType(@Nonnull String type) {
            return delegates.stream().allMatch(criteria -> criteria.matchingType(type));
        }

        public List<EventCriteria> getDelegates() {
            return delegates;
        }
    }

    /**
     * Criteria that matches when one of the given tags is present in the event.
     *
     * @param tags The tags to match against.
     */
    record TagsCriteria(Set<Tag> tags) implements EventCriteria {

        @Override
        public boolean matchingTags(@Nonnull Set<Tag> tags) {
            return tags.stream().anyMatch(this.tags::contains);
        }

        @Override
        public boolean matchingType(@Nonnull String type) {
            return true;
        }
    }

    /**
     * Criteria that matches when the event is one of the given types.
     *
     * @param types The types to match against.
     */
    record EventTypesCriteria(Set<String> types) implements EventCriteria {

        @Override
        public boolean matchingTags(@Nonnull Set<Tag> tags) {
            return true;
        }

        @Override
        public boolean matchingType(@Nonnull String type) {
            return types.contains(type);
        }
    }

}
