/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Matcher;

import java.time.Duration;
import java.time.ZonedDateTime;

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
    private final CommandValidator commandValidator;
    private final FieldFilter fieldFilter;

    /**
     * Initializes an instance and make it monitor the given infrastructure classes.
     *
     * @param sagaStore      The SagaStore to monitor
     * @param eventScheduler The scheduler to monitor
     * @param eventBus       The event bus to monitor
     * @param commandBus     The command bus to monitor
     * @param sagaType       The type of Saga under test
     * @param fieldFilter    The FieldFilter describing the fields to include in equality checks
     */
    FixtureExecutionResultImpl(InMemorySagaStore sagaStore, StubEventScheduler eventScheduler,
                               EventBus eventBus, RecordingCommandBus commandBus,
                               Class<T> sagaType, FieldFilter fieldFilter) {
        this.fieldFilter = fieldFilter;
        commandValidator = new CommandValidator(commandBus, fieldFilter);
        repositoryContentValidator = new RepositoryContentValidator<>(sagaType, sagaStore);
        eventValidator = new EventValidator(eventBus, fieldFilter);
        eventSchedulerValidator = new EventSchedulerValidator(eventScheduler);
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
    public FixtureExecutionResult expectScheduledEventMatching(Duration duration, Matcher<?> matcher) {
        eventSchedulerValidator.assertScheduledEventMatching(duration, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, Object applicationEvent) {
        return expectScheduledEventMatching(duration, messageWithPayload(equalTo(applicationEvent, fieldFilter)));
    }

    @Override
    public FixtureExecutionResult expectScheduledEventOfType(Duration duration, Class<?> eventType) {
        return expectScheduledEventMatching(duration, messageWithPayload(any(eventType)));
    }

    @Override
    public FixtureExecutionResult expectScheduledEventMatching(ZonedDateTime scheduledTime,
                                                               Matcher<?> matcher) {
        eventSchedulerValidator.assertScheduledEventMatching(scheduledTime, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(ZonedDateTime scheduledTime, Object applicationEvent) {
        return expectScheduledEventMatching(scheduledTime, messageWithPayload(equalTo(applicationEvent, fieldFilter)));
    }

    @Override
    public FixtureExecutionResult expectScheduledEventOfType(ZonedDateTime scheduledTime,
                                                             Class<?> eventType) {
        return expectScheduledEventMatching(scheduledTime, messageWithPayload(any(eventType)));
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommandsEqualTo(Object... expected) {
        commandValidator.assertDispatchedEqualTo(expected);
        return this;
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommandsMatching(Matcher<? extends Iterable<?>> matcher) {
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
    public FixtureExecutionResult expectPublishedEventsMatching(Matcher<? extends Iterable<?>> matcher) {
        eventValidator.assertPublishedEventsMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Object... expected) {
        eventValidator.assertPublishedEvents(expected);
        return this;
    }
}
