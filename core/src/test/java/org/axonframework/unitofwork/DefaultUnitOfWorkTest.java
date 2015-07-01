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

package org.axonframework.unitofwork;

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.testutils.MockException;
import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class DefaultUnitOfWorkTest {

    private List<PhaseTransition> phaseTransitions = new ArrayList<>();
    private UnitOfWork outer;
    private UnitOfWork inner;

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
        inner = new DefaultUnitOfWork(new GenericEventMessage<>("Input 2")) {
            @Override
            public String toString() {
                return "inner";
            }
        };
        registerListeners(outer);
        registerListeners(inner);
    }

    private void registerListeners(UnitOfWork unitOfWork) {
        unitOfWork.onPrepareCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.PREPARE_COMMIT)));
        unitOfWork.onCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.COMMIT)));
        unitOfWork.afterCommit(u -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.AFTER_COMMIT)));
        unitOfWork.onRollback((u, e) -> phaseTransitions.add(new PhaseTransition(u, UnitOfWork.Phase.ROLLBACK)));
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
        outer.start();
        outer.commit();

        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
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
        outer.commit();

        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
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

        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
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

        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    @Test
    public void testInnerUnitCommitFailureDoesNotAffectOuterCommit() {
        outer.onPrepareCommit(u -> {
            inner.start();
            inner.onCommit(uow -> { throw new MockException(); });
            inner.commit();
        });
        outer.start();
        outer.commit();

        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
        assertEquals(Arrays.asList(new PhaseTransition(outer, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.PREPARE_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.ROLLBACK),
                                   new PhaseTransition(outer, UnitOfWork.Phase.COMMIT),
                                   new PhaseTransition(outer, UnitOfWork.Phase.AFTER_COMMIT),
                                   new PhaseTransition(inner, UnitOfWork.Phase.CLEANUP),
                                   new PhaseTransition(outer, UnitOfWork.Phase.CLEANUP)
        ), phaseTransitions);
    }

    private static class PhaseTransition {

        private final UnitOfWork.Phase phase;
        private final UnitOfWork unitOfWork;

        public PhaseTransition(UnitOfWork unitOfWork, UnitOfWork.Phase phase) {
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
