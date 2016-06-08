/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.MockException;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AbstractUnitOfWorkTest {

    private List<PhaseTransition> phaseTransitions;
    private UnitOfWork<?> subject;

    @SuppressWarnings({"unchecked", "deprecation"})
    @Before
    public void setUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        subject = spy(new DefaultUnitOfWork(new GenericEventMessage<>("Input 1")) {
            @Override
            public String toString() {
                return "unitOfWork";
            }
        });
        phaseTransitions = new ArrayList<>();
        registerListeners(subject);
    }

    private void registerListeners(UnitOfWork<?> unitOfWork) {
        unitOfWork.onPrepareCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.PREPARE_COMMIT)));
        unitOfWork.onCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.COMMIT)));
        unitOfWork.afterCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.AFTER_COMMIT)));
        unitOfWork.onRollback(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.ROLLBACK)));
        unitOfWork.onCleanup(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.CLEANUP)));
    }

    @After
    public void tearDown() {
        assertFalse("A UnitOfWork was not properly cleared", CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testHandlersForCurrentPhaseAreExecuted() {
        AtomicBoolean prepareCommit = new AtomicBoolean();
        AtomicBoolean commit = new AtomicBoolean();
        AtomicBoolean afterCommit = new AtomicBoolean();
        AtomicBoolean cleanup = new AtomicBoolean();
        subject.onPrepareCommit(u -> subject.onPrepareCommit(i -> prepareCommit.set(true)));
        subject.onCommit(u -> subject.onCommit(i -> commit.set(true)));
        subject.afterCommit(u -> subject.afterCommit(i -> afterCommit.set(true)));
        subject.onCleanup(u -> subject.onCleanup(i -> cleanup.set(true)));

        subject.start();
        subject.commit();

        assertTrue(prepareCommit.get());
        assertTrue(commit.get());
        assertTrue(afterCommit.get());
        assertTrue(cleanup.get());
    }

    @Test
    public void testExecuteTask() {
        Runnable task = mock(Runnable.class);
        doNothing().when(task).run();
        subject.execute(task);
        InOrder inOrder = inOrder(task, subject);
        inOrder.verify(subject).start();
        inOrder.verify(task).run();
        inOrder.verify(subject).commit();
        assertFalse(subject.isActive());
    }

    @Test
    public void testExecuteFailingTask() {
        Runnable task = mock(Runnable.class);
        MockException mockException = new MockException();
        doThrow(mockException).when(task).run();
        try {
            subject.execute(task);
        } catch (MockException e) {
            InOrder inOrder = inOrder(task, subject);
            inOrder.verify(subject).start();
            inOrder.verify(task).run();
            inOrder.verify(subject).rollback(e);
            assertNotNull(subject.getExecutionResult());
            assertSame(mockException, subject.getExecutionResult().getExceptionResult());
            return;
        }
        throw new AssertionError();
    }

    @Test
    public void testExecuteTaskWithResult() throws Exception {
        Object taskResult = new Object();
        Callable<?> task = mock(Callable.class);
        when(task.call()).thenReturn(taskResult);
        Object result = subject.executeWithResult(task);
        InOrder inOrder = inOrder(task, subject);
        inOrder.verify(subject).start();
        inOrder.verify(task).call();
        inOrder.verify(subject).commit();
        assertFalse(subject.isActive());
        assertSame(taskResult, result);
        assertNotNull(subject.getExecutionResult());
        assertSame(taskResult, subject.getExecutionResult().getResult());
    }

    private static class PhaseTransition {

        private final UnitOfWork.Phase phase;
        private final UnitOfWork<?> unitOfWork;

        public PhaseTransition(UnitOfWork<?> unitOfWork, UnitOfWork.Phase phase) {
            this.unitOfWork = unitOfWork;
            this.phase = phase;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PhaseTransition that = (PhaseTransition) o;
            return Objects.equals(phase, that.phase) &&
                    Objects.equals(unitOfWork, that.unitOfWork);
        }

        @Override
        public int hashCode() {
            return Objects.hash(phase, unitOfWork);
        }

        @Override
        public String toString() {
            return unitOfWork + " " + phase;
        }
    }
}
