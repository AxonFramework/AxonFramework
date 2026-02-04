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

import java.util.Objects;
import java.util.Set;

/**
 * Builder class to construct an {@link EventCriteria} that will match the to-be-built criteria and the already existing
 * criteria. Mimics the static methods of the {@link EventCriteria} to provide a consistent, fluent API.
 *
 * @author Mitchell Herrijgers
 * @see EventCriteria
 * @since 5.0.0
 */
public final class OrEventCriteriaBuilder {

    private final EventCriteria orCriteria;

    OrEventCriteriaBuilder(@Nonnull EventCriteria orCriteria) {
        this.orCriteria = Objects.requireNonNull(orCriteria, "orCriteria may not be null");
    }

    /**
     * Define that the event must contain all the provided {@code tags} to match. These tags function in an AND
     * relation, meaning that an event must have all tags to match. A partial match is not sufficient.
     * <p>
     * You can further limit the types of events to be matched by using the
     * {@link EventTypeRestrictableEventCriteria#andBeingOneOfTypes(Set)} method.
     *
     * @param tags The tags to match against.
     * @return The completed EventCriteria instance.
     */
    public EventTypeRestrictableEventCriteria havingTags(@Nonnull Set<Tag> tags) {
        return wrap(EventCriteria.havingTags(tags));
    }

    /**
     * Define that the event must contain all the provided {@code tags} to match. These tags function in an AND
     * relation, meaning that an event must have all tags to match. A partial match is not sufficient.
     * <p>
     * You can further limit the types of events to be matched by using the
     * {@link EventTypeRestrictableEventCriteria#andBeingOneOfTypes(Set)} method.
     *
     * @param tags The tags to match against.
     * @return The completed EventCriteria instance.
     */
    public EventTypeRestrictableEventCriteria havingTags(@Nonnull Tag... tags) {
        return wrap(EventCriteria.havingTags(tags));
    }

    /**
     * Define that the event must contain all the provided {@code tags} to match. These tags function in an AND
     * relation, meaning that an event must have all tags to match. A partial match is not sufficient.
     * <p>
     * You can further limit the types of events to be matched by using the
     * {@link EventTypeRestrictableEventCriteria#andBeingOneOfTypes(Set)} method.
     *
     * @param tags The tags to match against.
     * @return The completed EventCriteria instance.
     */
    public EventTypeRestrictableEventCriteria havingTags(@Nonnull String... tags) {
        return wrap(EventCriteria.havingTags(tags));
    }

    /**
     * Construct a {@code EventCriteria} that allows <b>any</b> events.
     * <p>
     * Event though this criteria will not filter any tags, you can limit the types of events to be matched by using the
     * {@link EventTypeRestrictableEventCriteria#andBeingOneOfTypes(Set)} method.
     *
     * @return An {@code EventCriteria} that contains no criteria at all.
     */
    public EventTypeRestrictableEventCriteria havingAnyTag() {
        TagFilteredEventCriteria filteredEventCriteria = new TagFilteredEventCriteria(Set.of());
        return wrap(filteredEventCriteria);
    }

    private EventTypeRestrictableEventCriteria wrap(EventTypeRestrictableEventCriteria criteria) {
        return new EventTypeRestrictableOrEventCriteria(criteria, orCriteria);
    }
}
