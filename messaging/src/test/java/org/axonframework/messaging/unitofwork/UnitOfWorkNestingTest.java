/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.utils.MockException;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class UnitOfWorkNestingTest {

    private List<PhaseTransition> phaseTransitions = new ArrayList<>();
    private UnitOfWork<?> outer;
    private UnitOfWork<?> middle;
    private UnitOfWork<?> inner;

    @SuppressWarnings({"unchecked", "deprecation"})
    @Before
    public void setUp() {
        phaseTransitions.clear();
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }

        outer = new DefaultUnitOfWork(new GenericEventMessage<>("Input 1")) {
            @Override
            public String toString() {
                return "outer";
            }
        };
        middle = new DefaultUnitOfWork(new GenericEventMessage<Object>("Input middle")) {
            @Override
            public String toString() {
                return "middle";
            }
        };
        inner = new DefaultUnitOfWork(new GenericEventMessage<>("Input 2")) {
            @Override
            public String toString() {
                return "inner";
            }
        };
        registerListeners(outer);
        registerListeners(middle);
        registerListeners(inner);
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
    public void testInnerUnitOfWorkNotifiedOfOuterCommitFailure() {
        outer.onPrepareCommit(u -> {
            inner.start();
            inner.commit();
        });
        outer.onCommit(u -> {
            throw new MockException();
        });
        outer.onCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.COMMIT, "x")));
        outer.start();
        try {
            outer.commit();
        } catch (MockException e) {
            //ok
        }

        assertFalse("The UnitOfWork hasn't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT, "x"),
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    @Test
    public void testInnerUnitOfWorkNotifiedOfOuterPrepareCommitFailure() {
        outer.onPrepareCommit(u -> {
            inner.start();
            inner.commit();
        });
        outer.onPrepareCommit(u -> {
            throw new MockException();
        });
        outer.start();
        try {
            outer.commit();
        } catch (MockException e) {
            //ok
        }

        assertFalse("The UnitOfWork hasn't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    @Test
    public void testInnerUnitOfWorkNotifiedOfOuterCommit() {
        outer.onPrepareCommit(u -> {
            inner.start();
            inner.commit();
        });
        outer.start();
        outer.commit();

        assertFalse("The UnitOfWork hasn't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    @Test
    public void testInnerUnitRollbackDoesNotAffectOuterCommit() {
        outer.onPrepareCommit(u -> {
            inner.start();
            inner.rollback(new MockException());
        });
        outer.start();
        outer.commit();

        assertFalse("The UnitOfWork hasn't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    @Test
    public void testRollbackOfMiddleUnitOfWorkRollsBackInner() {
        outer.onPrepareCommit(u -> {
            middle.start();
            inner.start();
            inner.commit();
            middle.rollback();
        });
        outer.start();
        outer.commit();

        assertTrue("The middle UnitOfWork hasn't been correctly marked as rolled back", middle.isRolledBack());
        assertTrue("The inner UnitOfWork hasn't been correctly marked as rolled back", inner.isRolledBack());
        assertFalse("The out UnitOfWork has been incorrectly marked as rolled back", outer.isRolledBack());
        assertFalse("The UnitOfWork hasn't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT),
                                   // important that the inner has been given a rollback signal
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(middle, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(middle, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    @Test
    public void testInnerUnitCommitFailureDoesNotAffectOuterCommit() {
        outer.onPrepareCommit(u -> {
            inner.start();
            inner.onCommit(uow -> {
                throw new MockException();
            });
            // commits are invoked in reverse order, so we expect to see this one, but not the previously registered handler
            inner.onCommit(uow -> phaseTransitions.add(new PhaseTransition(inner, UnitOfWork.Phase.COMMIT, "x")));
            try {
                inner.commit();
            } catch (MockException e) {
                //ok
            }
        });
        outer.start();
        outer.commit();

        assertFalse("The UnitOfWork hasn't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT, "x"),
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    private static class PhaseTransition {

        private final UnitOfWork.Phase phase;
        private final UnitOfWork<?> unitOfWork;
        private final String id;

        public PhaseTransition(UnitOfWork<?> unitOfWork, UnitOfWork.Phase phase) {
            this(unitOfWork, phase, "");
        }

        public PhaseTransition(UnitOfWork<?> unitOfWork, UnitOfWork.Phase phase, String id) {
            this.unitOfWork = unitOfWork;
            this.phase = phase;
            this.id = id.length() > 0 ? " " + id : id;
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
                    Objects.equals(unitOfWork, that.unitOfWork) &&
                    Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(phase, unitOfWork, id);
        }

        @Override
        public String toString() {
            return unitOfWork + " " + phase + id;
        }
    }
}
