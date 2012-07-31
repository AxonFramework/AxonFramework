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

package org.axonframework.saga.annotation;

import org.junit.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AsyncSagaCreationElectorTest {

    private AsyncSagaCreationElector testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AsyncSagaCreationElector();
    }

    @Test
    public void testAllElectFalse() throws InterruptedException {
        ElectionResult e1 = elect(false, 4, false);
        ElectionResult e2 = elect(false, 4, false);
        ElectionResult e3 = elect(false, 4, true);
        ElectionResult e4 = elect(false, 4, false);

        e1.join(1000);
        e2.join(1000);
        e3.join(1000);
        e4.join(1000);
        assertTrue("Thread 1 seems to be hanging", e1.isFinished());
        assertTrue("Thread 2 seems to be hanging", e2.isFinished());
        assertTrue("Thread 3 seems to be hanging", e3.isFinished());
        assertTrue("Thread 4 seems to be hanging", e4.isFinished());

        assertFalse(e1.isElected());
        assertFalse(e2.isElected());
        assertTrue(e3.isElected());
        assertFalse(e4.isElected());
    }

    @Test
    public void testLastElectsTrue() throws InterruptedException {
        ElectionResult e1 = elect(false, 4, false);
        e1.join(100);
        assertTrue("e1 should NOT be waiting for other votes", e1.isFinished());
        ElectionResult e2 = elect(false, 4, true);
        e2.join(100);
        assertFalse("e2 should be waiting for other votes", e2.hasState(Thread.State.BLOCKED));
        ElectionResult e3 = elect(false, 4, false);
        ElectionResult e4 = elect(true, 4, false);

        e1.join(1000);
        e2.join(1000);
        e3.join(1000);
        e4.join(1000);
        assertTrue("Thread 1 seems to be hanging", e1.isFinished());
        assertTrue("Thread 2 seems to be hanging", e2.isFinished());
        assertTrue("Thread 3 seems to be hanging", e3.isFinished());
        assertTrue("Thread 4 seems to be hanging", e4.isFinished());

        assertFalse(e1.isElected());
        assertFalse(e2.isElected());
        assertFalse(e3.isElected());
        assertFalse(e4.isElected());
    }

    private ElectionResult elect(final boolean didInvocation, final int electorCount,
                                 final boolean isSagaOwner) {
        final AtomicBoolean elected = new AtomicBoolean();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                elected.set(testSubject.waitForSagaCreationVote(didInvocation, electorCount, isSagaOwner));
            }
        });
        t.start();
        return new ElectionResult(elected, t);
    }

    private class ElectionResult {
        private final AtomicBoolean elected;
        private final Thread t;

        public ElectionResult(AtomicBoolean elected, Thread t) {
            this.elected = elected;
            this.t = t;
        }

        public void join(int millis) throws InterruptedException {
            t.join(millis);
        }

        public boolean isElected() {
            return elected.get();
        }

        public boolean hasState(Thread.State expected) {
            return t.getState() == expected;
        }

        public boolean isFinished() {
            return !t.isAlive();
        }
    }
}
