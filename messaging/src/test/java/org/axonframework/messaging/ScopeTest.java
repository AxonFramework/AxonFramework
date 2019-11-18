package org.axonframework.messaging;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ScopeTest {


    private ScheduledExecutorService executor;

    @Before
    public void setUp() throws Exception {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Test
    public void testScopeCompletionAwaitsAsyncCompletion() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        MockScope scope = new MockScope();
        scope.executeWithResult(() -> {
            assertSame(scope, Scope.getCurrentScope());
            Scope.Async async = scope.doStartAsync();
            return executor.schedule(() -> {
                async.bindScope();
                counter.incrementAndGet();
                assertSame(scope, Scope.getCurrentScope());
                async.complete();
                return null;
            }, 10, TimeUnit.MILLISECONDS);
        });
        assertEquals(1, counter.get());
    }

    @Test
    public void testCompletionOfAsyncScopeOnlyAllowedOnce() {
        MockScope scope = new MockScope();
        scope.startScope();
        Scope.Async async = scope.doStartAsync();
        async.complete();
        try {
            async.complete();
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertTrue("Unexpected message: " + e.getMessage(), e.getMessage().contains("Async"));
            assertTrue("Unexpected message: " + e.getMessage(), e.getMessage().contains("marked"));
            assertTrue("Unexpected message: " + e.getMessage(), e.getMessage().contains("as completed"));
        } finally {
            scope.endScope();
        }
    }

    private static class MockScope extends Scope {

        @Override
        public ScopeDescriptor describeScope() {
            return () -> "Mock";
        }

        @Override
        public <V> V executeWithResult(Callable<V> task) throws Exception {
            return super.executeWithResult(task);
        }
    }
}