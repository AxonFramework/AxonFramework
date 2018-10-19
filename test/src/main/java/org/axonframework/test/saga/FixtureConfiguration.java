/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.aggregate.ResultValidator;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.utils.CallbackBehavior;

import java.time.Instant;


/**
 * Interface describing action to perform on a Saga Test Fixture during the configuration phase.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public interface FixtureConfiguration {

    /**
     * Disables the check that injected resources are stored in fields that are marked 'transient'.
     *
     * By default, Saga fixtures check for the transient modifier on fields that hold injected resources. These
     * resources are generally not means to be serialized as part of the Saga.
     *
     * When the transience check reports false positives, this method allows this check to be skipped.
     * @return this instance for fluent interfacing.
     */
    FixtureConfiguration withTransienceCheckDisabled();

    /**
     * Registers the given {@code resource}. When a Saga is created, all resources are injected on that instance
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
     * Creates a Command Gateway for the given {@code gatewayInterface} and registers that as a resource. The
     * gateway will dispatch commands on the Command Bus contained in this Fixture, so that you can validate commands
     * using {@link FixtureExecutionResult#expectDispatchedCommands(Object...)} and {@link
     * FixtureExecutionResult#expectDispatchedCommandsMatching(org.hamcrest.Matcher)}.
     * <p/>
     * Note that you need to use {@link #setCallbackBehavior(org.axonframework.test.utils.CallbackBehavior)} to defined
     * the behavior of commands when expecting return values. Alternatively, you can use {@link
     * #registerCommandGateway(Class, Object)} to define behavior using a stub implementation.
     *
     * @param gatewayInterface The interface describing the gateway
     * @param <T>              The gateway type
     * @return the gateway implementation being registered as a resource.
     */
    <T> T registerCommandGateway(Class<T> gatewayInterface);

    /**
     * Creates a Command Gateway for the given {@code gatewayInterface} and registers that as a resource. The
     * gateway will dispatch commands on the Command Bus contained in this Fixture, so that you can validate commands
     * using {@link FixtureExecutionResult#expectDispatchedCommands(Object...)} and {@link
     * FixtureExecutionResult#expectDispatchedCommandsMatching(org.hamcrest.Matcher)}.
     * <p/>
     * The behavior of the created gateway is defined by the given {@code stubImplementation}, if not null.
     * Dispatched Commands are still recorded for verification. Note that only commands executed in the "when" phase
     * are recorded, while the stub implementation may record activity during the "given" phase as well.
     *
     * @param gatewayInterface   The interface describing the gateway
     * @param stubImplementation The stub or mock implementation defining behavior of the gateway
     * @param <T>                The gateway type
     * @return the gateway implementation being registered as a resource.
     */
    <T> T registerCommandGateway(Class<T> gatewayInterface, T stubImplementation);

    /**
     * Registers the given {@code fieldFilter}, which is used to define which Fields are used when comparing
     * objects. The {@link ResultValidator#expectEvents(Object...)} and {@link ResultValidator#expectReturnValue(Object)},
     * for example, use this filter.
     * <p/>
     * When multiple filters are registered, a Field must be accepted by all registered filters in order to be
     * accepted.
     * <p/>
     * By default, all Fields are included in the comparison.
     *
     * @param fieldFilter The FieldFilter that defines which fields to include in the comparison
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerFieldFilter(FieldFilter fieldFilter);

    /**
     * Indicates that a field with given {@code fieldName}, which is declared in given {@code declaringClass}
     * is ignored when performing deep equality checks.
     *
     * @param declaringClass The class declaring the field
     * @param fieldName The name of the field
     * @return the current FixtureConfiguration, for fluent interfacing
     * @throws FixtureExecutionException when no such field is declared
     */
    FixtureConfiguration registerIgnoredField(Class<?> declaringClass, String fieldName);

    /**
     * Registers handler definition within this fixture. This {@code handlerDefinition} will replace existing one within
     * this fixture.
     *
     * @param handlerDefinition used to create concrete handlers
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerHandlerDefinition(HandlerDefinition handlerDefinition);

    /**
     * Registers a deadline dispatch interceptor which will always be invoked before a deadline is dispatched
     * (scheduled) on the {@link org.axonframework.deadline.DeadlineManager} to perform a task specified in the
     * interceptor.
     *
     * @param deadlineDispatchInterceptor the interceptor for dispatching (scheduling) deadlines
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerDeadlineDispatchInterceptor(
            MessageDispatchInterceptor<DeadlineMessage<?>> deadlineDispatchInterceptor);

    /**
     * Registers a deadline handler interceptor which will always be invoked before a deadline is handled to perform a
     * task specified in the interceptor.
     *
     * @param deadlineHandlerInterceptor the interceptor for handling deadlines
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerDeadlineHandlerInterceptor(
            MessageHandlerInterceptor<DeadlineMessage<?>> deadlineHandlerInterceptor);

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
     * {@code andThenAggregate(someIdentifier).published(someEvents)}
     *
     * @param aggregateIdentifier The identifier of the aggregate the events should appear to come from
     * @return an object that allows registration of the actual events to send
     */
    GivenAggregateEventPublisher givenAggregate(String aggregateIdentifier);

    /**
     * Use this method to indicate a specific moment as the initial current time "known" by the fixture at the start
     * of the given state.
     *
     * @param currentTime The simulated "current time" at which the given state is initialized
     * @return an object that allows chaining of more given state
     */
    ContinuedGivenState givenCurrentTime(Instant currentTime);

    /**
     * Indicates that the given {@code applicationEvent} has been published in the past. This event is sent to the
     * associated sagas.
     *
     * @param event The event to publish
     * @return an object that allows chaining of more given state
     */
    ContinuedGivenState givenAPublished(Object event);

    /**
     * Indicates that no relevant activity has occurred in the past.
     *
     * @return an object that allows the definition of the activity to measure Saga behavior
     *
     * @since 2.1.1
     */
    WhenState givenNoPriorActivity();

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
    Instant currentTime();
}
