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

final class EventCriteriaBuilder implements EventCriteria.Builder {

    private final String[] types;

    static final EventCriteriaBuilder NO_TYPES = new EventCriteriaBuilder();

    EventCriteriaBuilder(String... types) {
        this.types = types;
    }

    @Override
    public EventCriteria withTags(@Nonnull Set<Tag> tags) {
        if (tags.isEmpty() && types.length == 0) {
            return AnyEvent.INSTANCE;
        }
        return new DefaultEventCriteria(Set.of(types), tags);
    }

    @Override
    public EventCriteria withTags(@Nonnull Tag... tags) {
        return withTags(Set.of(tags));
    }

    @Override
    public EventCriteria withTags(@Nonnull String... tagKeyValuePairs) {
        if (tagKeyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("tagKeyValuePairs must have even length");
        }
        Set<Tag> tags = new HashSet<>();
        for (int i = 0; i < tagKeyValuePairs.length; i = i + 2) {
            tags.add(new Tag(tagKeyValuePairs[i], tagKeyValuePairs[i + 1]));
        }
        return withTags(tags);
    }

    @Override
    public EventCriteria withAnyTags() {
        if (types.length == 0) {
            return AnyEvent.INSTANCE;
        }
        return new DefaultEventCriteria(Set.of(types), Set.of());
    }
}
