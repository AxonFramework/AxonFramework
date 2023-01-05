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

package org.axonframework.test.aggregate;

import org.axonframework.messaging.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Interface describing the operations available on a test fixture in the execution stage. In this stage, there is only
 * on operation: {@link #when(Object)}, which dispatches a command on this fixture's Command Bus.
 *
 * @param <T> The type of Aggregate under test
 * @author Allard Buijze
 * @since 0.6
 */
public interface TestExecutor<T> {

    /**
     * Dispatches the given command to the appropriate command handler and records all activity in the fixture for
     * result validation. If the given {@code command} is a {@link
     * org.axonframework.commandhandling.CommandMessage} instance, it will be dispatched as-is. Any other object will
     * cause the given {@code command} to be wrapped in a {@code CommandMessage} as its payload.
     *
     * @param command The command to execute
     * @return a ResultValidator that can be used to validate the resulting actions of the command execution
     */
    ResultValidator<T> when(Object command);

    /**
     * Dispatches the given command and meta data to the appropriate command handler and records all
     * activity in the fixture for result validation. If the given {@code command} is a {@link
     * org.axonframework.commandhandling.CommandMessage} instance, it will be dispatched as-is, with given
     * additional {@code metaData}. Any other object will cause the given {@code command} to be wrapped in a
     * {@code CommandMessage} as its payload.
     *
     * @param command  The command to execute
     * @param metaData The meta data to attach to the
     * @return a ResultValidator that can be used to validate the resulting actions of the command execution
     */
    ResultValidator<T> when(Object command, Map<String, ?> metaData);

    /**
     * Configures the given {@code domainEvents} as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     * <p/>
     * If an item in the given {@code domainEvents} implements {@link Message}, the
     * payload and meta data from that message are copied into a newly created Domain Event Message. Otherwise, a
     * Domain Event Message with the item as payload and empty meta data is created.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor<T> andGiven(Object... domainEvents);

    /**
     * Configures the given {@code domainEvents} as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     * <p/>
     * If an item in the list implements {@link Message}, the payload and meta data from that
     * message are copied into a newly created Domain Event Message. Otherwise, a Domain Event Message with the item
     * as payload and empty meta data is created.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor<T> andGiven(List<?> domainEvents);

    /**
     * Configures the given {@code commands} as the command that will provide the "given" events. The commands are
     * executed, and the resulting stored events are captured.
     *
     * @param commands the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor<T> andGivenCommands(Object... commands);

    /**
     * Configures the given {@code commands} as the command that will provide the "given" events. The commands are
     * executed, and the resulting stored events are captured.
     *
     * @param commands the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor<T> andGivenCommands(List<?> commands);

    /**
     * Use this method to indicate a specific moment as the initial current time "known" by the fixture at the start
     * of the given state.
     *
     * @param currentTime The simulated "current time" at which the given state is initialized
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor<T> andGivenCurrentTime(Instant currentTime);

    /**
     * Returns the time as "known" by the fixture. This is the time at which the fixture was created, plus the amount of
     * time the fixture was told to simulate a "wait".
     *
     * @return the simulated "current time" of the fixture.
     */
    Instant currentTime();

    /**
     * Simulates the time elapsing in the current given state using a {@link Duration} as the unit of time. This can be
     * useful when the time between given events is of importance, for example when leveraging the
     * {@link org.axonframework.deadline.DeadlineManager} to schedule deadlines in the context of a given Aggregate.
     *
     * @param elapsedTime a {@link Duration} specifying the amount of time that will elapse
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of the command execution
     *
     * @deprecated in favor of {@link #whenTimeElapses(Duration)}. This function incorrectly suggests you can
     * proceed with other operations after calling it, which is made impossible due to the {@link ResultValidator}
     * return type
     */
    @Deprecated
    default ResultValidator andThenTimeElapses(Duration elapsedTime) {
        return whenTimeElapses(elapsedTime);
    }

    /**
     * Simulates the time elapsing in the current given state using a {@link Duration} as the unit of time. This can be
     * useful when the time between given events is of importance, for example when leveraging the
     * {@link org.axonframework.deadline.DeadlineManager} to schedule deadlines in the context of a given Aggregate.
     *
     * @param elapsedTime a {@link Duration} specifying the amount of time that will elapse
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of the command execution
     * @deprecated since 4.6. Use {@link #whenTimeAdvancesTo(Instant)} method
     */
    @Deprecated
    ResultValidator<T> whenThenTimeElapses(Duration elapsedTime);

    /**
     * Simulates the time elapsing in the current given state using a {@link Duration} as the unit of time. This can be
     * useful when the time between given events is of importance, for example when leveraging the
     * {@link org.axonframework.deadline.DeadlineManager} to schedule deadlines in the context of a given Aggregate.
     *
     * Note: As this method is added to the interface as a replacement for the deprecated
     * {@link #whenThenTimeAdvancesTo(Instant)} method, and in case there are other implementations by 3rd party
     * libraries, this method is changed to a default method that rely on the deprecated method so that there is no
     * breaking changes in the API in case an external implementation of this interface. Nevertheless, the recommended
     * approach is to override this implementation.
     *
     * @param elapsedTime a {@link Duration} specifying the amount of time that will elapse
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of the command execution
     */
    default ResultValidator<T> whenTimeElapses(Duration elapsedTime) {
        return whenThenTimeElapses(elapsedTime);
    }

    /**
     * Simulates the time advancing in the current given state using an {@link Instant} as the unit of time. This can be
     * useful when the time between given events is of importance, for example when leveraging the
     * {@link org.axonframework.deadline.DeadlineManager} to schedule deadlines in the context of a given Aggregate.
     *
     * @param newPointInTime an {@link Instant} specifying the amount of time to advance the clock to
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of the command execution
     *
     * @deprecated in favor of {@link #whenTimeAdvancesTo(Instant)}. This function incorrectly suggests you can
     * proceed with other operations after calling it, which is made impossible due to the {@link ResultValidator}
     * return type
     */
    @Deprecated
    default ResultValidator andThenTimeAdvancesTo(Instant newPointInTime) {
        return whenTimeAdvancesTo(newPointInTime);
    }

    /**
     * Simulates the time advancing in the current given state using an {@link Instant} as the unit of time. This can be
     * useful when the time between given events is of importance, for example when leveraging the
     * {@link org.axonframework.deadline.DeadlineManager} to schedule deadlines in the context of a given Aggregate.
     *
     * @param newPointInTime an {@link Instant} specifying the amount of time to advance the clock to
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of the command execution
     * @deprecated since 4.6. Use {@link #whenTimeAdvancesTo(Instant)} method
     */
    @Deprecated
    ResultValidator<T> whenThenTimeAdvancesTo(Instant newPointInTime);

    /**
     * Simulates the time advancing in the current given state using an {@link Instant} as the unit of time. This can be
     * useful when the time between given events is of importance, for example when leveraging the
     * {@link org.axonframework.deadline.DeadlineManager} to schedule deadlines in the context of a given Aggregate.
     *
     * Note: As this method is added to the interface as a replacement for the deprecated
     * {@link #whenThenTimeAdvancesTo(Instant)} method, and in case there are other implementations by 3rd party
     * libraries, this method is changed to a default method that rely on the deprecated method so that there is no
     * breaking changes in the API in case an external implementation of this interface. Nevertheless, the recommended
     * approach is to override this implementation.
     *
     * @param newPointInTime an {@link Instant} specifying the amount of time to advance the clock to
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of the command execution
     */
    default ResultValidator<T> whenTimeAdvancesTo(Instant newPointInTime) {
        return whenThenTimeAdvancesTo(newPointInTime);
    }

    /**
     * Invokes the given {@code aggregateFactory} expecting an aggregate instance of type {@code T} to be returned.
     * <p>
     * All activity is recorded in the fixture for result validation. The {@code aggregateFactory} typically refers to
     * one of the aggregate's constructors.
     * <p>
     * You should use this when-phase operation whenever you do not use the
     * {@link org.axonframework.commandhandling.CommandHandler} annotation on the aggregate's methods, nor have
     * {@link org.axonframework.test.aggregate.FixtureConfiguration#registerAnnotatedCommandHandler(Object) registered
     * an external command handler} invoking the {@link org.axonframework.modelling.command.Repository}.
     *
     * @param aggregateFactory A callable operation expecting an aggregate instance of type {@code T} to be returned.
     *                         This typically is an aggregate constructor invocation.
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of executing the given
     * {@code aggregateFactory}.
     */
    ResultValidator<T> whenConstructing(Callable<T> aggregateFactory);

    /**
     * Invokes the given {@code aggregateConsumer} after loading an aggregate of type {@code T} based on the given
     * {@code aggregateIdentifier}.
     * <p>
     * All activity is recorded in the fixture for result validation.
     * <p>
     * You should use this when-phase operation whenever you do not use the
     * {@link org.axonframework.commandhandling.CommandHandler} annotation on the aggregate's methods, nor have
     * {@link org.axonframework.test.aggregate.FixtureConfiguration#registerAnnotatedCommandHandler(Object) registered
     * an external command handler} invoking the {@link org.axonframework.modelling.command.Repository}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to
     *                            {@link org.axonframework.modelling.command.Repository#load(String)}.
     * @param aggregateConsumer   A lambda providing an aggregate instance of type {@code T} based on the given
     *                            {@code aggregateIdentifier}.
     * @return a {@link ResultValidator} that can be used to validate the resulting actions of executing the given
     * {@code aggregateConsumer}.
     */
    ResultValidator<T> whenInvoking(String aggregateIdentifier, Consumer<T> aggregateConsumer);
}
