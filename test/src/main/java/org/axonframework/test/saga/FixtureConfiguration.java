/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.test.utils.CallbackBehavior;
import org.joda.time.DateTime;

/**
 * Interface describing action to perform on a Saga Test Fixture during the configuration phase.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public interface FixtureConfiguration {

    /**
     * Registers the given <code>resource</code>. When a Saga is created, all resources are injected on that instance
     * before any Events are passed onto it.
     * <p/>
     * Note that a CommandBus, EventBus and EventScheduler are already registered as resources, and need not be
     * registered again.
     * <p/>
     * Also note that you might need to reset the resources manually if you want to isolate behavior during the "when"
     * stage of the test.
     *
     * @param resource the resource to register.
     */
    void registerResource(Object resource);

    /**
     * Sets the instance that defines the behavior of the Command Bus when a command is dispatched with a callback.
     *
     * @param callbackBehavior The instance deciding to how the callback should be invoked.
     */
    void setCallbackBehavior(CallbackBehavior callbackBehavior);

    /**
     * Use this method to indicate that an aggregate with given identifier published certain events.
     * <p/>
     * Can be chained to build natural sentences:<br/>
     * <code>andThenAggregate(someIdentifier).published(someEvents)</code>
     *
     * @param aggregateIdentifier The identifier of the aggregate the events should appear to come from
     * @return an object that allows registration of the actual events to send
     */
    GivenAggregateEventPublisher givenAggregate(Object aggregateIdentifier);

    /**
     * Indicates that the given <code>applicationEvent</code> has been published in the past. This event is sent to the
     * associated sagas.
     *
     * @param event The event to publish
     * @return an object that allows chaining of more given state
     */
    ContinuedGivenState givenAPublished(Object event);

    /**
     * Returns the time as "known" by the fixture. This is the time at which the fixture was created, plus the amount
     * of
     * time the fixture was told to simulate a "wait".
     * <p/>
     * This time can be used to predict calculations that the saga may have made based on timestamps from the events it
     * received.
     *
     * @return the simulated "current time" of the fixture.
     */
    DateTime currentTime();
}
