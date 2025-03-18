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

public interface EventCriteriaBuilderEventTypeStage {

    /**
     * Adds the given {@code types} to the types that this criteria instance matches with.
     *
     * @param types The types to match against.
     * @return The current Builder instance, for fluent interfacing.
     */
    EventCriteriaBuilderTagStage eventTypes(@Nonnull String... types);

    /**
     * Marks this criteria instance to match against any event type.
     *
     * @return The current Builder instance's {@link TagBuilder}, for fluent interfacing.
     */
    EventCriteriaBuilderTagStage anyEventType();
}
