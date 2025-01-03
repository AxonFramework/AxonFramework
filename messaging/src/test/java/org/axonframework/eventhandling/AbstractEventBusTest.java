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
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
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
import javax.annotation.Nonnull;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AbstractEventBus}.
 *
 * @author Rene de Waele
 */
class AbstractEventBusTest {

    private static final QualifiedName TEST_EVENT_NAME = new QualifiedName("test", "event", "0.0.1");

    private UnitOfWork<?> unitOfWork;
    private StubPublishingEventBus testSubject;

    @BeforeEach
    void setUp() {
        (unitOfWork = spy(new DefaultUnitOfWork<>(null))).start();
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
        EventMessage<?> event = newEvent();
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
        EventMessage<?> event = newEvent();
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
        List<EventMessage<?>> actual = testSubject.committedEvents;
        assertEquals(Arrays.asList(event, event), actual);
    }

    @Test
    void commitOnUnitOfWork() {
        EventMessage<?> event = newEvent();
        testSubject.publish(event);
        unitOfWork.commit();
        assertEquals(Collections.singletonList(event), testSubject.committedEvents);
    }

    @Test
    void publicationOrder() {
        EventMessage<?> eventA = newEvent(), eventB = newEvent();
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
                              .publicationPhase(UnitOfWork.Phase.COMMIT)
                              .startNewUowBeforePublishing(false)
                              .build()
                              .publish(numberedEvent(5));

        assertThrows(IllegalStateException.class, unitOfWork::commit);
    }

    @Test
    void publicationForbiddenDuringRootUowCommitPhase() {
        testSubject = spy(StubPublishingEventBus.builder().publicationPhase(UnitOfWork.Phase.COMMIT).build());
        testSubject.publish(numberedEvent(1));

        assertThrows(IllegalStateException.class, unitOfWork::commit);
    }

    @Test
    void messageMonitorRecordsIngestionAndPublication_InUnitOfWork() {
        //noinspection unchecked
        MessageMonitor<? super EventMessage<?>> mockMonitor = mock(MessageMonitor.class);
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
        //noinspection unchecked
        MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptorMock = mock(MessageDispatchInterceptor.class);
        String key = "additional", value = "metaData";
        when(dispatchInterceptorMock.handle(anyList())).thenAnswer(invocation -> {
            //noinspection unchecked
            List<EventMessage<?>> eventMessages = (List<EventMessage<?>>) invocation.getArguments()[0];
            return (BiFunction<Integer, Object, Object>) (index, message) -> {
                if (eventMessages.get(index).getMetaData().containsKey(key)) {
                    throw new AssertionError("MessageProcessor is asked to process the same event message twice");
                }
                return eventMessages.get(index).andMetaData(Collections.singletonMap(key, value));
            };
        });
        testSubject.registerDispatchInterceptor(dispatchInterceptorMock);
        testSubject.publish(newEvent(), newEvent());
        verifyNoInteractions(dispatchInterceptorMock);

        unitOfWork.commit();
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatchInterceptorMock).handle(argumentCaptor.capture()); //prepare commit, commit, and after commit
        assertEquals(1, argumentCaptor.getAllValues().size());
        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(value, argumentCaptor.getValue().get(0).getMetaData().get(key));
    }

    private static EventMessage<Object> newEvent() {
        return new GenericEventMessage<>(TEST_EVENT_NAME, new Object());
    }

    private static EventMessage<Integer> numberedEvent(final int number) {
        return new StubNumberedEvent(number);
    }

    private static class StubPublishingEventBus extends AbstractEventBus {

        private final List<EventMessage<?>> committedEvents = new ArrayList<>();

        private final UnitOfWork.Phase publicationPhase;
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
        protected void prepareCommit(List<? extends EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.PREPARE_COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void afterCommit(List<? extends EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.AFTER_COMMIT) {
                onEvents(events);
            }
        }

        private void onEvents(List<? extends EventMessage<?>> events) {
            //if the event payload is a number > 0, a new number is published that is 1 smaller than the first number
            Object payload = events.get(0).getPayload();
            if (payload instanceof Integer) {
                int number = (int) payload;
                if (number > 0) {
                    EventMessage<Integer> nextEvent = numberedEvent(number - 1);
                    if (startNewUowBeforePublishing) {
                        UnitOfWork<?> nestedUnitOfWork = DefaultUnitOfWork.startAndGet(null);
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
        public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        private static class Builder extends AbstractEventBus.Builder {

            private UnitOfWork.Phase publicationPhase = UnitOfWork.Phase.PREPARE_COMMIT;
            private boolean startNewUowBeforePublishing = true;

            @SuppressWarnings("SameParameterValue")
            private Builder publicationPhase(UnitOfWork.Phase publicationPhase) {
                this.publicationPhase = publicationPhase;
                return this;
            }

            @Override
            public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
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

    private static class StubNumberedEvent extends GenericEventMessage<Integer> {

        private static final QualifiedName NAME = new QualifiedName("test", "StubNumberedEvent", "0.0.1");

        StubNumberedEvent(Integer payload) {
            super(NAME, payload);
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
            return Objects.equals(getPayload(), that.getPayload());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getPayload());
        }

        @Override
        public String toString() {
            return "StubNumberedEvent{" + getPayload() + "}";
        }
    }
}
