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

import java.util.Set;

/**
 * Criteria that matches when one of the given tags is present in the event.
 *
 * @param tag The tag to match against.
 */
public record TagEventCriteria(Tag tag) implements EventCriteria {

    @Override
    public boolean matchingTags(@Nonnull Set<Tag> tags) {
        return tags.contains(tag);
    }

    @Override
    public String toString() {
        return tag.key() + "=" + tag.value();
    }
}
