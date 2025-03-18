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
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Builder class to construct a {@link FilteredEventCriteria} instance. The builder executes in two steps:
 *
 * <ol>
 *     <li>
 *         {@link EventCriteriaBuilderEventTypeStage The event type stage}, defining the types that this criteria
 *          instance matches with using {@link EventCriteriaBuilderEventTypeStage#eventTypes(String...)}.
 *     </li>
 *     <li>
 *         {@link EventCriteriaBuilderEventTypeStage The tag stage}, defining the tags that events are expected to have using
 *          {@link EventCriteriaBuilderTagStage#withTags(Tag...)}, {@link EventCriteriaBuilderTagStage#withTags(Set)}, or
 *          {@link EventCriteriaBuilderTagStage#withTags(String...)}.
 *     </li>
 * </ol>
 * <p>
 * The builder will return an {@link EventCriteria} instance that matches on the defined types and tags.
 *
 * @author Mitchell Herrijgers
 * @see EventCriteria
 * @since 5.0.0
 */
public class EventCriteriaBuilder implements EventCriteriaBuilderEventTypeStage, EventCriteriaBuilderTagStage {

    private final EventCriteria orCriteria;
    private final Set<String> eventTypes = new HashSet<>();


    private EventCriteriaBuilder(@Nullable EventCriteria orCriteria) {
        this.orCriteria = orCriteria;
    }


    /**
     * Start building a new {@link EventCriteria} instance.
     *
     * @return The builder in the {@link EventCriteriaBuilderEventTypeStage event type stage}.
     */
    public static EventCriteriaBuilderEventTypeStage match() {
        return new EventCriteriaBuilder(null);
    }

    /**
     * Start building a new {@link EventCriteria} instance that when constructed will be combined with the given
     * {@code criteria} using an OR operation.
     *
     * @param criteria The criteria to combine with the new criteria using an OR operation.
     * @return The builder in the {@link EventCriteriaBuilderEventTypeStage event type stage}.
     */
    public static EventCriteriaBuilderEventTypeStage or(EventCriteria criteria) {
        return new EventCriteriaBuilder(criteria);
    }

    public EventCriteriaBuilderTagStage eventTypes(@Nonnull String... types) {
        eventTypes.addAll(Arrays.asList(types));
        return this;
    }

    public EventCriteriaBuilderTagStage anyEventType() {
        return this;
    }

    public EventCriteria withTags(@Nonnull Set<Tag> tags) {
        if (tags.isEmpty()) {
            return withAnyTags();
        }
        FilteredEventCriteria criterion = new FilteredEventCriteria(eventTypes, tags);
        return wrapWithOrIfNecessary(criterion);
    }

    public EventCriteria withAnyTags() {
        if (eventTypes.isEmpty()) {
            return wrapWithOrIfNecessary(AnyEvent.INSTANCE);
        }
        FilteredEventCriteria filteredEventCriteria = new FilteredEventCriteria(eventTypes, Set.of());
        return wrapWithOrIfNecessary(filteredEventCriteria);
    }

    private EventCriteria wrapWithOrIfNecessary(EventCriteria criteria) {
        if (orCriteria == null) {
            return criteria;
        }
        return orCriteria.or(criteria);
    }
}
