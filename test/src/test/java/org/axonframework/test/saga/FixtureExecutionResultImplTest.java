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

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.test.matchers.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify correct execution of the {@link FixtureExecutionResultImpl}.
 *
 * @author Allard Buijze
 */
class FixtureExecutionResultImplTest {

    private FixtureExecutionResultImpl<StubSaga> testSubject;
    private RecordingCommandBus commandBus;
    private SimpleEventBus eventBus;
    private StubEventScheduler eventScheduler;
    private StubDeadlineManager deadlineManager;
    private InMemorySagaStore sagaStore;
    private TimerTriggeredEvent applicationEvent;
    private String identifier;

    @BeforeEach
    void setUp() {
        commandBus = new RecordingCommandBus();
        eventBus = SimpleEventBus.builder().build();
        eventScheduler = new StubEventScheduler();
        deadlineManager = new StubDeadlineManager();
        sagaStore = new InMemorySagaStore();
        testSubject = new FixtureExecutionResultImpl<>(
                sagaStore, eventScheduler, deadlineManager, eventBus, commandBus, StubSaga.class,
                AllFieldsFilter.instance()
        );
        testSubject.startRecording();
        identifier = UUID.randomUUID().toString();
        applicationEvent = new TimerTriggeredEvent(identifier);
    }

    @Test
    void testStartRecording() {
        testSubject = new FixtureExecutionResultImpl<>(
                sagaStore, eventScheduler, deadlineManager, eventBus, commandBus, StubSaga.class,
                AllFieldsFilter.instance()
        );

        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaStartEvent(identifier)));
        testSubject.startRecording();
        TriggerSagaEndEvent endEvent = new TriggerSagaEndEvent(identifier);
        eventBus.publish(new GenericEventMessage<>(endEvent));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectPublishedEvents(endEvent);
        testSubject.expectPublishedEventsMatching(payloadsMatching(exactSequenceOf(equalTo(endEvent), andNoMore())));

        testSubject.expectDispatchedCommands("Second");
        testSubject.expectDispatchedCommandsMatching(payloadsMatching(exactSequenceOf(equalTo("Second"), andNoMore())));
    }

    @Test
    void testStartRecording_ClearsEventsAndCommands() {
        testSubject = new FixtureExecutionResultImpl<>(sagaStore, eventScheduler, deadlineManager, eventBus,
                                                       commandBus, StubSaga.class, AllFieldsFilter.instance());
        testSubject.startRecording();
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Command"));

        testSubject.startRecording();
        testSubject.expectPublishedEvents();
        testSubject.expectNoDispatchedCommands();
    }

    @Test
    void testExpectPublishedEvents_WrongCount() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectPublishedEvents(
                        new TriggerSagaEndEvent(identifier), new TriggerExistingSagaEvent(identifier)
                )
        );
    }

    @Test
    void testExpectPublishedEvents_WrongType() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectPublishedEvents(new TriggerExistingSagaEvent(identifier))
        );
    }

    @Test
    void testExpectPublishedEvents_FailedMatcher() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        assertThrows(
                AxonAssertionError.class, () -> testSubject.expectPublishedEvents(new FailingMatcher<EventMessage<?>>())
        );
    }

    @Test
    void testExpectDispatchedCommands_FailedCount() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Third"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Fourth"));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectDispatchedCommands("First", "Second", "Third"));
    }

    @Test
    void testExpectDispatchedCommands_FailedType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectDispatchedCommands("First", "Third"));
    }

    @Test
    void testExpectDispatchedCommands() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectDispatchedCommands("First", "Second");
    }

    @Test
    void testExpectDispatchedCommands_ObjectsNotImplementingEquals() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        testSubject.expectDispatchedCommands(new SimpleCommand("First"), new SimpleCommand("Second"));
    }

    @Test
    void testExpectDispatchedCommands_ObjectsNotImplementingEquals_FailedField() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        AxonAssertionError e = assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectDispatchedCommands(new SimpleCommand("Second"), new SimpleCommand("Third"))
        );
        assertTrue(e.getMessage().contains("expected <Second>"), "Wrong message: " + e.getMessage());
    }

    @Test
    void testExpectDispatchedCommands_ObjectsNotImplementingEquals_WrongType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        AxonAssertionError e = assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectDispatchedCommands("Second", new SimpleCommand("Third"))
        );
        assertTrue(e.getMessage().contains("Expected <String>"), "Wrong message: " + e.getMessage());
    }

    @Test
    void testExpectNoDispatchedCommands_Failed() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        assertThrows(AxonAssertionError.class, testSubject::expectNoDispatchedCommands);
    }

    @Test
    void testExpectNoDispatchedCommands() {
        testSubject.expectNoDispatchedCommands();
    }

    @Test
    void testExpectDispatchedCommands_FailedMatcher() {
        assertThrows(
                AxonAssertionError.class, () -> testSubject.expectDispatchedCommands(new FailingMatcher<String>())
        );
    }

    @Test
    void testExpectNoScheduledEvents_EventIsScheduled() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        assertThrows(AxonAssertionError.class, testSubject::expectNoScheduledEvents);
    }

    @Test
    void testExpectNoScheduledEvents_NoEventScheduled() {
        testSubject.expectNoScheduledEvents();
    }

    @Test
    void testExpectNoScheduledEvents_ScheduledEventIsTriggered() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceToNextTrigger();
        testSubject.expectNoScheduledEvents();
    }

    @Test
    void testExpectScheduledEvent_WrongDateTime() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent)
        );
    }

    @Test
    void testExpectScheduledEvent_WrongClass() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEventOfType(Duration.ofSeconds(1), Object.class)
        );
    }

    @Test
    void testExpectScheduledEvent_WrongEvent() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEvent(
                        Duration.ofSeconds(1), new GenericEventMessage<>(new TimerTriggeredEvent("unexpected"))
                )
        );
    }

    @Test
    void testExpectScheduledEvent_FailedMatcher() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEvent(Duration.ofSeconds(1), new FailingMatcher<>())
        );
    }

    @Test
    void testExpectScheduledEvent_Found() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        testSubject.expectScheduledEvent(Duration.ofMillis(500), applicationEvent);
    }

    @Test
    void testExpectScheduledEvent_FoundInMultipleCandidates() {
        eventScheduler.schedule(
                Duration.ofSeconds(1), new GenericEventMessage<>(new TimerTriggeredEvent("unexpected1"))
        );
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.schedule(
                Duration.ofSeconds(1), new GenericEventMessage<>(new TimerTriggeredEvent("unexpected2"))
        );
        testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent);
    }

    @Test
    void testAssociationWith_WrongValue() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        assertThrows(AxonAssertionError.class, () -> testSubject.expectAssociationWith("key", "value2"));
    }

    @Test
    void testAssociationWith_WrongKey() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        assertThrows(AxonAssertionError.class, () -> testSubject.expectAssociationWith("key2", "value"));
    }

    @Test
    void testAssociationWith_Present() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        testSubject.expectAssociationWith("key", "value");
    }

    @Test
    void testNoAssociationWith_WrongValue() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        testSubject.expectNoAssociationWith("key", "value2");
    }

    @Test
    void testNoAssociationWith_WrongKey() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        testSubject.expectNoAssociationWith("key2", "value");
    }

    @Test
    void testNoAssociationWith_Present() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoAssociationWith("key", "value"));
    }

    @Test
    void testExpectActiveSagas_WrongCount() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), Collections.emptySet());

        assertThrows(AxonAssertionError.class, () -> testSubject.expectActiveSagas(2));
    }

    @Test
    void testExpectActiveSagas_CorrectCount() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), Collections.emptySet());
        sagaStore.deleteSaga(StubSaga.class, "test", Collections.emptySet());
        sagaStore.insertSaga(StubSaga.class, "test2", new StubSaga(), Collections.emptySet());

        testSubject.expectActiveSagas(1);
    }

    @Test
    void testStartRecordingCallback() {
        AtomicInteger startRecordingCallbackInvocations = new AtomicInteger();
        testSubject.registerStartRecordingCallback(startRecordingCallbackInvocations::incrementAndGet);
        testSubject.startRecording();

        assertThat(startRecordingCallbackInvocations.get(), equalTo(1));
    }

    private static class SimpleCommand {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String content;

        public SimpleCommand(String content) {
            this.content = content;
        }
    }

    private static class FailingMatcher<T> extends BaseMatcher<List<? extends T>> {

        @Override
        public boolean matches(Object item) {
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("something you'll never be able to deliver");
        }
    }
}
