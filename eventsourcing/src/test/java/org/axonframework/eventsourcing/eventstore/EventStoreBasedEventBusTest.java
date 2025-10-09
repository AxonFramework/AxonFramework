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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;
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
@ExtendWith(MockitoExtension.class)
class EventStoreBasedEventBusTest {

    private EventStorageEngine mockStorageEngine = mock(EventStorageEngine.class);
    private TagResolver tagResolver = mock(TagResolver.class);
    private EventStoreBasedEventBus testSubject = new EventStoreBasedEventBus(new SimpleEventStore(mockStorageEngine, tagResolver), new SimpleEventBus());

    @Nested
    class Subscription {

        @Test
        void subscribeAddsConsumerAndReturnsRegistration() {
            // given
            var mockConsumer = mock(java.util.function.BiConsumer.class);

            // when
            var registration = testSubject.subscribe(mockConsumer);

            // then
            assertNotNull(registration);
            assertTrue(registration.cancel());
            assertFalse(registration.cancel()); // Second cancellation should return false
        }

        @Test
        void subscribingSameConsumerTwiceOnlyAddsItOnce() {
            // given
            var mockConsumer = mock(java.util.function.BiConsumer.class);

            // when
            var registration1 = testSubject.subscribe(mockConsumer);
            var registration2 = testSubject.subscribe(mockConsumer);

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

            var mockSubscriber = mock(java.util.function.BiConsumer.class);
            testSubject.subscribe(mockSubscriber);
            EventMessage testEvent = createEvent(0);

            // when
            CompletableFuture<Void> result = testSubject.publish(null, testEvent);

            // then
            awaitSuccessfulCompletion(result);
            verify(mockSubscriber).accept(eq(List.of(testEvent)), isNull());
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

            var mockSubscriber = mock(java.util.function.BiConsumer.class);
            testSubject.subscribe(mockSubscriber);
            EventMessage testEventZero = createEvent(0);
            EventMessage testEventOne = createEvent(1);

            // when
            UnitOfWork uow = aUnitOfWork();
            uow.onPreInvocation(context -> testSubject.publish(context, testEventZero, testEventOne));

            awaitSuccessfulCompletion(uow.execute());

            // then
            verify(mockSubscriber).accept(eq(List.of(testEventZero, testEventOne)), any(ProcessingContext.class));
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

            var mockSubscriber = mock(java.util.function.BiConsumer.class);
            var registration = testSubject.subscribe(mockSubscriber);
            EventMessage testEvent = createEvent(0);

            // when
            registration.cancel();
            CompletableFuture<Void> result = testSubject.publish(null, testEvent);

            // then
            awaitSuccessfulCompletion(result);
            verifyNoInteractions(mockSubscriber);
        }

        @Test
        void subscriberExceptionRollsBackTransaction() {
            // given
            EventStorageEngine.AppendTransaction<?> mockAppendTransaction = mock();
            when(mockStorageEngine.appendEvents(any(), any(ProcessingContext.class), anyList()))
                    .thenReturn(completedFuture(mockAppendTransaction));
            when(tagResolver.resolve(any())).thenReturn(Set.of());

            RuntimeException subscriberException = new RuntimeException("Subscriber failed");
            var mockSubscriber = mock(java.util.function.BiConsumer.class);
            doThrow(subscriberException).when(mockSubscriber).accept(any(), any(ProcessingContext.class));
            testSubject.subscribe(mockSubscriber);
            EventMessage testEvent = createEvent(0);

            // when
            UnitOfWork uow = aUnitOfWork();
            uow.onPreInvocation(context -> testSubject.publish(context, testEvent));
            CompletableFuture<Void> result = uow.execute();

            // then
            ExecutionException exception = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertSame(subscriberException, exception.getCause());
            verify(mockSubscriber).accept(eq(List.of(testEvent)), any(ProcessingContext.class));
            verify(mockAppendTransaction, never()).commit(any(ProcessingContext.class));
            verify(mockAppendTransaction).rollback(any(ProcessingContext.class));
        }
    }

}