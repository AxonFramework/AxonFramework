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

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Allard Buijze
 */
public class FixtureExecutionResultImplTest {

    private FixtureExecutionResultImpl<StubSaga> testSubject;
    private RecordingCommandBus commandBus;
    private SimpleEventBus eventBus;
    private StubEventScheduler eventScheduler;
    private InMemorySagaStore sagaStore;
    private TimerTriggeredEvent applicationEvent;
    private String identifier;

    @Before
    public void setUp() throws Exception {
        commandBus = new RecordingCommandBus();
        eventBus = new SimpleEventBus();
        eventScheduler = new StubEventScheduler();
        sagaStore = new InMemorySagaStore();
        testSubject = new FixtureExecutionResultImpl<>(sagaStore, eventScheduler, eventBus,
                                                     commandBus, StubSaga.class, AllFieldsFilter.instance());
        testSubject.startRecording();
        identifier = UUID.randomUUID().toString();
        applicationEvent = new TimerTriggeredEvent(identifier);
    }

    @Test
    public void testStartRecording() {
        testSubject = new FixtureExecutionResultImpl<>(sagaStore, eventScheduler, eventBus,
                                                     commandBus, StubSaga.class, AllFieldsFilter.instance());
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaStartEvent(identifier)));
        testSubject.startRecording();
        TriggerSagaEndEvent endEvent = new TriggerSagaEndEvent(identifier);
        eventBus.publish(new GenericEventMessage<>(endEvent));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectPublishedEvents(endEvent);
        testSubject.expectPublishedEventsMatching(payloadsMatching(exactSequenceOf(equalTo(endEvent), andNoMore())));

        testSubject.expectDispatchedCommandsEqualTo("Second");
        testSubject.expectDispatchedCommandsMatching(payloadsMatching(exactSequenceOf(equalTo("Second"), andNoMore())));
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectPublishedEvents_WrongCount() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        testSubject.expectPublishedEvents(new TriggerSagaEndEvent(identifier),
                                          new TriggerExistingSagaEvent(identifier));
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectPublishedEvents_WrongType() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        testSubject.expectPublishedEvents(new TriggerExistingSagaEvent(identifier));
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectPublishedEvents_FailedMatcher() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

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
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(
                applicationEvent));
        testSubject.expectNoScheduledEvents();
    }

    @Test
    public void testExpectNoScheduledEvents_NoEventScheduled() {
        testSubject.expectNoScheduledEvents();
    }

    @Test
    public void testExpectNoScheduledEvents_ScheduledEventIsTriggered() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(
                applicationEvent));
        eventScheduler.advanceToNextTrigger();
        testSubject.expectNoScheduledEvents();
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_WrongDateTime() throws Exception {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(
                applicationEvent));
        eventScheduler.advanceTime(Duration.ofMillis(500), i -> {});
        testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent);
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_WrongClass() throws Exception {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(
                applicationEvent));
        eventScheduler.advanceTime(Duration.ofMillis(500), i -> {});
        testSubject.expectScheduledEventOfType(Duration.ofSeconds(1), Object.class);
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_WrongEvent() throws Exception {
        eventScheduler.schedule(Duration.ofSeconds(1),
                                new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTime(Duration.ofMillis(500), i -> {});
        testSubject.expectScheduledEvent(Duration.ofSeconds(1),
                                         new GenericEventMessage<>(new TimerTriggeredEvent(
                                                 "unexpected")));
    }

    @SuppressWarnings({"unchecked"})
    @Test(expected = AxonAssertionError.class)
    public void testExpectScheduledEvent_FailedMatcher() throws Exception {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(
                applicationEvent));
        eventScheduler.advanceTime(Duration.ofMillis(500), i -> {});
        testSubject.expectScheduledEvent(Duration.ofSeconds(1),
                                         new FailingMatcher());
    }

    @Test
    public void testExpectScheduledEvent_Found() throws Exception {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(
                applicationEvent));
        eventScheduler.advanceTime(Duration.ofMillis(500), i -> {});
        testSubject.expectScheduledEvent(Duration.ofMillis(500), applicationEvent);
    }

    @Test
    public void testExpectScheduledEvent_FoundInMultipleCandidates() {
        eventScheduler.schedule(Duration.ofSeconds(1),
                                new GenericEventMessage<>(new TimerTriggeredEvent("unexpected1")));
        eventScheduler.schedule(Duration.ofSeconds(1),
                                new GenericEventMessage<>(applicationEvent));
        eventScheduler.schedule(Duration.ofSeconds(1),
                                new GenericEventMessage<>(new TimerTriggeredEvent("unexpected2")));
        testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent);
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssociationWith_WrongValue() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.singleton(new AssociationValue("key", "value")));

        testSubject.expectAssociationWith("key", "value2");
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssociationWith_WrongKey() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.singleton(new AssociationValue("key", "value")));

        testSubject.expectAssociationWith("key2", "value");
    }

    @Test
    public void testAssociationWith_Present() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.singleton(new AssociationValue("key", "value")));

        testSubject.expectAssociationWith("key", "value");
    }

    @Test
    public void testNoAssociationWith_WrongValue() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.singleton(new AssociationValue("key", "value")));

        testSubject.expectNoAssociationWith("key", "value2");
    }

    @Test
    public void testNoAssociationWith_WrongKey() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.singleton(new AssociationValue("key", "value")));

        testSubject.expectNoAssociationWith("key2", "value");
    }

    @Test(expected = AxonAssertionError.class)
    public void testNoAssociationWith_Present() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.singleton(new AssociationValue("key", "value")));

        testSubject.expectNoAssociationWith("key", "value");
    }

    @Test(expected = AxonAssertionError.class)
    public void testExpectActiveSagas_WrongCount() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.emptySet());

        testSubject.expectActiveSagas(2);
    }

    @Test
    public void testExpectActiveSagas_CorrectCount() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), null, Collections.emptySet());
        sagaStore.deleteSaga(StubSaga.class, "test", Collections.emptySet());
        sagaStore.insertSaga(StubSaga.class, "test2", new StubSaga(), null, Collections.emptySet());

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
