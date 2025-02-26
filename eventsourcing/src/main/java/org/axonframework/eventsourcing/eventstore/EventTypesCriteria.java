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
import java.util.stream.Collectors;

/**
 * Criteria that matches when the event is one of the given types.
 *
 * @param types The types to match against.
 */
public record EventTypesCriteria(Set<String> types) implements EventCriteria {

    @Override
    public boolean matchingType(@Nonnull String type) {
        return types.contains(type);
    }

    @Override
    public String toString() {
        return "IS (" + String.join(",", types) + ")";
    }
}
