/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;

import java.util.List;
import java.util.function.Predicate;

/**
 * Utility class providing common Predicates used to detect conflicts between the actual state of an event sourced
 * aggregate and the expected state of the aggregate.
 *
 * @author Rene de Waele
 */
public class Conflicts {

    /**
     * Returns a {@link Predicate} for a {@link ConflictResolver} that responds affirmative if any event in a list of
     * unseen events matches the given {@code messageFilter}. If the returned predicate matches an unseen event the
     * ConflictResolver will throw an exception.
     *
     * @param messageFilter predicate for a single event
     * @return a Predicate to detect conflicts in unseen aggregate events
     */
    public static <T extends EventMessage<?>> Predicate<List<T>> eventMatching(
            Predicate<? super T> messageFilter) {
        return events -> events.stream().anyMatch(messageFilter::test);
    }

    /**
     * Returns a {@link Predicate} for a {@link ConflictResolver} that responds affirmative if the payload of any event
     * in a list of unseen events matches the given {@code messageFilter}. If the returned predicate matches an unseen
     * event the ConflictResolver will throw an exception.
     *
     * @param payloadFilter predicate for the payload of a single event
     * @return a Predicate to detect conflicts in unseen aggregate events
     */
    public static Predicate<List<DomainEventMessage<?>>> payloadMatching(Predicate<Object> payloadFilter) {
        return events -> events.stream().map(Message::getPayload).anyMatch(payloadFilter::test);
    }

    /**
     * Returns a {@link Predicate} for a {@link ConflictResolver} that responds affirmative if the payload of any event
     * in a list of unseen events is of given {@code payloadType} and matches the given {@code messageFilter}. If the
     * returned predicate matches an unseen event the ConflictResolver will throw an exception.
     *
     * @param payloadType   the type of event payload to filter for
     * @param payloadFilter predicate for the payload of a single event
     * @return a Predicate to detect conflicts in unseen aggregate events
     */
    @SuppressWarnings("unchecked")
    public static <T> Predicate<List<DomainEventMessage<?>>> payloadMatching(Class<T> payloadType,
                                                                       Predicate<? super T>
                                                                                               payloadFilter) {
        return events -> events.stream().filter(event -> payloadType.isAssignableFrom(event.getPayloadType()))
                .map(event -> (T) event.getPayload()).anyMatch(payloadFilter::test);
    }

    /**
     * Returns a {@link Predicate} for a {@link ConflictResolver} that responds affirmative if the payload type of any
     * event in a list of unseen events is assignable to given {@code payloadType}. If the returned predicate matches an
     * unseen event the ConflictResolver will throw an exception.
     *
     * @param payloadType the type of event payload to filter for
     * @return a Predicate to detect conflicts in unseen aggregate events
     */
    public static <T> Predicate<List<DomainEventMessage<?>>> payloadTypeOf(Class<T> payloadType) {
        return eventMatching(event -> payloadType.isAssignableFrom(event.getPayloadType()));
    }

    private Conflicts() {
    }
}
