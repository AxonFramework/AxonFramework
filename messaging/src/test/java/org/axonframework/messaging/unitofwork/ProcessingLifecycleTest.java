package org.axonframework.messaging.unitofwork;

import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for implementations of the {@link ProcessingLifecycle}.
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @param <PL> The implementation of the {@link ProcessingLifecycle} being tested.
 */
abstract class ProcessingLifecycleTest<PL extends ProcessingLifecycle> {

    /**
     * Constructs a test subject.
     *
     * @return A test subject.
     */
    abstract PL createTestSubject();

    /**
     * Execute the given {@code testSubject}, starting the test.
     *
     * @param testSubject The {@link ProcessingLifecycle} under test.
     * @return A {@link CompletableFuture} completing once the given {@code testSubject} executed.
     */
    abstract CompletableFuture<?> execute(PL testSubject);

    @Test
    void synchronousActionsRegisteredInTheSamePhaseAlwaysCompleteBeforeEnteringTheSubsequentPhase() throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        // Phase PreInvocation
        testSubject.onPreInvocation(fixture.createSyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));
        testSubject.onPreInvocation(fixture.createSyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));
        // Phase Invocation
        testSubject.onInvocation(fixture.createSyncHandler(INVOCATION));
        testSubject.onInvocation(fixture.createSyncHandler(INVOCATION));
        // Phase PostInvocation
        testSubject.onPostInvocation(fixture.createSyncHandler(POST_INVOCATION));
        testSubject.onPostInvocation(fixture.createSyncHandler(POST_INVOCATION));
        // Phase onPrepareCommit
        testSubject.onPrepareCommit(fixture.createSyncHandler(PREPARE_COMMIT));
        testSubject.onPrepareCommit(fixture.createSyncHandler(PREPARE_COMMIT));
        // Phase Commit
        testSubject.onCommit(fixture.createSyncHandler(COMMIT));
        testSubject.onCommit(fixture.createSyncHandler(COMMIT));
        // Phase AfterCommit
        testSubject.onAfterCommit(fixture.createSyncHandler(AFTER_COMMIT));
        testSubject.onAfterCommit(fixture.createSyncHandler(AFTER_COMMIT));
        // Phase Completed
        testSubject.onCompleted(fixture.createSyncHandler(COMPLETED));
        testSubject.onCompleted(fixture.createSyncHandler(COMPLETED));

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
    }

    @Test
    void synchronousActionsRegisteredInReversePhaseOrderAreExecutedInTheIntendedPhaseOrder() throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        // Phase Completed
        testSubject.onCompleted(fixture.createSyncHandler(COMPLETED));
        testSubject.onCompleted(fixture.createSyncHandler(COMPLETED));
        // Phase AfterCommit
        testSubject.onAfterCommit(fixture.createSyncHandler(AFTER_COMMIT));
        testSubject.onAfterCommit(fixture.createSyncHandler(AFTER_COMMIT));
        // Phase Commit
        testSubject.onCommit(fixture.createSyncHandler(COMMIT));
        testSubject.onCommit(fixture.createSyncHandler(COMMIT));
        // Phase onPrepareCommit
        testSubject.onPrepareCommit(fixture.createSyncHandler(PREPARE_COMMIT));
        testSubject.onPrepareCommit(fixture.createSyncHandler(PREPARE_COMMIT));
        // Phase PostInvocation
        testSubject.onPostInvocation(fixture.createSyncHandler(POST_INVOCATION));
        testSubject.onPostInvocation(fixture.createSyncHandler(POST_INVOCATION));
        // Phase Invocation
        testSubject.onInvocation(fixture.createSyncHandler(INVOCATION));
        testSubject.onInvocation(fixture.createSyncHandler(INVOCATION));
        // Phase PreInvocation
        testSubject.onPreInvocation(fixture.createSyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));
        testSubject.onPreInvocation(fixture.createSyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
    }

    @Test
    void asynchronousActionsRegisteredInTheSamePhaseAlwaysCompleteBeforeEnteringTheSubsequentPhase() throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        // Phase PreInvocation
        testSubject.onPreInvocation(fixture.createAsyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));
        testSubject.onPreInvocation(fixture.createAsyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));
        // Phase Invocation
        testSubject.onInvocation(fixture.createAsyncHandler(INVOCATION));
        testSubject.onInvocation(fixture.createAsyncHandler(INVOCATION));
        // Phase PostInvocation
        testSubject.onPostInvocation(fixture.createAsyncHandler(POST_INVOCATION));
        testSubject.onPostInvocation(fixture.createAsyncHandler(POST_INVOCATION));
        // Phase onPrepareCommit
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT));
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT));
        // Phase Commit
        testSubject.onCommit(fixture.createAsyncHandler(COMMIT));
        testSubject.onCommit(fixture.createAsyncHandler(COMMIT));
        // Phase AfterCommit
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT));
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT));
        // Phase Completed
        testSubject.onCompleted(fixture.createAsyncHandler(COMPLETED));
        testSubject.onCompleted(fixture.createAsyncHandler(COMPLETED));

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
    }

    @Test
    void asynchronousActionsRegisteredInReversePhaseOrderAreExecutedInTheIntendedPhaseOrder() throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        // Phase Completed
        testSubject.onCompleted(fixture.createAsyncHandler(COMPLETED));
        testSubject.onCompleted(fixture.createAsyncHandler(COMPLETED));
        // Phase AfterCommit
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT));
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT));
        // Phase Commit
        testSubject.onCommit(fixture.createAsyncHandler(COMMIT));
        testSubject.onCommit(fixture.createAsyncHandler(COMMIT));
        // Phase onPrepareCommit
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT));
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT));
        // Phase PostInvocation
        testSubject.onPostInvocation(fixture.createAsyncHandler(POST_INVOCATION));
        testSubject.onPostInvocation(fixture.createAsyncHandler(POST_INVOCATION));
        // Phase Invocation
        testSubject.onInvocation(fixture.createAsyncHandler(INVOCATION));
        testSubject.onInvocation(fixture.createAsyncHandler(INVOCATION));
        // Phase PreInvocation
        testSubject.onPreInvocation(fixture.createAsyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));
        testSubject.onPreInvocation(fixture.createAsyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION));

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
    }

    @Test
    void asynchronousActionsRegisteredInReversePhaseOrderWithDifferingTimeoutsAreExecutedInTheIntendedPhaseOrder()
            throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        // Phase Completed
        testSubject.onCompleted(fixture.createAsyncHandler(COMPLETED, 5));
        testSubject.onCompleted(fixture.createAsyncHandler(COMPLETED, 5));
        // Phase AfterCommit
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT, 10));
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT, 10));
        // Phase Commit
        testSubject.onCommit(fixture.createAsyncHandler(COMMIT, 15));
        testSubject.onCommit(fixture.createAsyncHandler(COMMIT, 15));
        // Phase onPrepareCommit
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT, 20));
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT, 20));
        // Phase PostInvocation
        testSubject.onPostInvocation(fixture.createAsyncHandler(POST_INVOCATION, 25));
        testSubject.onPostInvocation(fixture.createAsyncHandler(POST_INVOCATION, 25));
        // Phase Invocation
        testSubject.onInvocation(fixture.createAsyncHandler(INVOCATION, 30));
        testSubject.onInvocation(fixture.createAsyncHandler(INVOCATION, 30));
        // Phase PreInvocation
        testSubject.onPreInvocation(fixture.createAsyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION, 35));
        testSubject.onPreInvocation(fixture.createAsyncHandler(ProcessingLifecycle.Phase.PRE_INVOCATION, 35));

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
    }

    @Test
    void asynchronousActionsRegisteredByShufflingThePhasesAreExecutedInTheIntendedPhaseOrder() throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        Random random = ThreadLocalRandom.current();
        List<ProcessingLifecycle.Phase> phases = Arrays.stream(values()).collect(Collectors.toList());
        Collections.shuffle(phases, random);
        for (ProcessingLifecycle.Phase phase : phases) {
            testSubject.on(phase, fixture.createAsyncHandler(phase, random.nextInt(100)));
            testSubject.on(phase, fixture.createAsyncHandler(phase, random.nextInt(100)));
        }

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
    }

    @Test
    void rollbackRegisteredActionsAreNotInvokedWhenEverythingSucceeds() throws Exception {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.runOnRollback(context -> invoked.set(true));

        execute(testSubject).get(1, TimeUnit.SECONDS);

        fixture.assertCompleteExecution();
        assertFalse(invoked.get());
    }

    @Test
    void rollbackRegisteredActionsAreInvokedWhenAnActionFailsInAnyPhaseExceptForCompleted() {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.onInvocation(fixture.createExceptionThrower(INVOCATION));
        testSubject.runOnRollback(context -> invoked.set(true));

        CompletableFuture<?> result = execute(testSubject);
        assertTrue(result.isCompletedExceptionally());

        fixture.assertCompleteExecution();
        fixture.assertErrorHappeningInPhase(INVOCATION);
        assertTrue(invoked.get());
    }

    @Test
    void phasesOccurringAfterTheFailingPhaseAreNotExecuted() {
        PL testSubject = createTestSubject();
        ProcessingLifecycleFixture fixture = new ProcessingLifecycleFixture();

        testSubject.onInvocation(fixture.createExceptionThrower(INVOCATION));
        testSubject.onPostInvocation(fixture.createSyncHandler(POST_INVOCATION));
        testSubject.onPrepareCommit(fixture.createAsyncHandler(PREPARE_COMMIT));
        testSubject.onCommit(fixture.createSyncHandler(COMMIT));
        testSubject.onAfterCommit(fixture.createAsyncHandler(AFTER_COMMIT));

        CompletableFuture<?> result = execute(testSubject);
        assertTrue(result.isCompletedExceptionally());

        fixture.assertCompleteExecution();
        fixture.assertErrorHappeningInPhase(INVOCATION);
        fixture.assertNotInvoked(POST_INVOCATION);
        fixture.assertNotInvoked(PREPARE_COMMIT);
        fixture.assertNotInvoked(COMMIT);
        fixture.assertNotInvoked(AFTER_COMMIT);
    }

    // TODO draft up test cases test

    /**
     * Test fixture intended for validating the invocation of actions registered in
     * {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase phases} within the
     * {@link ProcessingLifecycle}.
     */
    private static class ProcessingLifecycleFixture {

        private final Map<ProcessingLifecycle.Phase, Integer> phaseToHandlerCount = new ConcurrentHashMap<>();
        private final List<ExecutionCompleted> completedExecutions = new CopyOnWriteArrayList<>();

        private SyncHandler createSyncHandler(ProcessingLifecycle.Phase phase) {
            incrementCounter(phase);
            return new SyncHandler(phase, completedExecutions);
        }

        private AsyncHandler createAsyncHandler(ProcessingLifecycle.Phase phase) {
            return createAsyncHandler(phase, 100);
        }

        private AsyncHandler createAsyncHandler(ProcessingLifecycle.Phase phase, int sleepMilliseconds) {
            incrementCounter(phase);
            return new AsyncHandler(phase, completedExecutions, sleepMilliseconds);
        }

        private ExceptionThrower createExceptionThrower(ProcessingLifecycle.Phase phase) {
            return createExceptionThrower(phase, new IllegalStateException("Some exception"));
        }

        private ExceptionThrower createExceptionThrower(ProcessingLifecycle.Phase phase, Throwable throwable) {
            incrementCounter(phase);
            return new ExceptionThrower(throwable, phase, completedExecutions);
        }

        private void incrementCounter(ProcessingLifecycle.Phase phase) {
            phaseToHandlerCount.compute(phase, (p, currentCount) -> currentCount == null ? 1 : currentCount + 1);
        }

        public void assertCompleteExecution() {
            long nonRollbackHandlerCount = filteredHandlerCount(entry -> entry.getKey() != ROLLBACK);
            assertAmountOfHandlers(nonRollbackHandlerCount);
            assertAmountOfExecutedHandlers(nonRollbackHandlerCount);
            assertExecutionOrder();
        }

        public void assertErrorHappeningInPhase(ProcessingLifecycle.Phase phase) {
            for (int i = 0; i < phase.ordinal(); i++) {
                ProcessingLifecycle.Phase p = ProcessingLifecycle.Phase.values()[i];
                assertInvoked(p);
            }

            for (int i = phase.ordinal() + 1; i < ROLLBACK.ordinal(); i++) {
                ProcessingLifecycle.Phase p = ProcessingLifecycle.Phase.values()[i];
                assertInvoked(p);
            }

            // assert everything before given phase is invoked
            // assert everything after give phase but rollback and complete is not invoked
            assertInvoked(ROLLBACK);
            assertInvoked(COMPLETED);
        }

        private void assertInvoked(ProcessingLifecycle.Phase phase) {
            int expected = phaseToHandlerCount.get(phase) == null ? 0 : phaseToHandlerCount.get(phase);
            assertEquals(expected, countExecuted(phase));
        }

        private void assertNotInvoked(ProcessingLifecycle.Phase phase) {
            assertEquals(0, countExecuted(phase));
        }

        private int countExecuted(ProcessingLifecycle.Phase phase) {
            return (int) completedExecutions.stream()
                                            .filter(executionCompleted -> executionCompleted.phase().equals(phase))
                                            .count();
        }

        private void assertAmountOfHandlers(long nonRollbackHandlerCount) {
            assertEquals(nonRollbackHandlerCount, completedExecutions.size());
        }

        private long filteredHandlerCount(Predicate<Map.Entry<ProcessingLifecycle.Phase, Integer>> phaseFilter) {
            return phaseToHandlerCount.entrySet()
                                      .stream()
                                      .filter(phaseFilter)
                                      .map(Map.Entry::getValue)
                                      .reduce(Integer::sum)
                                      .orElse(0);
        }

        private void assertAmountOfExecutedHandlers(long nonRollbackHandlerCount) {
            Set<String> ids = completedExecutions.stream()
                                                 .map(ExecutionCompleted::id)
                                                 .collect(Collectors.toSet());
            assertEquals(nonRollbackHandlerCount, ids.size());
        }

        private void assertExecutionOrder() {
            ProcessingLifecycle.Phase prevPhase = ProcessingLifecycle.Phase.PRE_INVOCATION;
            for (ExecutionCompleted executionCompleted : completedExecutions) {
                assertTrue(executionCompleted.phase.ordinal() >= prevPhase.ordinal());
                prevPhase = executionCompleted.phase;
            }
        }
    }

    private record ExecutionCompleted(
            String id,
            ProcessingLifecycle.Phase phase
    ) {

    }

    private static class SyncHandler extends Handler {

        private SyncHandler(ProcessingLifecycle.Phase phase, List<ExecutionCompleted> completedExecutions) {
            super(phase, completedExecutions);
        }

        @Override
        public CompletableFuture<?> apply(ProcessingContext processingContext) {
            completedExecutions.add(new ExecutionCompleted(id, phase));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class AsyncHandler extends Handler {

        private final int sleepMs;

        private AsyncHandler(ProcessingLifecycle.Phase phase,
                             List<ExecutionCompleted> completedExecutions,
                             int sleepMs) {
            super(phase, completedExecutions);
            this.sleepMs = sleepMs;
        }

        @Override
        public CompletableFuture<?> apply(ProcessingContext processingContext) {
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(sleepMs);
                    completedExecutions.add(new ExecutionCompleted(id, phase));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static class ExceptionThrower extends Handler {

        private final Throwable throwable;

        public ExceptionThrower(Throwable throwable,
                                ProcessingLifecycle.Phase phase,
                                List<ExecutionCompleted> completedExecutions) {
            super(phase, completedExecutions);
            this.throwable = throwable;
        }

        @Override
        public CompletableFuture<?> apply(ProcessingContext processingContext) {
            completedExecutions.add(new ExecutionCompleted(id, phase));
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private static abstract class Handler implements Function<ProcessingContext, CompletableFuture<?>> {

        final String id = UUID.randomUUID().toString();
        final ProcessingLifecycle.Phase phase;
        final List<ExecutionCompleted> completedExecutions;

        private Handler(ProcessingLifecycle.Phase phase, List<ExecutionCompleted> completedExecutions) {
            this.phase = phase;
            this.completedExecutions = completedExecutions;
        }
    }
}




