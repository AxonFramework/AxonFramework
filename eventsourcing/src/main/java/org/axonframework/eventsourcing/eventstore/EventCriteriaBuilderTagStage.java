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

import java.util.HashSet;
import java.util.Set;

/**
 * Interface declaring the possible builder actions for the tag stage of the {@link EventCriteriaBuilder}. After calling
 * any of the methods defined in this interface, the builder will complete and return an {@link EventCriteria}.
 *
 * @author Mitchell Herrijgers
 * @see EventCriteria
 * @see EventCriteriaBuilder
 * @since 5.0.0
 */
public interface EventCriteriaBuilderTagStage {

    /**
     * Define that the event must contain all the provided {@code tags} to match. These tags function in an AND
     * relation, meaning that an event must have all tags to match. A partial match is not sufficient.
     *
     * @param tags The tags to match against.
     * @return The completed {@link EventCriteria} instance.
     */
    EventCriteria withTags(@Nonnull Set<Tag> tags);

    /**
     * Define that the event must contain all the provided {@code tags} to match. These tags function in an AND
     * relation, meaning that an event must have all tags to match. A partial match is not sufficient.
     *
     * @param tags The tags to match against.
     * @return The completed {@link EventCriteria} instance.
     */
    default EventCriteria withTags(@Nonnull Tag... tags) {
        return withTags(Set.of(tags));
    }

    /**
     * Define, as key-value pairs, that the event must contain all the provided {@code tags} to match. These tags
     * function in an AND relation, meaning that an event must have all tags to match. A partial match is not
     * sufficient.
     *
     * @param tags The tags to match against.
     * @return The completed {@link EventCriteria} instance.
     */
    default EventCriteria withTags(@Nonnull String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be in pairs of key and value");
        }
        var tagSet = new HashSet<Tag>();
        for (int i = 0; i < tags.length; i += 2) {
            tagSet.add(new Tag(tags[i], tags[i + 1]));
        }

        return withTags(tagSet);
    }

    /**
     * Finalizes the builder and returns an {@link EventCriteria} instance that matches only based on the types that
     * were already defined in the previous stage.
     *
     * @return The completed {@link EventCriteria} instance.
     */
    EventCriteria withAnyTags();
}
