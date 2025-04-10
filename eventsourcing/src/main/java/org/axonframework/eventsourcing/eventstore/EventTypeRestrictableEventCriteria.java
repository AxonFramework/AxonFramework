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
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An {@link EventCriteria} that can be restricted to a specific set of event types. See {@link EventCriteria} for a
 * deep explanation of how filtering works.
 *
 * @author Mitchell Herrijgers
 * @see EventCriteria
 * @since 5.0.0
 */
public sealed interface EventTypeRestrictableEventCriteria extends EventCriteria
        permits TagFilteredEventCriteria, EventTypeRestrictableOrEventCriteria, AnyEvent {


    /**
     * Define that the event must have one of the provided {@code types} to match. If the {@code types} set is empty,
     * the criteria will match against any type. The types match in an OR relation, meaning that an event must have at
     * least one of the types to match.
     *
     * @param types The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    EventCriteria andBeingOneOfTypes(@Nonnull Set<QualifiedName> types);

    /**
     * Define that the event must have one of the provided {@code types} to match. If the {@code types} set is empty,
     * the criteria will match against any type. The types match in an OR relation, meaning that an event must have at
     * least one of the types to match.
     *
     * @param types The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOneOfTypes(@Nonnull QualifiedName... types) {
        return andBeingOneOfTypes(Set.of(types));
    }

    /**
     * Define that the event must match on one of the provided {@code types} to match. The {@code types} are resolved to
     * a {@link QualifiedName} using the provided {@code typeResolver}. If the {@code types} set is empty, the criteria
     * will match against any type. The types match in an OR relation, meaning that an event must have at least one of
     * the types to match.
     *
     * @param typeResolver The {@link MessageTypeResolver} to resolve the types to a {@link QualifiedName}.
     * @param types        The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOneOfTypes(@Nonnull MessageTypeResolver typeResolver, @Nonnull Class<?>... types) {
        return andBeingOneOfTypes(Arrays.stream(types)
                                        .map(typeResolver::resolve)
                                        .map(MessageType::qualifiedName)
                                        .collect(Collectors.toSet()));
    }

    /**
     * Define that the event must match on one of the provided {@code types} to match.
     * {@link QualifiedName qualified names} are constructed from the string values. If the {@code types} set is empty,
     * the criteria will match against any type. The types match in an OR relation, meaning that an event must have at
     * least one of the types to match.
     *
     * @param types The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOneOfTypes(@Nonnull String... types) {
        return andBeingOneOfTypes(Arrays.stream(types)
                                        .map(QualifiedName::new)
                                        .collect(Collectors.toSet()));
    }

    /**
     * Specifies that the event can have any type to match.
     *
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOfAnyType() {
        return this;
    }
}
