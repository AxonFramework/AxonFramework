/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.deadline.ScheduledDeadlineInfo;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.test.matchers.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify correct execution of the {@link FixtureExecutionResultImpl}.
 *
 * @author Allard Buijze
 */
@ExtendWith(MockitoExtension.class)
class FixtureExecutionResultImplTest {

    private FixtureExecutionResultImpl<StubSaga> testSubject;
    private RecordingCommandBus commandBus;
    private SimpleEventBus eventBus;
    private StubEventScheduler eventScheduler;
    @Mock
    private StubDeadlineManager deadlineManager;
    private InMemorySagaStore sagaStore;
    private TimerTriggeredEvent applicationEvent;
    private String identifier;

    private final Instant deadlineWindowFrom = Instant.now();
    private final Instant deadlineWindowTo = Instant.now().plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        commandBus = new RecordingCommandBus();
        eventBus = SimpleEventBus.builder().build();
        eventScheduler = new StubEventScheduler();
        sagaStore = new InMemorySagaStore();
        testSubject = new FixtureExecutionResultImpl<>(
                sagaStore, eventScheduler, deadlineManager, eventBus, commandBus, StubSaga.class,
                AllFieldsFilter.instance(), new RecordingListenerInvocationErrorHandler(new LoggingErrorHandler()));
        testSubject.startRecording();
        identifier = UUID.randomUUID().toString();
        applicationEvent = new TimerTriggeredEvent(identifier);
    }

    @Test
    void startRecording() throws Exception {
        RecordingListenerInvocationErrorHandler errorHandler = new RecordingListenerInvocationErrorHandler(
                (exception, event, eventHandler) -> {/* No-op */}
        );
        EventMessageHandler eventMessageHandler = mock(EventMessageHandler.class);
        testSubject = new FixtureExecutionResultImpl<>(
                sagaStore, eventScheduler, deadlineManager, eventBus, commandBus, StubSaga.class,
                AllFieldsFilter.instance(), errorHandler);

        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        GenericEventMessage<TriggerSagaStartEvent> firstEventMessage =
                new GenericEventMessage<>(new TriggerSagaStartEvent(identifier));
        eventBus.publish(firstEventMessage);
        Exception testException = new IllegalArgumentException("First");
        assertThrows(IllegalArgumentException.class,
                     () -> errorHandler.onError(testException, firstEventMessage, eventMessageHandler));

        testSubject.startRecording();

        GenericEventMessage<TriggerSagaEndEvent> endEventMessage =
                new GenericEventMessage<>(new TriggerSagaEndEvent(identifier));
        eventBus.publish(endEventMessage);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));
        IllegalArgumentException secondException = new IllegalArgumentException("Second");
        errorHandler.onError(secondException, endEventMessage, eventMessageHandler);

        testSubject.expectPublishedEvents(endEventMessage.getPayload());
        testSubject.expectPublishedEventsMatching(payloadsMatching(exactSequenceOf(deepEquals(endEventMessage.getPayload()), andNoMore())));

        testSubject.expectDispatchedCommands("Second");
        testSubject.expectDispatchedCommandsMatching(payloadsMatching(exactSequenceOf(deepEquals("Second"), andNoMore())));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectSuccessfulHandlerExecution());
        assertTrue(errorHandler.getException().isPresent());
        assertEquals(secondException, errorHandler.getException().get());
    }

    @Test
    void startRecording_ClearsEventsAndCommands() throws Exception {
        RecordingListenerInvocationErrorHandler errorHandler = new RecordingListenerInvocationErrorHandler(
                (exception, event, eventHandler) -> {/* No-op */}
        );
        EventMessageHandler eventMessageHandler = mock(EventMessageHandler.class);
        testSubject = new FixtureExecutionResultImpl<>(sagaStore, eventScheduler, deadlineManager, eventBus,
                                                       commandBus, StubSaga.class, AllFieldsFilter.instance(), errorHandler);
        testSubject.startRecording();
        GenericEventMessage<TriggerSagaEndEvent> eventMessage = new GenericEventMessage<>(new TriggerSagaEndEvent(identifier));
        eventBus.publish(eventMessage);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Command"));
        errorHandler.onError(new IllegalArgumentException("First"), eventMessage, eventMessageHandler);

        testSubject.startRecording();
        testSubject.expectPublishedEvents();
        testSubject.expectNoDispatchedCommands();
        testSubject.expectSuccessfulHandlerExecution();
    }

    @Test
    void expectPublishedEvents_WrongCount() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectPublishedEvents(
                        new TriggerSagaEndEvent(identifier), new TriggerExistingSagaEvent(identifier)
                )
        );
    }

    @Test
    void expectPublishedEvents_WrongType() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectPublishedEvents(new TriggerExistingSagaEvent(identifier))
        );
    }

    @Test
    void expectPublishedEvents_FailedMatcher() {
        eventBus.publish(new GenericEventMessage<>(new TriggerSagaEndEvent(identifier)));

        assertThrows(
                AxonAssertionError.class, () -> testSubject.expectPublishedEvents(new FailingMatcher<EventMessage<?>>())
        );
    }

    @Test
    void expectDispatchedCommands_FailedCount() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Third"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Fourth"));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectDispatchedCommands("First", "Second", "Third"));
    }

    @Test
    void expectDispatchedCommands_FailedType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectDispatchedCommands("First", "Third"));
    }

    @Test
    void expectDispatchedCommands() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("Second"));

        testSubject.expectDispatchedCommands("First", "Second");
    }

    @Test
    void expectDispatchedCommands_ObjectsNotImplementingEquals() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        testSubject.expectDispatchedCommands(new SimpleCommand("First"), new SimpleCommand("Second"));
    }

    @Test
    void expectDispatchedCommands_ObjectsNotImplementingEquals_FailedField() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        AxonAssertionError e = assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectDispatchedCommands(new SimpleCommand("Second"), new SimpleCommand("Third"))
        );
        assertTrue(e.getMessage().contains("Expected <SimpleCommand{content='Second'"), "Wrong message: " + e.getMessage());
    }

    @Test
    void expectDispatchedCommands_ObjectsNotImplementingEquals_WrongType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("First")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new SimpleCommand("Second")));

        AxonAssertionError e = assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectDispatchedCommands("Second", new SimpleCommand("Third"))
        );
        assertTrue(e.getMessage().contains("Expected <String>"), "Wrong message: " + e.getMessage());
    }

    @Test
    void expectNoDispatchedCommands_Failed() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("First"));
        assertThrows(AxonAssertionError.class, testSubject::expectNoDispatchedCommands);
    }

    @Test
    void expectNoDispatchedCommands() {
        testSubject.expectNoDispatchedCommands();
    }

    @Test
    void expectDispatchedCommands_FailedMatcher() {
        assertThrows(
                AxonAssertionError.class, () -> testSubject.expectDispatchedCommands(new FailingMatcher<String>())
        );
    }

    @Test
    void expectNoScheduledEvents_EventIsScheduled() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        assertThrows(AxonAssertionError.class, testSubject::expectNoScheduledEvents);
    }

    @Test
    void expectNoScheduledEvents_NoEventScheduled() {
        testSubject.expectNoScheduledEvents();
    }

    @Test
    void expectNoScheduledEvents_ScheduledEventIsTriggered() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceToNextTrigger();
        testSubject.expectNoScheduledEvents();
    }

    @Test
    void expectScheduledEvent_WrongDateTime() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent)
        );
    }

    @Test
    void expectScheduledEvent_WrongClass() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEventOfType(Duration.ofSeconds(1), Object.class)
        );
    }

    @Test
    void expectScheduledEvent_WrongEvent() {
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
    void expectScheduledEvent_FailedMatcher() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        assertThrows(
                AxonAssertionError.class,
                () -> testSubject.expectScheduledEvent(Duration.ofSeconds(1), new FailingMatcher<>())
        );
    }

    @Test
    void expectScheduledEvent_Found() {
        eventScheduler.schedule(Duration.ofSeconds(1), new GenericEventMessage<>(applicationEvent));
        eventScheduler.advanceTimeBy(Duration.ofMillis(500), i -> {
        });
        testSubject.expectScheduledEvent(Duration.ofMillis(500), applicationEvent);
    }

    @Test
    void expectScheduledEvent_FoundInMultipleCandidates() {
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
    void associationWith_WrongValue() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        assertThrows(AxonAssertionError.class, () -> testSubject.expectAssociationWith("key", "value2"));
    }

    @Test
    void associationWith_WrongKey() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        assertThrows(AxonAssertionError.class, () -> testSubject.expectAssociationWith("key2", "value"));
    }

    @Test
    void associationWith_Present() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        testSubject.expectAssociationWith("key", "value");
    }

    @Test
    void noAssociationWith_WrongValue() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        testSubject.expectNoAssociationWith("key", "value2");
    }

    @Test
    void noAssociationWith_WrongKey() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        testSubject.expectNoAssociationWith("key2", "value");
    }

    @Test
    void noAssociationWith_Present() {
        sagaStore.insertSaga(
                StubSaga.class, "test", new StubSaga(), Collections.singleton(new AssociationValue("key", "value"))
        );

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoAssociationWith("key", "value"));
    }

    @Test
    void expectActiveSagas_WrongCount() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), Collections.emptySet());

        assertThrows(AxonAssertionError.class, () -> testSubject.expectActiveSagas(2));
    }

    @Test
    void expectActiveSagas_CorrectCount() {
        sagaStore.insertSaga(StubSaga.class, "test", new StubSaga(), Collections.emptySet());
        sagaStore.deleteSaga(StubSaga.class, "test", Collections.emptySet());
        sagaStore.insertSaga(StubSaga.class, "test2", new StubSaga(), Collections.emptySet());

        testSubject.expectActiveSagas(1);
    }

    @Test
    void startRecordingCallback() {
        AtomicInteger startRecordingCallbackInvocations = new AtomicInteger();
        testSubject.registerStartRecordingCallback(startRecordingCallbackInvocations::incrementAndGet);
        testSubject.startRecording();

        assertThat(startRecordingCallbackInvocations.get(), deepEquals(1));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithOtherDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.nullValue()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlineAtFrom() {
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(deadlineWindowFrom)));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlineAtTo() {
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(deadlineWindowTo)));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        ScheduledDeadlineInfo deadlineInfo = createDeadline(expiryTime);
        Object deadline = deadlineInfo.deadlineMessage().getPayload();
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadlineInfo));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline));
    }

    @Test
    void noDeadlineInTimeframeWithOtherDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, new Object()));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlineAtFrom() {
        ScheduledDeadlineInfo deadlineInfo = createDeadline(deadlineWindowFrom);
        Object deadline = deadlineInfo.deadlineMessage().getPayload();
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadlineInfo));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlineAtTo() {
        ScheduledDeadlineInfo deadlineInfo = createDeadline(deadlineWindowTo);
        Object deadline = deadlineInfo.deadlineMessage().getPayload();
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadlineInfo));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadlineBefore.deadlineMessage().getPayload()));
        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadlineAfter.deadlineMessage().getPayload()));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        ScheduledDeadlineInfo deadline = createDeadline(expiryTime);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithOtherDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, Integer.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlineAtFrom() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlineAtTo() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        ScheduledDeadlineInfo deadline = createDeadline(expiryTime);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    @Test
    void noDeadlineWithNameTimeframeWithOtherDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "otherName"));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlineAtFrom() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlineAtTo() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> testSubject.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> testSubject.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    private ScheduledDeadlineInfo createDeadline(Instant expiryTime) {
        DeadlineMessage<String> deadlineMessage = GenericDeadlineMessage.asDeadlineMessage("deadlineName", "payload", expiryTime);
        return new ScheduledDeadlineInfo(expiryTime, "deadlineName", "1", 0, deadlineMessage, null);
    }

    private static class SimpleCommand {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String content;

        public SimpleCommand(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return "SimpleCommand{" +
                    "content='" + content + '\'' +
                    '}';
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
