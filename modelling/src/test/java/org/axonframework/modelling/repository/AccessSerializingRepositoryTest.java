/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.utils.AssertUtils;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessSerializingRepositoryTest {

    private AsyncRepository.LifecycleManagement<String, String> delegate;
    private AccessSerializingRepository<String, String> testSubject;

    @BeforeEach
    void setUp() {
        delegate = mock();
        testSubject = new AccessSerializingRepository<>(delegate);
    }

    @Test
    void concurrentAccessToSameIdentifierIsBlocked() {
        AtomicInteger concurrentCounter = new AtomicInteger();
        when(delegate.load(eq("aggregateId"), any())).thenAnswer(i -> {
            i.getArgument(1, ProcessingContext.class).runOnAfterCommit(pc -> concurrentCounter.decrementAndGet());
            return CompletableFuture.completedFuture(new StubEntity("instance" + concurrentCounter.incrementAndGet()));
        });
        when(delegate.attach(any(), any())).thenAnswer(i -> i.getArgument(0));

        AsyncUnitOfWork uow1 = new AsyncUnitOfWork();
        AsyncUnitOfWork uow2 = new AsyncUnitOfWork();

        // set blockers to allow both to run concurrently and block a wait moment at the repository
        CompletableFuture<Void> uowBlocker1 = new CompletableFuture<>();
        CompletableFuture<Void> uowBlocker2 = new CompletableFuture<>();
        uow1.onPostInvocation(pc -> uowBlocker1);
        uow2.onPostInvocation(pc -> uowBlocker2);

        AtomicBoolean task2Loaded = new AtomicBoolean();

        CompletableFuture<String> result1 = uow1.executeWithResult(pc -> testSubject.load("aggregateId", pc)
                                                                                    .thenApply(ManagedEntity::entity));
        CompletableFuture<String> result2 = uow2.executeWithResult(pc -> testSubject.load("aggregateId", pc)
                                                                                    .thenApply(ManagedEntity::entity)
                                                                                    .whenComplete((r, e) -> task2Loaded.set(
                                                                                            true)));

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
    }

    @Test
    void aTimeoutOnAQueuedItemMakesTheNextWaitForCompletionOfAllPreviousItems() {
        AtomicInteger concurrentCounter = new AtomicInteger();
        when(delegate.load(eq("aggregateId"), any())).thenAnswer(i -> {
            i.getArgument(1, ProcessingContext.class).runOnAfterCommit(pc -> concurrentCounter.decrementAndGet());
            return CompletableFuture.completedFuture(new StubEntity("instance" + concurrentCounter.incrementAndGet()));
        });
        when(delegate.attach(any(), any())).thenAnswer(i -> i.getArgument(0));

        AsyncUnitOfWork uow1 = new AsyncUnitOfWork("uow1");
        AsyncUnitOfWork uow2 = new AsyncUnitOfWork("uow2");
        AsyncUnitOfWork uow3 = new AsyncUnitOfWork("uow3");

        // set blockers to allow both to run concurrently and block a wait moment at the repository
        CompletableFuture<Void> uowBlocker1 = new CompletableFuture<>();
        CompletableFuture<Void> uowBlocker3 = new CompletableFuture<>();
        uow1.onPostInvocation(pc -> uowBlocker1);
        uow3.onPostInvocation(pc -> uowBlocker3);

        AtomicBoolean task2Loaded = new AtomicBoolean();
        AtomicBoolean task3Loaded = new AtomicBoolean();

        CompletableFuture<String> result1 = uow1.executeWithResult(pc -> testSubject.load("aggregateId", pc))
                                                .thenApply(ManagedEntity::entity);
        CompletableFuture<String> result2 = uow2.executeWithResult(pc -> testSubject.load("aggregateId", pc)
                                                                                    .orTimeout(10,
                                                                                               TimeUnit.MILLISECONDS)
                                                                                    .whenComplete((r, e) -> task2Loaded.set(
                                                                                            true)))
                                                .thenApply(ManagedEntity::entity);
        CompletableFuture<String> result3 = uow3.executeWithResult(pc -> testSubject.load("aggregateId", pc)
                                                                                    .whenComplete((r, e) -> task3Loaded.set(
                                                                                            true)))
                                                .thenApply(ManagedEntity::entity);

        assertFalse(result1.isDone());
        // this one times out, and is expected to have completed
        AssertUtils.assertWithin(100, TimeUnit.MILLISECONDS, () -> assertTrue(result2.isDone()));

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

        verify(delegate, times(2)).load(eq("aggregateId"), any());
    }

    private static class StubEntity implements ManagedEntity<String, String> {

        private final String entity;

        public StubEntity(String entity) {
            this.entity = entity;
        }

        @Override
        public String identifier() {
            return "";
        }

        @Override
        public String entity() {
            return entity;
        }

        @Override
        public String applyStateChange(UnaryOperator change) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}