package org.axonframework.messaging.unitofwork;

import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating whether the {@link AsyncUnitOfWork} complies with the expectations of a
 * {@link ProcessingLifecycle} implementation.
 *
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
class AsyncUnitOfWorkTest extends ProcessingLifecycleTest<AsyncUnitOfWork> {

    @Override
    AsyncUnitOfWork createTestSubject() {
        return new AsyncUnitOfWork();
    }

    @Override
    CompletableFuture<?> execute(AsyncUnitOfWork testSubject) {
        return testSubject.execute();
    }

    @Test
    void executeWithResultReturnsResultAtCompletionOfUnitOfWork() {
        AsyncUnitOfWork testSubject = createTestSubject();
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
        AsyncUnitOfWork testSubject = createTestSubject();
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
}
