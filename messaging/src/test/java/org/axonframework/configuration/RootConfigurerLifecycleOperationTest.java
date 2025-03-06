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

package org.axonframework.configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.junit.jupiter.api.*;
import org.mockito.*;

/**
 * Test class validating the workings of the lifecycle operations registered and invoked on the {@link RootConfigurer},
 * {@link RootConfiguration} and the {@link DefaultRootConfigurer} implementation.
 * <p>
 * As such, operations like the {@link LifecycleSupportingConfiguration#onStart(int, LifecycleHandler)},
 * {@link LifecycleSupportingConfiguration#onShutdown(int, LifecycleHandler)}, {@link RootConfigurer#start()},
 * {@link RootConfiguration#start()} and {@link RootConfiguration#shutdown()} will be tested.
 *
 * @author Steven van Beelen
 */
class RootConfigurerLifecycleOperationTest {

    private static final String START_FAILURE_EXCEPTION_MESSAGE = "some start failure";

    @Test
    void startLifecycleHandlersAreInvokedInAscendingPhaseOrder() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, phaseOneHandler::start);
        testSubject.onStart(10, phaseTenHandler::start);
        testSubject.onStart(9001, phaseOverNineThousandHandler::start);

        testSubject.start();

        InOrder lifecycleOrder =
                inOrder(phaseZeroHandler, phaseOneHandler, phaseTenHandler, phaseOverNineThousandHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(phaseOneHandler).start();
        lifecycleOrder.verify(phaseTenHandler).start();
        lifecycleOrder.verify(phaseOverNineThousandHandler).start();
    }

    @Test
    void startLifecycleHandlerConfiguredThroughConfigurerAreInvokedInAscendingPhaseOrder() {
        RootConfigurer testSubject = RootConfigurer.defaultConfigurer();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(9001, phaseOverNineThousandHandler::start);
        testSubject.onStart(10, phaseTenHandler::start);
        testSubject.onStart(1, phaseOneHandler::start);
        testSubject.onStart(0, phaseZeroHandler::start);

        testSubject.start();

        InOrder lifecycleOrder =
                inOrder(phaseZeroHandler, phaseOneHandler, phaseTenHandler, phaseOverNineThousandHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(phaseOneHandler).start();
        lifecycleOrder.verify(phaseTenHandler).start();
        lifecycleOrder.verify(phaseOverNineThousandHandler).start();
    }

    @Test
    void startLifecycleHandlersWillOnlyProceedToFollowingPhaseAfterCurrentPhaseIsFinalized()
            throws InterruptedException {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();
        // Create a lock for the slow handler and lock it immediately, to spoof the handler's slow/long process
        ReentrantLock slowHandlerLock = new ReentrantLock();
        slowHandlerLock.lock();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance slowPhaseZeroHandler = spy(new LifecycleManagedInstance(slowHandlerLock));
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(0, slowPhaseZeroHandler::slowStart);
        testSubject.onStart(1, phaseOneHandler::start);

        // Start in a different thread as the 'slowPhaseZeroHandler' will otherwise not lock
        Thread startThread = new Thread(testSubject::start);
        startThread.start();
        // Sleep to give the start thread some time to execute
        Thread.sleep(250);

        try {
            // Phase one has not started yet, as the method has not been invoked yet.
            verify(phaseOneHandler, never()).start();
            // The phase zero handlers on the other hand have been invoked
            verify(phaseZeroHandler).start();
            verify(slowPhaseZeroHandler).slowStart();
        } finally {
            slowHandlerLock.unlock();
        }

        // Wait until the start thread is finished prior to validating the order.
        startThread.join();
        verify(phaseOneHandler).start();

        InOrder lifecycleOrder = inOrder(phaseZeroHandler, slowPhaseZeroHandler, phaseOneHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(slowPhaseZeroHandler).slowStart();
        lifecycleOrder.verify(phaseOneHandler).start();
    }

    @Test
    void outOfOrderAddedStartHandlerHasPrecedenceOverSubsequentHandlers() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandlerAdder = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance addedPhaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, phaseOneHandler::start);
        testSubject.onStart(1, () -> phaseOneHandlerAdder.addLifecycleHandler(
                LifecycleSupportingConfiguration::onStart, testSubject, 0, addedPhaseZeroHandler::start
        ));
        testSubject.onStart(2, phaseTwoHandler::start);

        testSubject.start();

        InOrder lifecycleOrder = inOrder(
                phaseZeroHandler, phaseOneHandler, phaseOneHandlerAdder, addedPhaseZeroHandler, phaseTwoHandler
        );
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(phaseOneHandler).start();
        lifecycleOrder.verify(phaseOneHandlerAdder).addLifecycleHandler(any(), eq(testSubject), eq(0), any());
        lifecycleOrder.verify(addedPhaseZeroHandler).start();
        lifecycleOrder.verify(phaseTwoHandler).start();
    }

    @Test
    void outOfOrderAddedShutdownHandlerDuringStartUpIsNotCalledImmediately() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandlerAdder = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance addedPhaseTwoShutdownHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, phaseOneHandler::start);
        testSubject.onStart(1, () -> phaseOneHandlerAdder.addLifecycleHandler(
                LifecycleSupportingConfiguration::onShutdown, testSubject, 2, addedPhaseTwoShutdownHandler::shutdown
        ));
        testSubject.onStart(2, phaseTwoHandler::start);

        testSubject.start();

        InOrder lifecycleOrder = inOrder(phaseZeroHandler, phaseOneHandler, phaseOneHandlerAdder, phaseTwoHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(phaseOneHandler).start();
        lifecycleOrder.verify(phaseOneHandlerAdder).addLifecycleHandler(any(), eq(testSubject), eq(2), any());
        lifecycleOrder.verify(phaseTwoHandler).start();

        verifyNoInteractions(addedPhaseTwoShutdownHandler);
    }

    @Test
    void shutdownLifecycleHandlersAreInvokedInDescendingPhaseOrder() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

        testSubject.onShutdown(9001, phaseOverNineThousandHandler::shutdown);
        testSubject.onShutdown(10, phaseTenHandler::shutdown);
        testSubject.onShutdown(1, phaseOneHandler::shutdown);
        testSubject.onShutdown(0, phaseZeroHandler::shutdown);
        testSubject.start();

        testSubject.shutdown();

        InOrder lifecycleOrder =
                inOrder(phaseOverNineThousandHandler, phaseTenHandler, phaseOneHandler, phaseZeroHandler);
        lifecycleOrder.verify(phaseOverNineThousandHandler).shutdown();
        lifecycleOrder.verify(phaseTenHandler).shutdown();
        lifecycleOrder.verify(phaseOneHandler).shutdown();
        lifecycleOrder.verify(phaseZeroHandler).shutdown();
    }

    @Test
    void shutdownLifecycleHandlersConfiguredThroughConfigurerAreInvokedInDescendingPhaseOrder() {
        RootConfigurer testSubject = RootConfigurer.defaultConfigurer();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

        testSubject.onShutdown(0, phaseZeroHandler::shutdown);
        testSubject.onShutdown(1, phaseOneHandler::shutdown);
        testSubject.onShutdown(10, phaseTenHandler::shutdown);
        testSubject.onShutdown(9001, phaseOverNineThousandHandler::shutdown);
        RootConfiguration config = testSubject.start();

        config.shutdown();

        InOrder lifecycleOrder =
                inOrder(phaseOverNineThousandHandler, phaseTenHandler, phaseOneHandler, phaseZeroHandler);
        lifecycleOrder.verify(phaseOverNineThousandHandler).shutdown();
        lifecycleOrder.verify(phaseTenHandler).shutdown();
        lifecycleOrder.verify(phaseOneHandler).shutdown();
        lifecycleOrder.verify(phaseZeroHandler).shutdown();
    }

    @Test
    void shutdownLifecycleHandlersWillOnlyProceedToFollowingPhaseAfterCurrentPhaseIsFinalized()
            throws InterruptedException {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();
        // Create a lock for the slow handler and lock it immediately, to spoof the handler's slow/long process
        ReentrantLock slowHandlerLock = new ReentrantLock();
        slowHandlerLock.lock();

        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance slowPhaseOneHandler = spy(new LifecycleManagedInstance(slowHandlerLock));
        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());

        testSubject.onShutdown(1, phaseOneHandler::shutdown);
        testSubject.onShutdown(1, slowPhaseOneHandler::slowShutdown);
        testSubject.onShutdown(0, phaseZeroHandler::shutdown);

        testSubject.start();
        // Start in a different thread as the 'slowPhaseOneHandler' will otherwise not lock
        Thread shutdownThread = new Thread(testSubject::shutdown);
        shutdownThread.start();
        // Sleep to give the shutdown thread some time to execute
        Thread.sleep(250);

        try {
            // Phase one has not started yet, as the method has not been invoked yet.
            verify(phaseZeroHandler, never()).shutdown();
            // The phase zero handlers on the other hand have been invoked
            verify(phaseOneHandler).shutdown();
            verify(slowPhaseOneHandler).slowShutdown();
        } finally {
            slowHandlerLock.unlock();
        }

        // Wait until the shutdown-thread is finished prior to validating the order.
        shutdownThread.join();
        verify(phaseZeroHandler).shutdown();

        InOrder lifecycleOrder = inOrder(phaseOneHandler, slowPhaseOneHandler, phaseZeroHandler);
        lifecycleOrder.verify(phaseOneHandler).shutdown();
        lifecycleOrder.verify(slowPhaseOneHandler).slowShutdown();
        lifecycleOrder.verify(phaseZeroHandler).shutdown();
    }

    @Test
    void outOfOrderAddedShutdownHandlerHasPrecedenceOverSubsequentHandlers() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandlerAdder = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance addedPhaseTwoHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());

        testSubject.onShutdown(2, phaseTwoHandler::shutdown);
        testSubject.onShutdown(1, () -> phaseOneHandlerAdder.addLifecycleHandler(
                LifecycleSupportingConfiguration::onShutdown, testSubject, 2, addedPhaseTwoHandler::shutdown
        ));
        testSubject.onShutdown(1, phaseOneHandler::shutdown);
        testSubject.onShutdown(0, phaseZeroHandler::shutdown);
        testSubject.start();

        testSubject.shutdown();

        InOrder lifecycleOrder = inOrder(
                phaseTwoHandler, phaseOneHandlerAdder, addedPhaseTwoHandler, phaseOneHandler, phaseZeroHandler
        );
        lifecycleOrder.verify(phaseTwoHandler).shutdown();
        lifecycleOrder.verify(phaseOneHandlerAdder).addLifecycleHandler(any(), eq(testSubject), eq(2), any());
        lifecycleOrder.verify(addedPhaseTwoHandler).shutdown();
        lifecycleOrder.verify(phaseOneHandler).shutdown();
        lifecycleOrder.verify(phaseZeroHandler).shutdown();
    }

    /**
     * To be honest, I don't know why somebody would add a start handler during shutdown, but since the validation is
     * there through the lifecycle state I wanted to test it regardless.
     */
    @Test
    void outOfOrderAddedStartHandlerDuringShutdownIsNotCalledImmediately() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandlerAdder = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance addedPhaseOneStartHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());

        testSubject.onShutdown(2, phaseTwoHandler::shutdown);
        testSubject.onShutdown(1, () -> phaseOneHandlerAdder.addLifecycleHandler(
                LifecycleSupportingConfiguration::onStart, testSubject, 1, addedPhaseOneStartHandler::start
        ));
        testSubject.onShutdown(1, phaseOneHandler::shutdown);
        testSubject.onShutdown(0, phaseZeroHandler::shutdown);
        testSubject.start();

        testSubject.shutdown();

        InOrder lifecycleOrder = inOrder(phaseTwoHandler, phaseOneHandlerAdder, phaseOneHandler, phaseZeroHandler);
        lifecycleOrder.verify(phaseTwoHandler).shutdown();
        lifecycleOrder.verify(phaseOneHandlerAdder).addLifecycleHandler(any(), eq(testSubject), eq(1), any());
        lifecycleOrder.verify(phaseOneHandler).shutdown();
        lifecycleOrder.verify(phaseZeroHandler).shutdown();

        verifyNoInteractions(addedPhaseOneStartHandler);
    }

    @Test
    void failingStartLifecycleProceedsIntoShutdownOrderAtFailingPhase() {
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseThreeHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseFourHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, phaseOneHandler::start);
        // The LifecycleManagedInstance#failingStart() should trigger a shutdown as of phase 2
        testSubject.onStart(2, phaseTwoHandler::failingStart);
        testSubject.onStart(3, phaseThreeHandler::start);
        testSubject.onStart(4, phaseFourHandler::start);

        testSubject.onShutdown(4, phaseFourHandler::shutdown);
        testSubject.onShutdown(3, phaseThreeHandler::shutdown);
        testSubject.onShutdown(2, phaseTwoHandler::shutdown);
        testSubject.onShutdown(1, phaseOneHandler::shutdown);
        testSubject.onShutdown(0, phaseZeroHandler::shutdown);

        try {
            testSubject.start();
            fail("Expected a LifecycleHandlerInvocationException to be thrown");
        } catch (LifecycleHandlerInvocationException e) {
            assertTrue(e.getCause().getMessage().contains(START_FAILURE_EXCEPTION_MESSAGE));
        }

        InOrder lifecycleOrder =
                inOrder(phaseZeroHandler, phaseOneHandler, phaseTwoHandler, phaseThreeHandler, phaseFourHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(phaseOneHandler).start();
        lifecycleOrder.verify(phaseTwoHandler).failingStart();
        lifecycleOrder.verify(phaseFourHandler).shutdown();
        lifecycleOrder.verify(phaseThreeHandler).shutdown();
        lifecycleOrder.verify(phaseTwoHandler).shutdown();
        lifecycleOrder.verify(phaseOneHandler).shutdown();
        lifecycleOrder.verify(phaseZeroHandler).shutdown();
    }

    @Test
    void lifecycleHandlersProceedToFollowingPhaseWhenTheThreadIsInterrupted() throws InterruptedException {
        AtomicBoolean invoked = new AtomicBoolean(false);
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer().build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance(invoked));
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, phaseOneHandler::uncompletableStart);
        testSubject.onStart(2, phaseTwoHandler::start);

        // Start in a different thread to be able to interrupt the thread
        Thread startThread = new Thread(testSubject::start);
        startThread.start();
        startThread.interrupt();

        // Wait until the start thread is finished prior to validating the order.
        startThread.join();

        InOrder lifecycleOrder = inOrder(phaseZeroHandler, phaseOneHandler, phaseTwoHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(phaseOneHandler).uncompletableStart();
        lifecycleOrder.verify(phaseTwoHandler).start();
        assertFalse(invoked.get());
    }

    @Test
    void lifecycleHandlersProceedToFollowingPhaseForNeverEndingPhases() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        RootConfiguration testSubject = RootConfigurer.defaultConfigurer()
                                                      .registerLifecyclePhaseTimeout(100, TimeUnit.MILLISECONDS)
                                                      .build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance extremelySlowPhaseOneHandler = spy(new LifecycleManagedInstance(invoked));
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, extremelySlowPhaseOneHandler::uncompletableStart);
        testSubject.onStart(2, phaseTwoHandler::start);

        testSubject.start();

        InOrder lifecycleOrder = inOrder(phaseZeroHandler, extremelySlowPhaseOneHandler, phaseTwoHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(extremelySlowPhaseOneHandler).uncompletableStart();
        lifecycleOrder.verify(phaseTwoHandler).start();
        assertFalse(invoked.get());
    }

    @Test
    void registerLifecyclePhaseTimeoutWithZeroTimeoutThrowsIllegalArgumentException() {
        RootConfigurer configurerTestSubject = RootConfigurer.defaultConfigurer();

        assertThrows(
                IllegalArgumentException.class,
                () -> configurerTestSubject.registerLifecyclePhaseTimeout(0, TimeUnit.SECONDS)
        );
    }

    @Test
    void registerLifecyclePhaseTimeoutWithNegativeTimeoutThrowsIllegalArgumentException() {
        RootConfigurer configurerTestSubject = RootConfigurer.defaultConfigurer();

        assertThrows(
                IllegalArgumentException.class,
                () -> configurerTestSubject.registerLifecyclePhaseTimeout(-1, TimeUnit.SECONDS)
        );
    }

    @Test
    void registerLifecyclePhaseTimeoutWithNullTimeUnitThrowsNullPointerException() {
        RootConfigurer configurerTestSubject = RootConfigurer.defaultConfigurer();

        //noinspection DataFlowIssue
        assertThrows(
                NullPointerException.class,
                () -> configurerTestSubject.registerLifecyclePhaseTimeout(1, null)
        );
    }

    private static class LifecycleManagedInstance {

        private final ReentrantLock lock;
        private final AtomicBoolean invoked;

        private LifecycleManagedInstance() {
            this(new ReentrantLock(), new AtomicBoolean(false));
        }

        public LifecycleManagedInstance(ReentrantLock lock) {
            this(lock, new AtomicBoolean(false));
        }

        private LifecycleManagedInstance(AtomicBoolean invoked) {
            this(new ReentrantLock(), invoked);
        }

        private LifecycleManagedInstance(ReentrantLock lock, AtomicBoolean invoked) {
            this.lock = lock;
            this.invoked = invoked;
        }

        public void start() {
            // No-op
        }

        public CompletableFuture<Void> slowStart() {
            return CompletableFuture.runAsync(() -> {
                try {
                    lock.lock();
                } finally {
                    lock.unlock();
                }
            });
        }

        public CompletableFuture<Object> uncompletableStart() {
            return new CompletableFuture<>().whenComplete((r, e) -> invoked.set(true));
        }

        public void addLifecycleHandler(LifecycleRegistration lifecycleRegistration,
                                        LifecycleSupportingConfiguration config,
                                        int phase,
                                        Runnable lifecycleHandler) {
            lifecycleRegistration.registerLifecycleHandler(config, phase, lifecycleHandler);
        }

        public void shutdown() {
            // No-op
        }

        public CompletableFuture<Void> slowShutdown() {
            return CompletableFuture.runAsync(() -> {
                try {
                    lock.lock();
                } finally {
                    lock.unlock();
                }
            });
        }

        public void failingStart() {
            throw new RuntimeException(START_FAILURE_EXCEPTION_MESSAGE);
        }
    }

    @FunctionalInterface
    private interface LifecycleRegistration {

        void registerLifecycleHandler(LifecycleSupportingConfiguration configuration,
                                      int phase,
                                      Runnable lifecycleHandler);
    }
}