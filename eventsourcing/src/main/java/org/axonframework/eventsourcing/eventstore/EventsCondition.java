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

import java.util.Set;

/**
 * Interface describing a condition that the type and tags of event messages must match against in order to be
 * relevant.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public sealed interface EventsCondition permits SourcingCondition, StreamingCondition, AppendCondition {

    /**
     * The set of criteria against which the Sourced events must match.
     *
     * @return The {@link EventCriteria} used to retrieve an event sequence complying to its criteria.
     */
    Set<EventCriteria> criteria();

    /**
     * Indicates whether the criteria defined in condition matches against the given {@code type} and {@code tags}.
     * <p>
     * More specifically, this condition matches if any of the provided criteria match the given {@code type} and
     * {@code tags}, or if no criteria have been provided at all.
     * <p>
     * See {@link EventCriteria#matchingTags(Set)} for more details on matching tags.
     *
     * @param type The type of the event to validate against
     * @param tags The tags of an event message to match
     * @return {@code true} if given type and tags match, otherwise {@code false}
     * @see EventCriteria#matchingTags(Set)
     */
    default boolean matches(String type, Set<Tag> tags) {
        return criteria().isEmpty()
                || criteria().stream().anyMatch(criteria -> criteria.matches(type, tags));
    }
}
