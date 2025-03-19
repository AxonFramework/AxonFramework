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

/**
 * Interface declaring the possible builder actions for the event type stage of the {@link EventCriteriaBuilder}. After
 * calling any of the methods defined in this interface, the builder will be in the
 * {@link EventCriteriaBuilderTagStage}.
 *
 * @author Mitchell Herrijgers
 * @see EventCriteria
 * @see EventCriteriaBuilder
 * @since 5.0.0
 */
public sealed interface EventCriteriaBuilderEventTypeStage permits EventCriteriaBuilder {

    /**
     * Define that the event must have one of the provided {@code types} to match. If the {@code types} set is empty,
     * the criteria will match against any type. The types match in an OR relation, meaning that an event must have at
     * least one of the types to match.
     *
     * @param types The types to match against.
     * @return The current builder as an {@link EventCriteriaBuilderTagStage}, for fluent interfacing.
     */
    EventCriteriaBuilderTagStage eventTypes(@Nonnull String... types);

    /**
     * Define that the event matches with any type.
     *
     * @return The current builder as an {@link EventCriteriaBuilderTagStage}, for fluent interfacing.
     */
    EventCriteriaBuilderTagStage anyEventType();
}
