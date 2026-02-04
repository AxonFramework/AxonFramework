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
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;

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
     * Define that the event must be one of the provided {@code types}. If the {@code types} set is empty, the criteria
     * allows any type.
     *
     * @param types The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    EventCriteria andBeingOneOfTypes(@Nonnull Set<QualifiedName> types);

    /**
     * Define that the event must be one of the provided {@code types}. If the {@code types} set is empty, the criteria
     * allows any type.
     *
     * @param types The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOneOfTypes(@Nonnull QualifiedName... types) {
        return andBeingOneOfTypes(Set.of(types));
    }

    /**
     * Define that the event must be one of the provided {@code types}. The {@code types} are resolved to a
     * {@link QualifiedName} using the provided {@code typeResolver}. If the {@code types} set is empty, the criteria
     * allows any type.
     *
     * @param typeResolver The {@link MessageTypeResolver} to resolve the types to a {@link QualifiedName}.
     * @param types        The types to match against.
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOneOfTypes(@Nonnull MessageTypeResolver typeResolver, @Nonnull Class<?>... types) {
        return andBeingOneOfTypes(Arrays.stream(types)
                                        .map(typeResolver::resolveOrThrow)
                                        .map(MessageType::qualifiedName)
                                        .collect(Collectors.toSet()));
    }

    /**
     * Define that the event must be one of the provided {@code types}. {@link QualifiedName qualified names} are
     * constructed from the string values. If the {@code types} set is empty, the criteria allow any type.
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
     * Specifies that the event can be of any type.
     *
     * @return The finished {@link EventCriteria} instance.
     */
    default EventCriteria andBeingOfAnyType() {
        return this;
    }
}
