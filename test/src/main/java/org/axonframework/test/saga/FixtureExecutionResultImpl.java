/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.deadline.DeadlineManagerValidator;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Matcher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.axonframework.test.matchers.Matchers.equalTo;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;
import static org.hamcrest.CoreMatchers.any;

/**
 * Default implementation of FixtureExecutionResult.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class FixtureExecutionResultImpl<T> implements FixtureExecutionResult {

    private final RepositoryContentValidator<T> repositoryContentValidator;
    private final EventValidator eventValidator;
    private final EventSchedulerValidator eventSchedulerValidator;
    private final DeadlineManagerValidator deadlineManagerValidator;
    private final CommandValidator commandValidator;
    private final FieldFilter fieldFilter;

    /**
     * Initializes an instance and make it monitor the given infrastructure classes.
     *
     * @param sagaStore       The SagaStore to monitor
     * @param eventScheduler  The scheduler to monitor
     * @param deadlineManager The deadline manager to monitor
     * @param eventBus        The event bus to monitor
     * @param commandBus      The command bus to monitor
     * @param sagaType        The type of Saga under test
     * @param fieldFilter     The FieldFilter describing the fields to include in equality checks
     */
    FixtureExecutionResultImpl(InMemorySagaStore sagaStore, StubEventScheduler eventScheduler,
                               StubDeadlineManager deadlineManager, EventBus eventBus, RecordingCommandBus commandBus,
                               Class<T> sagaType, FieldFilter fieldFilter) {
        this.fieldFilter = fieldFilter;
        commandValidator = new CommandValidator(commandBus, fieldFilter);
        repositoryContentValidator = new RepositoryContentValidator<>(sagaType, sagaStore);
        eventValidator = new EventValidator(eventBus, fieldFilter);
        eventSchedulerValidator = new EventSchedulerValidator(eventScheduler);
        deadlineManagerValidator = new DeadlineManagerValidator(deadlineManager, fieldFilter);
    }

    /**
     * Tells this class to start monitoring activity in infrastructure classes.
     */
    public void startRecording() {
        eventValidator.startRecording();
        commandValidator.startRecording();
    }

    @Override
    public FixtureExecutionResult expectActiveSagas(int expected) {
        repositoryContentValidator.assertActiveSagas(expected);
        return this;
    }

    @Override
    public FixtureExecutionResult expectAssociationWith(String associationKey, Object associationValue) {
        repositoryContentValidator.assertAssociationPresent(associationKey, associationValue.toString());
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoAssociationWith(String associationKey, Object associationValue) {
        repositoryContentValidator.assertNoAssociationPresent(associationKey, associationValue.toString());
        return this;
    }


    @Override
    public FixtureExecutionResult expectScheduledEventMatching(Duration duration, Matcher<? super EventMessage<?>> matcher) {
        eventSchedulerValidator.assertScheduledEventMatching(duration, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledDeadlineMatching(Duration duration,
                                                                  Matcher<? super DeadlineMessage<?>> matcher) {
        deadlineManagerValidator.assertScheduledDeadlineMatching(duration, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, Object applicationEvent) {
        return expectScheduledEventMatching(duration, messageWithPayload(equalTo(applicationEvent, fieldFilter)));
    }

    @Override
    public FixtureExecutionResult expectScheduledDeadline(Duration duration, Object deadline) {
        return expectScheduledDeadlineMatching(duration, messageWithPayload(equalTo(deadline, fieldFilter)));
    }

    @Override
    public FixtureExecutionResult expectScheduledEventOfType(Duration duration, Class<?> eventType) {
        return expectScheduledEventMatching(duration, messageWithPayload(any(eventType)));
    }

    @Override
    public FixtureExecutionResult expectScheduledDeadlineOfType(Duration duration, Class<?> deadlineType) {
        return expectScheduledDeadlineMatching(duration, messageWithPayload(any(deadlineType)));
    }

    @Override
    public FixtureExecutionResult expectScheduledEventMatching(Instant scheduledTime, Matcher<? super EventMessage<?>> matcher) {
        eventSchedulerValidator.assertScheduledEventMatching(scheduledTime, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledDeadlineMatching(Instant scheduledTime,
                                                                  Matcher<? super DeadlineMessage<?>> matcher) {
        deadlineManagerValidator.assertScheduledDeadlineMatching(scheduledTime, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Instant scheduledTime, Object applicationEvent) {
        return expectScheduledEventMatching(scheduledTime, messageWithPayload(equalTo(applicationEvent, fieldFilter)));
    }

    @Override
    public FixtureExecutionResult expectScheduledDeadline(Instant scheduledTime, Object deadline) {
        return expectScheduledDeadlineMatching(scheduledTime, messageWithPayload(equalTo(deadline, fieldFilter)));
    }

    @Override
    public FixtureExecutionResult expectScheduledEventOfType(Instant scheduledTime, Class<?> eventType) {
        return expectScheduledEventMatching(scheduledTime, messageWithPayload(any(eventType)));
    }

    @Override
    public FixtureExecutionResult expectScheduledDeadlineOfType(Instant scheduledTime, Class<?> deadlineType) {
        return expectScheduledDeadlineMatching(scheduledTime, messageWithPayload(any(deadlineType)));
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommands(Object... expected) {
        commandValidator.assertDispatchedEqualTo(expected);
        return this;
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommandsMatching(Matcher<? extends List<? super CommandMessage<?>>> matcher) {
        commandValidator.assertDispatchedMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoDispatchedCommands() {
        commandValidator.assertDispatchedMatching(Matchers.noCommands());
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoScheduledEvents() {
        eventSchedulerValidator.assertNoScheduledEvents();
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoScheduledDeadlines() {
        deadlineManagerValidator.assertNoScheduledDeadlines();
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEventsMatching(Matcher<? extends List<? super EventMessage<?>>> matcher) {
        eventValidator.assertPublishedEventsMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectDeadlinesMetMatching(
            Matcher<? extends List<? super DeadlineMessage<?>>> matcher) {
        deadlineManagerValidator.assertDeadlinesMetMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Object... expected) {
        eventValidator.assertPublishedEvents(expected);
        return this;
    }

    @Override
    public FixtureExecutionResult expectDeadlinesMet(Object... expected) {
        deadlineManagerValidator.assertDeadlinesMet(expected);
        return this;
    }
}
