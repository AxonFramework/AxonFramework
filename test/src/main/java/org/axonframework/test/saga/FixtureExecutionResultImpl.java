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

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.List;

import static org.axonframework.test.matchers.Matchers.equalTo;
import static org.hamcrest.CoreMatchers.any;

/**
 * Default implementation of FixtureExecutionResult.
 *
 * @author Allard Buijze
 * @since 1.1
 */
class FixtureExecutionResultImpl implements FixtureExecutionResult {

    private final RepositoryContentValidator repositoryContentValidator;
    private final EventValidator eventValidator;
    private final EventSchedulerValidator eventSchedulerValidator;
    private CommandValidator commandValidator;

    /**
     * Initializes an instance and make it monitor the given infrastructure classes.
     *
     * @param sagaRepository The repository to monitor
     * @param eventScheduler The scheduler to monitor
     * @param eventBus       The event bus to monitor
     * @param commandBus     The command bus to monitor
     * @param sagaType       The type of Saga under test
     */
    FixtureExecutionResultImpl(InMemorySagaRepository sagaRepository, StubEventScheduler eventScheduler,
                               EventBus eventBus, RecordingCommandBus commandBus,
                               Class<? extends AbstractAnnotatedSaga> sagaType) {
        commandValidator = new CommandValidator(commandBus);
        repositoryContentValidator = new RepositoryContentValidator(sagaRepository, sagaType);
        eventValidator = new EventValidator(eventBus);
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
        repositoryContentValidator.assertAssociationPresent(associationKey, associationValue);
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoAssociationWith(String associationKey, Object associationValue) {
        repositoryContentValidator.assertNoAssociationPresent(associationKey,
                                                              associationValue);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, Matcher<? extends ApplicationEvent> matcher) {
        eventSchedulerValidator.assertScheduledEvent(duration, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, ApplicationEvent applicationEvent) {
        return expectScheduledEvent(duration, equalTo(applicationEvent));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(Duration duration, Class<? extends ApplicationEvent> eventType) {
        return expectScheduledEvent(duration, any(eventType));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(DateTime scheduledTime,
                                                       Matcher<? extends ApplicationEvent> matcher) {
        eventSchedulerValidator.assertScheduledEvent(scheduledTime, matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(DateTime scheduledTime, ApplicationEvent applicationEvent) {
        return expectScheduledEvent(scheduledTime, equalTo(applicationEvent));
    }

    @Override
    public FixtureExecutionResult expectScheduledEvent(DateTime scheduledTime,
                                                       Class<? extends ApplicationEvent> eventType) {
        return expectScheduledEvent(scheduledTime, any(eventType));
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommandsEqualTo(Object... expected) {
        commandValidator.assertDispatchedEqualTo(expected);
        return this;
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommandsMatching(Matcher<List<?>> matcher) {
        commandValidator.assertDispatchedMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoScheduledEvents() {
        eventSchedulerValidator.assertNoScheduledEvents();
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Matcher<List<? extends Event>> matcher) {
        eventValidator.assertPublishedEvents(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Event... expected) {
        eventValidator.assertPublishedEvents(expected);
        return this;
    }
}
