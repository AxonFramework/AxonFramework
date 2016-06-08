/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.MockException;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.*;
import static org.axonframework.messaging.unitofwork.UnitOfWork.Phase.*;

/**
 * @author Rene de Waele
 */
public class BatchingUnitOfWorkTest {

    private List<PhaseTransition> transitions;
    private BatchingUnitOfWork<?> subject;

    @Before
    public void setUp() {
        transitions = new ArrayList<>();
    }

    @Test
    public void testExecuteTask() throws Exception {
        List<Message<?>> messages = Arrays.asList(toMessage(0), toMessage(1), toMessage(2));
        subject = new BatchingUnitOfWork<>(messages);
        subject.executeWithResult(() -> {
            registerListeners(subject);
            return resultFor(subject.getMessage());
        });
        validatePhaseTransitions(Arrays.asList(PREPARE_COMMIT, COMMIT, AFTER_COMMIT, CLEANUP), messages);
        Map<Message<?>, ExecutionResult> expectedResults = new HashMap<>();
        messages.forEach(m -> expectedResults.put(m, new ExecutionResult(resultFor(m))));
        assertEquals(expectedResults, subject.getExecutionResults());
    }

    @Test
    public void testRollback() throws Exception {
        List<Message<?>> messages = Arrays.asList(toMessage(0), toMessage(1), toMessage(2));
        subject = new BatchingUnitOfWork<>(messages);
        MockException e = new MockException();
        try {
            subject.executeWithResult(() -> {
                registerListeners(subject);
                if (subject.getMessage().getPayload().equals(1)) {
                    throw e;
                }
                return resultFor(subject.getMessage());
            });
        } catch (Exception ignored) {
        }
        validatePhaseTransitions(Arrays.asList(ROLLBACK, CLEANUP), messages.subList(0, 2));
        Map<Message<?>, ExecutionResult> expectedResult = new HashMap<>();
        messages.forEach(m -> expectedResult.put(m, new ExecutionResult(e)));
        assertEquals(expectedResult, subject.getExecutionResults());
    }

    @Test
    public void testSuppressedExceptionOnRollback() throws Exception {
        List<Message<?>> messages = Arrays.asList(toMessage(0), toMessage(1), toMessage(2));
        subject = new BatchingUnitOfWork<>(messages);
        MockException taskException = new MockException("task exception");
        MockException commitException = new MockException("commit exception");
        try {
            subject.executeWithResult(() -> {
                registerListeners(subject);
                if (subject.getMessage().getPayload().equals(2)) {
                    subject.addHandler(PREPARE_COMMIT, u -> {
                        throw commitException;
                    });
                    throw taskException;
                }
                return resultFor(subject.getMessage());
            }, e -> false);
        } catch (Exception ignored) {
        }
        validatePhaseTransitions(Arrays.asList(PREPARE_COMMIT, ROLLBACK, CLEANUP), messages);
        Map<Message<?>, ExecutionResult> expectedResult = new HashMap<>();
        expectedResult.put(messages.get(0), new ExecutionResult(commitException));
        expectedResult.put(messages.get(1), new ExecutionResult(commitException));
        expectedResult.put(messages.get(2), new ExecutionResult(taskException));
        assertEquals(expectedResult, subject.getExecutionResults());
        assertSame(commitException, taskException.getSuppressed()[0]);
    }

    private void registerListeners(UnitOfWork<?> unitOfWork) {
        unitOfWork.onPrepareCommit(u -> transitions.add(new PhaseTransition(u.getMessage(), PREPARE_COMMIT)));
        unitOfWork.onCommit(u -> transitions.add(new PhaseTransition(u.getMessage(), COMMIT)));
        unitOfWork.afterCommit(u -> transitions.add(new PhaseTransition(u.getMessage(), AFTER_COMMIT)));
        unitOfWork.onRollback(u -> transitions.add(new PhaseTransition(u.getMessage(), ROLLBACK)));
        unitOfWork.onCleanup(u -> transitions.add(new PhaseTransition(u.getMessage(), CLEANUP)));
    }

    private static Message<?> toMessage(Object payload) {
        return new GenericMessage<>(payload);
    }

    public static Object resultFor(Message<?> message) {
        return "Result for: " + message.getPayload();
    }

    private void validatePhaseTransitions(List<UnitOfWork.Phase> phases, List<Message<?>> messages) {
        Iterator<PhaseTransition> iterator = transitions.iterator();
        for (UnitOfWork.Phase phase : phases) {
            Iterator<Message<?>> messageIterator = phase.isReverseCallbackOrder()
                    ? new LinkedList<>(messages).descendingIterator() : messages.iterator();
            messageIterator.forEachRemaining(message -> {
                PhaseTransition expected = new PhaseTransition(message, phase);
                assertTrue(iterator.hasNext());
                PhaseTransition actual = iterator.next();
                assertEquals(expected, actual);
            });
        }
    }

    private static class PhaseTransition {

        private final UnitOfWork.Phase phase;
        private final Message<?> message;

        public PhaseTransition(Message<?> message, UnitOfWork.Phase phase) {
            this.message = message;
            this.phase = phase;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PhaseTransition that = (PhaseTransition) o;
            return phase == that.phase &&
                    Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(phase, message);
        }

        @Override
        public String toString() {
            return phase + " -> " + message.getPayload();
        }
    }
}