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

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.DirectExecutor;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

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
        return new UnitOfWork("unit-of-work-id", DirectExecutor.instance(), EmptyApplicationContext.INSTANCE);
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

    private void registerNextPhase(ProcessingContext processingContext, AtomicInteger phase) {
        int next = phase.getAndIncrement();
        if (next < 10000) {
            processingContext.runOn(new CustomPhase(next), p -> registerNextPhase(p, phase));
        }
    }

    private record CustomPhase(int order) implements ProcessingLifecycle.Phase {

    }
}
