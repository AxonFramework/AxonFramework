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

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.joda.time.Duration;
import org.junit.*;

import java.util.List;
import java.util.UUID;

import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class FixtureExecutionResultImplTest {

    private FixtureExecutionResultImpl testSubject;
    private RecordingCommandBus commandBus;
    private SimpleEventBus eventBus;
    private StubEventScheduler eventScheduler;
    private InMemorySagaRepository sagaRepository;
    private TimerTriggeredEvent applicationEvent;
    private String identifier;

    @Before
    public void setUp() throws Exception {
        commandBus = new RecordingCommandBus();
        eventBus = new SimpleEventBus();
        eventScheduler = new StubEventScheduler();
        sagaRepository = new InMemorySagaRepository();
        testSubject = new FixtureExecutionResultImpl(sagaRepository, eventScheduler, eventBus,
                                                     commandBus, StubSaga.class, AllFieldsFilter.instance());
        testSubject.startRecording();
        identifier = UUID.randomUUID().toString();
        applicationEvent = new TimerTriggeredEvent(identifier);
    }

    @Test
    public void testStartRecording() {
        testSubject = new FixtureExecutionResultImpl(sagaRepository, eventScheduler, eventBus,
                                                     commandBus, StubSaga.class, AllFieldsFilter.instance());
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        eventBus.publish(new GenericEventMessage<TriggerSagaStartEvent>(new TriggerSagaStartEvent(identifier)));
        testSubject.startRecording();
        TriggerSagaEndEvent endEvent = new TriggerSagaEndEvent(identifier);
        eventBus.publish(new GenericEventMessage<TriggerSagaEndEvent>(endEvent));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectPublishedEvents(endEvent);
        testSubject.expectPublishedEventsMatching(payloadsMatching(exactSequenceOf(equalTo(endEvent), andNoMore())));

        testSubject.expectDispatchedCommandsEqualTo("Second");
        testSubject.expectDispatchedCommandsMatching(payloadsMatching(exactSequenceOf(equalTo("Second"), andNoMore())));
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectPublishedEvents_WrongCount() {
        eventBus.publish(new GenericEventMessage<TriggerSagaEndEvent>(new TriggerSagaEndEvent(identifier)));

        testSubject.expectPublishedEvents(new TriggerSagaEndEvent(identifier),
                                          new TriggerExistingSagaEvent(identifier));
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectPublishedEvents_WrongType() {
        eventBus.publish(new GenericEventMessage<TriggerSagaEndEvent>(new TriggerSagaEndEvent(identifier)));

        testSubject.expectPublishedEvents(new TriggerExistingSagaEvent(identifier));
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectPublishedEvents_FailedMatcher() {
        eventBus.publish(new GenericEventMessage<TriggerSagaEndEvent>(new TriggerSagaEndEvent(identifier)));

        testSubject.expectPublishedEvents(new FailingMatcher<EventMessage>());
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectDispatchedCommands_FailedCount() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Third"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Fourth"));

        testSubject.expectDispatchedCommandsEqualTo("First", "Second", "Third");
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectDispatchedCommands_FailedType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectDispatchedCommandsEqualTo("First", "Third");
    }

    @Test
    public void testExpectDispatchedCommands() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectDispatchedCommandsEqualTo("First", "Second");
    }

    @Test
    public void testExpectDispatchedCommands_ObjectsNotImplementingEquals() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        testSubject.expectDispatchedCommandsEqualTo(new SimpleCommand("First"), new SimpleCommand("Second"));
    }

    @Test
    public void testExpectDispatchedCommands_ObjectsNotImplementingEquals_FailedField() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        try {
            testSubject.expectDispatchedCommandsEqualTo(new SimpleCommand("Second"), new SimpleCommand("Thrid"));
            fail("Expected exception");
        } catch (AxonAssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("expected <Second>"));
        }
    }

    @Test
    public void testExpectDispatchedCommands_ObjectsNotImplementingEquals_WrongType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        try {
            testSubject.expectDispatchedCommandsEqualTo("Second", new SimpleCommand("Thrid"));
            fail("Expected exception");
        } catch (AxonAssertionError e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("Expected <String>"));
        }
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectNoDispatchedCommands_Failed() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        testSubject.expectNoDispatchedCommands();
    }

    @Test
    public void testExpectNoDispatchedCommands() {
        testSubject.expectNoDispatchedCommands();
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectDispatchedCommands_FailedMatcher() {
        testSubject.expectDispatchedCommandsEqualTo(new FailingMatcher<String>());
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectNoScheduledEvents_EventIsScheduled() {
        eventScheduler.schedule(Duration.standardSeconds(1), new GenericEventMessage<TimerTriggeredEvent>(
                applicationEvent));
        testSubject.expectNoScheduledEvents();
    }

    @Test
    public void testExpectNoScheduledEvents_NoEventScheduled() {
        testSubject.expectNoScheduledEvents();
    }

    @Test
    public void testExpectNoScheduledEvents_ScheduledEventIsTriggered() {
        eventScheduler.schedule(Duration.standardSeconds(1), new GenericEventMessage<TimerTriggeredEvent>(
                applicationEvent));
        eventScheduler.advanceToNextTrigger();
        testSubject.expectNoScheduledEvents();
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_WrongDateTime() {
        eventScheduler.schedule(Duration.standardSeconds(1), new GenericEventMessage<TimerTriggeredEvent>(
                applicationEvent));
        eventScheduler.advanceTime(new Duration(500));
        testSubject.expectScheduledEvent(Duration.standardSeconds(1), applicationEvent);
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_WrongClass() {
        eventScheduler.schedule(Duration.standardSeconds(1), new GenericEventMessage<TimerTriggeredEvent>(
                applicationEvent));
        eventScheduler.advanceTime(new Duration(500));
        testSubject.expectScheduledEventOfType(Duration.standardSeconds(1), Object.class);
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_WrongEvent() {
        eventScheduler.schedule(Duration.standardSeconds(1),
                                new GenericEventMessage<TimerTriggeredEvent>(applicationEvent));
        eventScheduler.advanceTime(new Duration(500));
        testSubject.expectScheduledEvent(Duration.standardSeconds(1),
                                         new GenericEventMessage<TimerTriggeredEvent>(new TimerTriggeredEvent(
                                                 "unexpected")));
    }

    @SuppressWarnings({"unchecked"})
    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_FailedMatcher() {
        eventScheduler.schedule(Duration.standardSeconds(1), new GenericEventMessage<TimerTriggeredEvent>(
                applicationEvent));
        eventScheduler.advanceTime(new Duration(500));
        testSubject.expectScheduledEvent(Duration.standardSeconds(1),
                                         new FailingMatcher());
    }

    @Test
    public void testExpectScheduledEvent_Found() {
        eventScheduler.schedule(Duration.standardSeconds(1), new GenericEventMessage<TimerTriggeredEvent>(
                applicationEvent));
        eventScheduler.advanceTime(new Duration(500));
        testSubject.expectScheduledEvent(new Duration(500), applicationEvent);
    }

    @Test
    public void testExpectScheduledEvent_FoundInMultipleCandidates() {
        eventScheduler.schedule(Duration.standardSeconds(1),
                                new GenericEventMessage<TimerTriggeredEvent>(new TimerTriggeredEvent("unexpected1")));
        eventScheduler.schedule(Duration.standardSeconds(1),
                                new GenericEventMessage<TimerTriggeredEvent>(applicationEvent));
        eventScheduler.schedule(Duration.standardSeconds(1),
                                new GenericEventMessage<TimerTriggeredEvent>(new TimerTriggeredEvent("unexpected2")));
        testSubject.expectScheduledEvent(Duration.standardSeconds(1), applicationEvent);
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssociationWith_WrongValue() {
        StubSaga saga = new StubSaga();
        saga.associateWith("key", "value");
        sagaRepository.add(saga);

        testSubject.expectAssociationWith("key", "value2");
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssociationWith_WrongKey() {
        StubSaga saga = new StubSaga();
        saga.associateWith("key", "value");
        sagaRepository.add(saga);

        testSubject.expectAssociationWith("key2", "value");
    }

    @Test
    public void testAssociationWith_Present() {
        StubSaga saga = new StubSaga();
        saga.associateWith("key", "value");
        sagaRepository.add(saga);

        testSubject.expectAssociationWith("key", "value");
    }

    @Test
    public void testNoAssociationWith_WrongValue() {
        StubSaga saga = new StubSaga();
        saga.associateWith("key", "value");
        sagaRepository.add(saga);

        testSubject.expectNoAssociationWith("key", "value2");
    }

    @Test
    public void testNoAssociationWith_WrongKey() {
        StubSaga saga = new StubSaga();
        saga.associateWith("key", "value");
        sagaRepository.add(saga);

        testSubject.expectNoAssociationWith("key2", "value");
    }

    @Test(expected = AxonAssertionError.class)
    public void testNoAssociationWith_Present() {
        StubSaga saga = new StubSaga();
        saga.associateWith("key", "value");
        sagaRepository.add(saga);

        testSubject.expectNoAssociationWith("key", "value");
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectActiveSagas_WrongCount() {
        sagaRepository.add(new StubSaga());

        testSubject.expectActiveSagas(2);
    }

    @Test
    public void testExpectActiveSagas_CorrectCount() {
        sagaRepository.add(new StubSaga());
        StubSaga saga = new StubSaga();
        saga.end();
        sagaRepository.add(saga);

        testSubject.expectActiveSagas(1);
    }

    private class FailingMatcher<T> extends BaseMatcher<List<? extends T>> {

        @Override
        public boolean matches(Object item) {
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("something you'll never be able to deliver");
        }
    }

    private static class SimpleCommand {

        private final String content;

        public SimpleCommand(String content) {
            this.content = content;
        }
    }
}
