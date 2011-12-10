/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.saga;

import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Interface describing methods that can be executed after the first "given" state has been supplied. Either more
 * "given" state can be appended, or a transition to the definition of "when" can be made.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public interface ContinuedGivenState {

    /**
     * Use this method to indicate that an aggregate with given identifier published certain events.
     * <p/>
     * Can be chained to build natural sentences:<br/> <code>andThenAggregate(someIdentifier).published(someEvents)</code>
     *
     * @param aggregateIdentifier The identifier of the aggregate the events should appear to come from
     * @return an object that allows registration of the actual events to send
     */
    GivenAggregateEventPublisher andThenAggregate(Object aggregateIdentifier);

    /**
     * Indicates that the given <code>event</code> has been published in the past. This event is sent to the associated
     * sagas.
     *
     * @param event The event to publish
     * @return an object that allows chaining of more given state
     */
    ContinuedGivenState andThenAPublished(Object event);

    /**
     * Use this method to indicate that an aggregate with given identifier should publish certain events, <em>while
     * recording the outcome</em>. In contrast to the {@link FixtureConfiguration#givenAggregate(Object)} given} and
     * {@link #andThenAggregate(Object)}  andThen} methods, this method will start recording activity on the EventBus
     * and CommandBus.
     * <p/>
     * Can be chained to build natural sentences:<br/> <code>whenAggregate(someIdentifier).publishes(anEvent)</code>
     * <p/>
     * Note that if you inject resources using {@link FixtureConfiguration#registerResource(Object)}, you may need to
     * reset them yourself if they are manipulated by the Saga in the "given" stage of the test.
     *
     * @param aggregateIdentifier The identifier of the aggregate the events should appear to come from
     * @return an object that allows registration of the actual events to send
     */
    WhenAggregateEventPublisher whenAggregate(Object aggregateIdentifier);

    /**
     * Use this method to indicate an application is published, <em>while recording the outcome</em>.
     * <p/>
     * Note that if you inject resources using {@link FixtureConfiguration#registerResource(Object)}, you may need to
     * reset them yourself if they are manipulated by the Saga in the "given" stage of the test.
     *
     * @param event the event to publish
     * @return an object allowing you to verify the test results
     */
    FixtureExecutionResult whenPublishingA(Object event);

    /**
     * Mimic an elapsed time with no relevant activity for the Saga. If any Events are scheduled to be published within
     * this time frame, they are published. All activity by the Saga on the CommandBus and EventBus (meaning that
     * scheduled events are excluded) is recorded.
     * <p/>
     * Note that if you inject resources using {@link FixtureConfiguration#registerResource(Object)}, you may need to
     * reset them yourself if they are manipulated by the Saga in the "given" stage of the test.
     *
     * @param elapsedTime The amount of time to elapse
     * @return an object allowing you to verify the test results
     */
    FixtureExecutionResult whenTimeElapses(Duration elapsedTime);

    /**
     * Mimic an elapsed time with no relevant activity for the Saga. If any Events are scheduled to be published within
     * this time frame, they are published. All activity by the Saga on the CommandBus and EventBus (meaning that
     * scheduled events are excluded) is recorded.
     * <p/>
     * Note that if you inject resources using {@link FixtureConfiguration#registerResource(Object)}, you may need to
     * reset them yourself if they are manipulated by the Saga in the "given" stage of the test.
     *
     * @param newDateTime The time to advance the clock to
     * @return an object allowing you to verify the test results
     */
    FixtureExecutionResult whenTimeAdvancesTo(DateTime newDateTime);
}
