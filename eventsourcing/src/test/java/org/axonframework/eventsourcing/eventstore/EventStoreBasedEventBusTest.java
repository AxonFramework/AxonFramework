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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.EventTestUtils.createEvent;
import static org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils.aUnitOfWork;
import static org.axonframework.utils.AssertUtils.awaitSuccessfulCompletion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventStoreBasedEventBus}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class EventStoreBasedEventBusTest {

    private EventStorageEngine mockStorageEngine = mock(EventStorageEngine.class);
    private TagResolver tagResolver = mock(TagResolver.class);
    private EventStoreBasedEventBus testSubject = new EventStoreBasedEventBus(new SimpleEventStore(mockStorageEngine, tagResolver), new SimpleEventBus());

    @Nested
    class Subscription {

        @Test
        void subscribeAddsConsumerAndReturnsRegistration() {
            // given
            RecordingEventListener listener = new RecordingEventListener();

            // when
            var registration = testSubject.subscribe(listener);

            // then
            assertNotNull(registration);
            assertTrue(registration.cancel());
            assertFalse(registration.cancel()); // Second cancellation should return false
        }

        @Test
        void subscribingSameConsumerTwiceOnlyAddsItOnce() {
            // given
            RecordingEventListener listener = new RecordingEventListener();

            // when
            var registration1 = testSubject.subscribe(listener);
            var registration2 = testSubject.subscribe(listener);

            // then
            assertNotNull(registration1);
            assertNotNull(registration2);
            assertTrue(registration1.cancel());
            assertFalse(registration2.cancel()); // Already removed by registration1
        }

        @Test
        void subscribersAreNotifiedWhenEventsArePublishedWithoutContext() {
            // given
            EventStorageEngine.AppendTransaction<String> mockAppendTransaction = mock();
            when(mockAppendTransaction.commit(any())).thenReturn(completedFuture("anything"));
            when(mockAppendTransaction.afterCommit(any(), any(ProcessingContext.class)))
                    .thenReturn(completedFuture(mock(ConsistencyMarker.class)));
            when(mockStorageEngine.appendEvents(any(), isNull(), anyList()))
                    .thenReturn(completedFuture(mockAppendTransaction));
            when(tagResolver.resolve(any())).thenReturn(Set.of());

            RecordingEventListener listener = new RecordingEventListener();
            testSubject.subscribe(listener);
            EventMessage testEvent = createEvent(0);

            // when
            CompletableFuture<Void> result = testSubject.publish(null, testEvent);

            // then
            awaitSuccessfulCompletion(result);
            assertThat(listener.getReceivedEvents())
                    .hasSize(1)
                    .containsExactly(testEvent);
            assertThat(listener.getInvocationCount()).isEqualTo(1);
            assertThat(listener.getCapturedContexts()).containsExactly((ProcessingContext) null);
        }

        @Test
        void subscribersAreNotifiedWhenEventsArePublishedWithContext() {
            // given
            EventStorageEngine.AppendTransaction<?> mockAppendTransaction = mock();
            when(mockAppendTransaction.commit(any(ProcessingContext.class))).thenReturn(completedFuture(null));
            when(mockAppendTransaction.afterCommit(any(), any(ProcessingContext.class)))
                    .thenReturn(completedFuture(mock(ConsistencyMarker.class)));
            when(mockStorageEngine.appendEvents(any(), any(ProcessingContext.class), anyList()))
                    .thenReturn(completedFuture(mockAppendTransaction));
            when(tagResolver.resolve(any())).thenReturn(Set.of());

            RecordingEventListener listener = new RecordingEventListener();
            testSubject.subscribe(listener);
            EventMessage testEventZero = createEvent(0);
            EventMessage testEventOne = createEvent(1);

            // when
            UnitOfWork uow = aUnitOfWork();
            uow.onPreInvocation(context -> testSubject.publish(context, testEventZero, testEventOne));

            awaitSuccessfulCompletion(uow.execute());

            // then
            assertThat(listener.getReceivedEvents())
                    .hasSize(2)
                    .containsExactly(testEventZero, testEventOne);
            assertThat(listener.getInvocationCount()).isEqualTo(1);
            assertThat(listener.getCapturedContexts())
                    .hasSize(1)
                    .first()
                    .isNotNull();
        }

        @Test
        void unsubscribedConsumersAreNotNotified() {
            // given
            EventStorageEngine.AppendTransaction<String> mockAppendTransaction = mock();
            when(mockAppendTransaction.commit(any())).thenReturn(completedFuture("anything"));
            when(mockAppendTransaction.afterCommit(any(), any(ProcessingContext.class)))
                    .thenReturn(completedFuture(mock(ConsistencyMarker.class)));
            when(mockStorageEngine.appendEvents(any(), isNull(), anyList()))
                    .thenReturn(completedFuture(mockAppendTransaction));
            when(tagResolver.resolve(any())).thenReturn(Set.of());

            RecordingEventListener listener = new RecordingEventListener();
            var registration = testSubject.subscribe(listener);
            EventMessage testEvent = createEvent(0);

            // when
            registration.cancel();
            CompletableFuture<Void> result = testSubject.publish(null, testEvent);

            // then
            awaitSuccessfulCompletion(result);
            assertThat(listener.getReceivedEvents()).isEmpty();
            assertThat(listener.getInvocationCount()).isEqualTo(0);
        }

        @Test
        void subscriberExceptionRollsBackTransaction() {
            // given
            EventStorageEngine.AppendTransaction<?> mockAppendTransaction = mock();
            when(mockStorageEngine.appendEvents(any(), any(ProcessingContext.class), anyList()))
                    .thenReturn(completedFuture(mockAppendTransaction));
            when(tagResolver.resolve(any())).thenReturn(Set.of());

            RuntimeException subscriberException = new RuntimeException("Subscriber failed");
            FailingEventListener listener = new FailingEventListener(subscriberException);
            testSubject.subscribe(listener);
            EventMessage testEvent = createEvent(0);

            // when
            UnitOfWork uow = aUnitOfWork();
            uow.onPreInvocation(context -> testSubject.publish(context, testEvent));
            CompletableFuture<Void> result = uow.execute();

            // then
            ExecutionException exception = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertSame(subscriberException, exception.getCause());
            assertThat(listener.getInvocationCount()).isEqualTo(1);
            verify(mockAppendTransaction, never()).commit(any(ProcessingContext.class));
            verify(mockAppendTransaction).rollback(any(ProcessingContext.class));
        }
    }

    // Test listener implementations

    /**
     * Recording listener that tracks all invocations, events, and contexts received.
     */
    private static class RecordingEventListener implements java.util.function.BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> {
        private final List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
        private final List<ProcessingContext> capturedContexts = new CopyOnWriteArrayList<>();
        private int invocationCount = 0;

        @Override
        public CompletableFuture<?> apply(List<? extends EventMessage> events, ProcessingContext context) {
            invocationCount++;
            receivedEvents.addAll(events);
            capturedContexts.add(context);
            return CompletableFuture.completedFuture(null);
        }

        public List<EventMessage> getReceivedEvents() {
            return new ArrayList<>(receivedEvents);
        }

        public List<ProcessingContext> getCapturedContexts() {
            return new ArrayList<>(capturedContexts);
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    /**
     * Listener that throws an exception when invoked.
     */
    private static class FailingEventListener implements java.util.function.BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> {
        private final RuntimeException exceptionToThrow;
        private int invocationCount = 0;

        public FailingEventListener(RuntimeException exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public CompletableFuture<?> apply(List<? extends EventMessage> events, ProcessingContext context) {
            invocationCount++;
            throw exceptionToThrow;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

}