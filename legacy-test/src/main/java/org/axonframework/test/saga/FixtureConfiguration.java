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
 * Interface describing action to perform on a {@link SagaTestFixture} during the configuration phase.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public interface FixtureConfiguration {

    /**
     * Use this method to indicate that an aggregate with given identifier published certain events.
     * <p>
     * Can be chained to build natural sentences: {@code givenAggregate(someIdentifier).published(someEvents)}
     *
     * @param aggregateIdentifier The identifier of the aggregate the events should appear to come from
     * @return an object that allows registration of the actual events to send
     */
    GivenAggregateEventPublisher givenAggregate(String aggregateIdentifier);

    /**
     * Indicates that the given {@code event} has been published in the past. This event is sent to the associated
     * Sagas.
     *
     * @param event The event to publish
     * @return an object that allows chaining of more given state
     */
    ContinuedGivenState givenAPublished(Object event);

    /**
     * Indicates that the given {@code event} with given {@code metadata} has been published in the past. This event is
     * sent to the associated Sagas.
     *
     * @param event    The event to publish
     * @param metadata The metadata to attach to the event
     * @return an object that allows chaining of more given state
     */
    ContinuedGivenState givenAPublished(Object event, Map<String, String> metadata);

    /**
     * Indicates that no relevant activity has occurred in the past.
     *
     * @return an object that allows the definition of the activity to measure Saga behavior
     */
    WhenState givenNoPriorActivity();
}
