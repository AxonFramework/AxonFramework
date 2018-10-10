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

package org.axonframework.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Allard Buijze
 */
public class AxonThreadFactoryTest {

    private AxonThreadFactory testSubject;

    @Test
    public void testCreateWithThreadGroupByName() {
        testSubject = new AxonThreadFactory("test");
        Thread t1 = testSubject.newThread(new NoOpRunnable());
        Thread t2 = testSubject.newThread(new NoOpRunnable());

        assertEquals("test", t1.getThreadGroup().getName());
        assertEquals("test-0", t1.getName());
        assertEquals("test-1", t2.getName());
        assertSame("Expected only a single ThreadGroup", t1.getThreadGroup(), t2.getThreadGroup());
    }

    @Test
    public void testCreateWithThreadGroupByThreadGroupInstance() {
        ThreadGroup threadGroup = new ThreadGroup("test");
        testSubject = new AxonThreadFactory(threadGroup);
        Thread t1 = testSubject.newThread(new NoOpRunnable());
        Thread t2 = testSubject.newThread(new NoOpRunnable());

        assertEquals("test", t1.getThreadGroup().getName());
        assertEquals("test-0", t1.getName());
        assertSame("Expected only a single ThreadGroup", threadGroup, t1.getThreadGroup());
        assertSame("Expected only a single ThreadGroup", threadGroup, t2.getThreadGroup());
    }

    @Test
    public void testCreateWithPriority() {
        ThreadGroup threadGroup = new ThreadGroup("test");
        testSubject = new AxonThreadFactory(Thread.MAX_PRIORITY, threadGroup);
        Thread t1 = testSubject.newThread(new NoOpRunnable());
        Thread t2 = testSubject.newThread(new NoOpRunnable());

        assertEquals("test", t1.getThreadGroup().getName());
        assertEquals(Thread.MAX_PRIORITY, t1.getPriority());
        assertSame("Expected only a single ThreadGroup", threadGroup, t1.getThreadGroup());
        assertSame("Expected only a single ThreadGroup", threadGroup, t2.getThreadGroup());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsTooHighPriority() {
        new AxonThreadFactory(Thread.MAX_PRIORITY + 1, new ThreadGroup(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsTooLowPriority() {
        new AxonThreadFactory(Thread.MIN_PRIORITY - 1, new ThreadGroup(""));
    }

    private static class NoOpRunnable implements Runnable {

        @Override
        public void run() {
        }
    }
}
