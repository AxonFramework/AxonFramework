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

package org.axonframework.messaging.core.unitofwork;

import org.axonframework.common.DirectExecutor;
import org.axonframework.common.util.MockException;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating whether the {@link UnitOfWork} complies with the expectations of a
 * {@link ProcessingLifecycle} implementation.
 *
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
class UnitOfWorkTest extends ProcessingLifecycleTest<UnitOfWork> {

    @Override
    UnitOfWork createTestSubject() {
        return new UnitOfWork("unit-of-work-id", DirectExecutor.instance(), false, EmptyApplicationContext.INSTANCE);
    }

    @Override
    CompletableFuture<?> execute(UnitOfWork testSubject) {
        return testSubject.execute();
    }

    @Test
    void executeWithResultReturnsResultAtCompletionOfUnitOfWork() {
        UnitOfWork testSubject = createTestSubject();
        CompletableFuture<String> invocationResult = new CompletableFuture<>();
        CompletableFuture<String> actual = testSubject.executeWithResult(pc -> invocationResult);

        assertFalse(actual.isDone());

        invocationResult.complete("CompletionResult");

        assertTrue(actual.isDone());
        assertTrue(testSubject.isCompleted());
        assertFalse(actual.isCompletedExceptionally());

        assertEquals("CompletionResult", actual.join());
    }

    @Test
    void executeWithResultReturnsExceptionalResultAtCompletionOfUnitOfWork() {
        UnitOfWork testSubject = createTestSubject();
        CompletableFuture<String> invocationResult = new CompletableFuture<>();
        CompletableFuture<String> actual = testSubject.executeWithResult(pc -> invocationResult);

        assertFalse(actual.isDone());

        invocationResult.completeExceptionally(new MockException("CompletionResult"));

        assertTrue(actual.isDone());
        assertTrue(testSubject.isCompleted());
        assertTrue(testSubject.isError());
        assertTrue(actual.isCompletedExceptionally());

        CompletionException actualException = assertThrows(CompletionException.class, actual::join);
        assertInstanceOf(MockException.class, actualException.getCause());
        assertEquals("CompletionResult", actualException.getCause().getMessage());
    }

    @Test
    void executeWithExtremeNumberOfPhaseHandlers() {
        UnitOfWork testSubject = createTestSubject();
        AtomicInteger phaseNr = new AtomicInteger(-10000);
        testSubject.runOn(new CustomPhase(phaseNr.getAndIncrement()), p -> registerNextPhase(p, phaseNr));

        CompletableFuture<Void> execute = testSubject.execute();
        assertTrue(execute.isDone());
        execute.join();
        assertFalse(execute.isCompletedExceptionally());
    }

    @Test
    void exceptionsThrownInInvocationAreReturnedInFuture() {
        UnitOfWork testSubject = createTestSubject();
        CompletableFuture<Object> actual = testSubject.executeWithResult(c -> {
            throw new MockException("Simulating bad behavior");
        });

        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }

    @Test
    void allHandlersAreInvokedOnMainThreadWhenUoWCommitsSuccessfully() {
        List<String> threadNames = new ArrayList<>();
        try (ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
            UnitOfWork uow = new UnitOfWork("testSingleCoordinatorThread",
                                            Runnable::run,
                                            true,
                                            EmptyApplicationContext.INSTANCE);

            uow.on(ProcessingLifecycle.DefaultPhases.INVOCATION, c -> captureThreadName(threadNames, executor));
            uow.on(ProcessingLifecycle.DefaultPhases.COMMIT, c -> captureThreadName(threadNames, executor));
            uow.on(ProcessingLifecycle.DefaultPhases.AFTER_COMMIT,
                   c -> captureThreadName(threadNames, executor));
            uow.whenComplete(c -> captureThreadName(threadNames, executor));
            uow.doFinally(c -> captureThreadName(threadNames, executor));

            CompletableFuture<Void> actual = uow.execute();
            await().until(actual::isDone);

            threadNames.forEach(name -> assertEquals(Thread.currentThread().getName(), name));
            assertEquals(5, threadNames.size());
        }
    }

    @Test
    void allHandlersAreInvokedOnMainThreadWhenUoWRollsBack() {
        List<String> threadNames = new ArrayList<>();
        try (ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
            UnitOfWork uow = new UnitOfWork("testSingleCoordinatorThread",
                                            Runnable::run,
                                            true,
                                            EmptyApplicationContext.INSTANCE);

            uow.on(ProcessingLifecycle.DefaultPhases.INVOCATION, c -> captureThreadName(threadNames, executor));
            uow.on(ProcessingLifecycle.DefaultPhases.INVOCATION, c -> captureThreadName(threadNames, executor));
            uow.on(ProcessingLifecycle.DefaultPhases.COMMIT, c -> captureThreadName(threadNames, executor));
            uow.on(ProcessingLifecycle.DefaultPhases.COMMIT, c -> captureThreadName(threadNames, executor));
            uow.on(ProcessingLifecycle.DefaultPhases.AFTER_COMMIT,
                   c -> captureThreadNameAndFail(threadNames, executor));
            uow.whenComplete(c -> {
                captureThreadName(threadNames, executor);
                fail("WhenComplete handler should not be invoked");
            });
            uow.doFinally(c -> captureThreadName(threadNames, executor));

            CompletableFuture<Void> actual = uow.execute();
            await().until(actual::isDone);

            threadNames.forEach(name -> assertEquals(Thread.currentThread().getName(), name));
            assertEquals(6, threadNames.size());
            assertInstanceOf(MockException.class, actual.exceptionNow());
        }
    }

    private CompletableFuture<?> captureThreadName(List<String> threadNames, ScheduledExecutorService executor) {
        threadNames.add(Thread.currentThread().getName());
        CompletableFuture<?> completableFuture = new CompletableFuture<>();
        executor.schedule(() -> completableFuture.complete(null), 10, TimeUnit.MILLISECONDS);
        return completableFuture;
    }

    private CompletableFuture<?> captureThreadNameAndFail(List<String> threadNames, ScheduledExecutorService executor) {
        threadNames.add(Thread.currentThread().getName());
        CompletableFuture<?> completableFuture = new CompletableFuture<>();
        executor.schedule(() -> completableFuture.completeExceptionally(new MockException()),
                          10,
                          TimeUnit.MILLISECONDS);
        return completableFuture;
    }

    private void registerNextPhase(ProcessingContext processingContext, AtomicInteger phase) {
        int next = phase.getAndIncrement();
        if (next < 10000) {
            processingContext.runOn(new CustomPhase(next), p -> registerNextPhase(p, phase));
        }
    }

    private record CustomPhase(int order) implements ProcessingLifecycle.Phase {

    }
}
