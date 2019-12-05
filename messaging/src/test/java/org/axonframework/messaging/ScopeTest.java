package org.axonframework.messaging;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ScopeTest {


    private ScheduledExecutorService executor;

    @Before
    public void setUp() throws Exception {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Test
    public void testScopeCompletionAwaitsAsyncCompletion() throws Exception {
        int asyncCount = 10;
        int counter[] = new int[1];
        MockScope scope = new MockScope();
        scope.executeWithResult(() -> {
            assertSame(scope, Scope.getCurrentScope());
            for (int i = 0; i < asyncCount; i++) {
                Scope.Async async = scope.doStartAsync();
                executor.schedule(() -> {
                    async.bindScope();
                    counter[0]++;
                    assertSame(scope, Scope.getCurrentScope());
                    async.complete();
                    return null;
                }, 10, TimeUnit.MILLISECONDS);
            }
            return counter[0]++;
        });
        assertEquals(asyncCount + 1, counter[0]);
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

    @Test
    public void testBindingOfAsyncScopeOnlyAllowedOnce() {
        MockScope scope = new MockScope();
        scope.startScope();
        Scope.Async async = scope.doStartAsync();
        async.bindScope();
        try {
            async.bindScope();
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertTrue("Unexpected message: " + e.getMessage(), e.getMessage().contains("bind Scope"));
            assertTrue("Unexpected message: " + e.getMessage(), e.getMessage().contains("more than once"));
        } finally {
            async.complete();
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