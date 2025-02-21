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

package org.axonframework.modelling.repository;

import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AccessSerializingRepository}.
 *
 * @author Allard Buijze
 */
class AccessSerializingRepositoryTest {

    private static final String AGGREGATE_ID = "aggregateId";

    private AsyncRepository.LifecycleManagement<String, String> delegate;

    private AccessSerializingRepository<String, String> testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        delegate = mock();
        AtomicInteger concurrentCounter = new AtomicInteger();
        when(delegate.load(eq(AGGREGATE_ID), any())).thenAnswer(invocation -> {
            invocation.getArgument(1, ProcessingContext.class)
                      .runOnAfterCommit(pc -> concurrentCounter.decrementAndGet());
            return CompletableFuture.completedFuture(new StubEntity("instance" + concurrentCounter.incrementAndGet()));
        });
        when(delegate.attach(any(), any())).thenAnswer(i -> i.getArgument(0));

        testSubject = new AccessSerializingRepository<>(delegate);
    }

    @Test
    void concurrentAccessToSameIdentifierIsBlocked() {
        AsyncUnitOfWork uow1 = new AsyncUnitOfWork();
        AsyncUnitOfWork uow2 = new AsyncUnitOfWork();
        // Set blockers to allow both to run concurrently and block a wait moment at the repository
        CompletableFuture<Void> uowBlocker1 = new CompletableFuture<>();
        CompletableFuture<Void> uowBlocker2 = new CompletableFuture<>();
        uow1.onPostInvocation(pc -> uowBlocker1);
        uow2.onPostInvocation(pc -> uowBlocker2);
        AtomicBoolean task2Loaded = new AtomicBoolean();

        CompletableFuture<String> result1 =
                uow1.executeWithResult(context -> testSubject.load(AGGREGATE_ID, context))
                    .thenApply(ManagedEntity::entity);
        CompletableFuture<String> result2 =
                uow2.executeWithResult(
                            context -> testSubject.load(AGGREGATE_ID, context)
                                                  .whenComplete((r, e) -> task2Loaded.set(true))
                    )
                    .thenApply(ManagedEntity::entity);

        assertFalse(result1.isDone());
        assertFalse(result2.isDone());
        assertFalse(task2Loaded.get());

        uowBlocker1.complete(null);

        assertTrue(result1.isDone());
        assertFalse(result2.isDone());
        assertTrue(task2Loaded.get());

        uowBlocker2.complete(null);

        assertTrue(result1.isDone());
        assertTrue(result2.isDone());
        assertEquals("instance1", result1.resultNow());
        assertEquals("instance1", result2.resultNow());
        // Load is invoked once as the entity was still present from the first operation.
        verify(delegate, times(1)).load(eq(AGGREGATE_ID), any());
    }

    @Test
    void timeoutOnQueuedOperationMakesTheNextWaitForCompletionOfAllPreviousItems() {
        AsyncUnitOfWork uow1 = new AsyncUnitOfWork("uow1");
        AsyncUnitOfWork uow2 = new AsyncUnitOfWork("uow2");
        AsyncUnitOfWork uow3 = new AsyncUnitOfWork("uow3");
        // Set blockers to allow both to run concurrently and block a wait moment at the repository
        CompletableFuture<Void> uowBlocker1 = new CompletableFuture<>();
        CompletableFuture<Void> uowBlocker3 = new CompletableFuture<>();
        uow1.onPostInvocation(pc -> uowBlocker1);
        uow3.onPostInvocation(pc -> uowBlocker3);
        AtomicBoolean task2Loaded = new AtomicBoolean();
        AtomicBoolean task3Loaded = new AtomicBoolean();

        CompletableFuture<String> result1 =
                uow1.executeWithResult(context -> testSubject.load(AGGREGATE_ID, context))
                    .thenApply(ManagedEntity::entity);
        CompletableFuture<String> result2 =
                uow2.executeWithResult(
                            context -> testSubject.load(AGGREGATE_ID, context)
                                                  .orTimeout(10, TimeUnit.MILLISECONDS)
                                                  .whenComplete((r, e) -> task2Loaded.set(true))
                    )
                    .thenApply(ManagedEntity::entity);
        CompletableFuture<String> result3 =
                uow3.executeWithResult(
                            context -> testSubject.load(AGGREGATE_ID, context)
                                                  .whenComplete((r, e) -> task3Loaded.set(true))
                    )
                    .thenApply(ManagedEntity::entity);

        assertFalse(result1.isDone());
        // This one times out, and is expected to have completed
        await().pollDelay(Duration.ofMillis(10))
               .atMost(Duration.ofMillis(100))
               .untilAsserted(() -> assertTrue(result2.isCompletedExceptionally()));

        assertTrue(task2Loaded.get());
        assertFalse(result3.isDone());
        assertFalse(task3Loaded.get());

        uowBlocker1.complete(null);

        assertTrue(result1.isDone());
        assertFalse(result3.isDone());
        assertTrue(task3Loaded.get());

        uowBlocker3.complete(null);

        assertTrue(result1.isDone());
        assertTrue(result2.isDone());
        assertTrue(result3.isDone());
        assertEquals("instance1", result1.resultNow());
        assertInstanceOf(TimeoutException.class, result2.exceptionNow());
        assertEquals("instance1", result3.resultNow());
        // Load is invoked twice since the middle operation failed.
        verify(delegate, times(2)).load(eq(AGGREGATE_ID), any());
    }

    private record StubEntity(String entity) implements ManagedEntity<String, String> {

        @Override
        public String identifier() {
            return "some-identifier";
        }

        @Override
        public String applyStateChange(UnaryOperator<String> change) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}