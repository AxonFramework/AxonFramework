/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.saga;

import java.util.Map;

/**
 * Interface to an object that publishes events on behalf of an aggregate, recording what the Saga does in reaction.
 * <p>
 * Axon Framework 5 has no {@code DomainEventMessage}, so the events are published as ordinary
 * {@link org.axonframework.messaging.eventhandling.EventMessage EventMessages}: they carry neither an aggregate
 * identifier nor a sequence number. A Saga reading either from the message it handles has no equivalent here.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public interface WhenAggregateEventPublisher {

    /**
     * Register the given {@code event} to be published on behalf of an aggregate. Activity caused by this event on the
     * command bus and event sink is monitored and can be checked in the {@link FixtureExecutionResult}.
     *
     * @param event The event published by the aggregate
     * @return a reference to the test results for the validation phase
     */
    FixtureExecutionResult publishes(Object event);

    /**
     * Register the given {@code event} to be published on behalf of an aggregate, with given additional
     * {@code metadata}. Activity caused by this event on the command bus and event sink is monitored and can be checked
     * in the {@link FixtureExecutionResult}.
     *
     * @param event    The event published by the aggregate
     * @param metadata The metadata to attach to the event
     * @return a reference to the test results for the validation phase
     */
    FixtureExecutionResult publishes(Object event, Map<String, String> metadata);
}
