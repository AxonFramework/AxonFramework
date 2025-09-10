/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import jakarta.annotation.Nonnull;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AbstractEventBus}.
 *
 * @author Rene de Waele
 */
class AbstractEventBusTest {

    private static final MessageType TEST_EVENT_NAME = new MessageType("event");

    private LegacyUnitOfWork<?> unitOfWork;
    private StubPublishingEventBus testSubject;

    @BeforeEach
    void setUp() {
        (unitOfWork = spy(new LegacyDefaultUnitOfWork<>(null))).start();
        testSubject = spy(StubPublishingEventBus.builder().build());
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void consumersRegisteredWithUnitOfWorkWhenFirstEventIsPublished() {
        EventMessage event = newEvent();
        testSubject.publish(event);
        verify(unitOfWork).onPrepareCommit(any());
        verify(unitOfWork).onCommit(any());
        // the monitor callback is also registered
        verify(unitOfWork, times(2)).afterCommit(any());
        assertEquals(emptyList(), testSubject.committedEvents);
        unitOfWork.commit();
        assertEquals(Collections.singletonList(event), testSubject.committedEvents);
    }

    @Test
    void noMoreConsumersRegisteredWithUnitOfWorkWhenSecondEventIsPublished() {
        EventMessage event = newEvent();
        testSubject.publish(event);
        verify(unitOfWork).onPrepareCommit(any());
        verify(unitOfWork).onCommit(any());
        // the monitor callback is also registered
        verify(unitOfWork, times(2)).afterCommit(any());
        //noinspection unchecked
        reset(unitOfWork);

        testSubject.publish(event);
        verify(unitOfWork, never()).onPrepareCommit(any());
        verify(unitOfWork, never()).onCommit(any());
        // the monitor callback should still be registered
        verify(unitOfWork).afterCommit(any());

        unitOfWork.commit();
        List<EventMessage> actual = testSubject.committedEvents;
        assertEquals(Arrays.asList(event, event), actual);
    }

    @Test
    void commitOnUnitOfWork() {
        EventMessage event = newEvent();
        testSubject.publish(event);
        unitOfWork.commit();
        assertEquals(Collections.singletonList(event), testSubject.committedEvents);
    }

    @Test
    void publicationOrder() {
        EventMessage eventA = newEvent(), eventB = newEvent();
        testSubject.publish(eventA);
        testSubject.publish(eventB);
        unitOfWork.commit();
        assertEquals(Arrays.asList(eventA, eventB), testSubject.committedEvents);
    }

    @Test
    void publicationWithNestedUow() {
        testSubject.publish(numberedEvent(5));
        unitOfWork.commit();
        assertEquals(Arrays.asList(numberedEvent(5), numberedEvent(4), numberedEvent(3), numberedEvent(2),
                                   numberedEvent(1), numberedEvent(0)), testSubject.committedEvents);
        verify(testSubject, times(6)).prepareCommit(any());
        verify(testSubject, times(6)).commit(any());
        verify(testSubject, times(6)).afterCommit(any());

        // each UoW will register onPrepareCommit on the parent
        verify(unitOfWork, times(1)).onPrepareCommit(any());
        // each UoW will register onCommit with the root
        verify(unitOfWork, times(6)).onCommit(any());
    }

    @Test
    void publicationForbiddenDuringUowCommitPhase() {
        StubPublishingEventBus.builder()
                              .publicationPhase(LegacyUnitOfWork.Phase.COMMIT)
                              .startNewUowBeforePublishing(false)
                              .build()
                              .publish(numberedEvent(5));

        assertThrows(IllegalStateException.class, unitOfWork::commit);
    }

    @Test
    void publicationForbiddenDuringRootUowCommitPhase() {
        testSubject = spy(StubPublishingEventBus.builder().publicationPhase(LegacyUnitOfWork.Phase.COMMIT).build());
        testSubject.publish(numberedEvent(1));

        assertThrows(IllegalStateException.class, unitOfWork::commit);
    }

    @Test
    void messageMonitorRecordsIngestionAndPublication_InUnitOfWork() {
        //noinspection unchecked
        MessageMonitor<? super EventMessage> mockMonitor = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback mockMonitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(mockMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);
        testSubject = spy(StubPublishingEventBus.builder().messageMonitor(mockMonitor).build());

        testSubject.publish(EventTestUtils.asEventMessage("test1"), EventTestUtils.asEventMessage("test2"));

        verify(mockMonitor, times(2)).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();

        unitOfWork.commit();
        verify(mockMonitorCallback, times(2)).reportSuccess();
    }

    @Test
    void dispatchInterceptor() {

        final List<Integer> seenMessages = new ArrayList<>();

        //noinspection unchecked
        MessageDispatchInterceptor<EventMessage> dispatchInterceptorMock = mock(MessageDispatchInterceptor.class);
        String key = "additional", value = "metadata";
        when(dispatchInterceptorMock.interceptOnDispatch(any(), any(), any())).thenAnswer(invocation -> {
            EventMessage message = invocation.getArgument(0);
            synchronized (seenMessages) {
                if (seenMessages.contains(message.hashCode())) {
                    return MessageStream.failed(
                            new AssertionError("MessageProcessor is asked to process the same event message twice")
                    );
                } else {
                    seenMessages.add(message.hashCode());
                }
            }
            ProcessingContext processingContext = invocation.getArgument(1);
            MessageDispatchInterceptorChain<EventMessage> chain = invocation.getArgument(2);
            return chain.proceed(message, processingContext);
        });
        testSubject.registerDispatchInterceptor(dispatchInterceptorMock);
        testSubject.publish(newEvent(), newEvent());
        verifyNoInteractions(dispatchInterceptorMock);

        unitOfWork.commit();
        //noinspection unchecked
        ArgumentCaptor<EventMessage> argumentCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(dispatchInterceptorMock, times(2)).interceptOnDispatch(argumentCaptor.capture(), any(), any()); //prepare commit, commit, and after commit
        assertEquals(2, argumentCaptor.getAllValues().size());
        assertEquals(2, seenMessages.size());
    }

    private static EventMessage newEvent() {
        return new GenericEventMessage(TEST_EVENT_NAME, new Object());
    }

    private static EventMessage numberedEvent(final int number) {
        return new StubNumberedEvent(number);
    }

    private static class StubPublishingEventBus extends AbstractEventBus {

        private final List<EventMessage> committedEvents = new ArrayList<>();

        private final LegacyUnitOfWork.Phase publicationPhase;
        private final boolean startNewUowBeforePublishing;

        private StubPublishingEventBus(Builder builder) {
            super(builder);
            this.publicationPhase = builder.publicationPhase;
            this.startNewUowBeforePublishing = builder.startNewUowBeforePublishing;
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        protected void prepareCommit(List<? extends EventMessage> events) {
            if (publicationPhase == LegacyUnitOfWork.Phase.PREPARE_COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void commit(List<? extends EventMessage> events) {
            if (publicationPhase == LegacyUnitOfWork.Phase.COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void afterCommit(List<? extends EventMessage> events) {
            if (publicationPhase == LegacyUnitOfWork.Phase.AFTER_COMMIT) {
                onEvents(events);
            }
        }

        private void onEvents(List<? extends EventMessage> events) {
            //if the event payload is a number > 0, a new number is published that is 1 smaller than the first number
            Object payload = events.get(0).payload();
            if (payload instanceof Integer) {
                int number = (int) payload;
                if (number > 0) {
                    EventMessage nextEvent = numberedEvent(number - 1);
                    if (startNewUowBeforePublishing) {
                        LegacyUnitOfWork<?> nestedUnitOfWork = LegacyDefaultUnitOfWork.startAndGet(null);
                        try {
                            publish(nextEvent);
                        } finally {
                            nestedUnitOfWork.commit();
                        }
                    } else {
                        publish(nextEvent);
                    }
                }
            }
            committedEvents.addAll(events);
        }

        @Override
        public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        private static class Builder extends AbstractEventBus.Builder {

            private LegacyUnitOfWork.Phase publicationPhase = LegacyUnitOfWork.Phase.PREPARE_COMMIT;
            private boolean startNewUowBeforePublishing = true;

            @SuppressWarnings("SameParameterValue")
            private Builder publicationPhase(LegacyUnitOfWork.Phase publicationPhase) {
                this.publicationPhase = publicationPhase;
                return this;
            }

            @Override
            public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage> messageMonitor) {
                super.messageMonitor(messageMonitor);
                return this;
            }

            @SuppressWarnings("SameParameterValue")
            private Builder startNewUowBeforePublishing(boolean startNewUowBeforePublishing) {
                this.startNewUowBeforePublishing = startNewUowBeforePublishing;
                return this;
            }

            private StubPublishingEventBus build() {
                return new StubPublishingEventBus(this);
            }
        }
    }

    private static class StubNumberedEvent extends GenericEventMessage {

        private static final MessageType TYPE = new MessageType("StubNumberedEvent");

        StubNumberedEvent(Integer payload) {
            super(TYPE, payload);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StubNumberedEvent that = (StubNumberedEvent) o;
            return Objects.equals(payload(), that.payload());
        }

        @Override
        public int hashCode() {
            return Objects.hash(payload());
        }

        @Override
        public String toString() {
            return "StubNumberedEvent{" + payload() + "}";
        }
    }
}
