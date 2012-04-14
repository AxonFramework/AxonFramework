/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.commandhandling.callbacks;

import org.junit.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class FutureCallbackTest {

    private volatile FutureCallback<Object> testSubject;
    private volatile Object resultFromParallelThread;
    private static final int THREAD_JOIN_TIMEOUT = 1000;

    @Before
    public void setUp() throws Exception {
        testSubject = new FutureCallback<Object>();
    }

    @Test
    public void testOnSuccess() throws InterruptedException {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resultFromParallelThread = testSubject.get();
                } catch (Exception e) {
                    resultFromParallelThread = e;
                }
            }
        });
        t.start();
        assertTrue(t.isAlive());
        testSubject.onSuccess("Hello world");
        t.join(THREAD_JOIN_TIMEOUT);
        assertEquals("Hello world", resultFromParallelThread);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testOnFailure() throws InterruptedException {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resultFromParallelThread = testSubject.get();
                } catch (Exception e) {
                    resultFromParallelThread = e;
                }
            }
        });
        t.start();
        assertTrue(t.isAlive());
        RuntimeException exception = new RuntimeException("Mock");
        testSubject.onFailure(exception);
        t.join(THREAD_JOIN_TIMEOUT);
        assertTrue(resultFromParallelThread instanceof ExecutionException);
        assertEquals(exception, ((Exception) resultFromParallelThread).getCause());
    }

    @Test
    public void testOnSuccessForLimitedTime_Timeout() throws InterruptedException {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resultFromParallelThread = testSubject.get(1, TimeUnit.NANOSECONDS);
                } catch (Exception e) {
                    resultFromParallelThread = e;
                }
            }
        });
        t.start();
        t.join(1000);
        testSubject.onSuccess("Hello world");
        assertTrue(resultFromParallelThread instanceof TimeoutException);
    }

    @Test
    public void testOnSuccessForLimitedTime_InTime() throws InterruptedException {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resultFromParallelThread = testSubject.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    resultFromParallelThread = e;
                }
            }
        });
        t.start();
        assertTrue(t.isAlive());
        assertFalse(testSubject.isDone());
        testSubject.onSuccess("Hello world");
        assertTrue(testSubject.isDone());
        t.join(THREAD_JOIN_TIMEOUT);
        assertEquals("Hello world", resultFromParallelThread);
    }

    @Test
    public void testUnableToCancel() {
        assertFalse(testSubject.cancel(true));
        assertFalse(testSubject.cancel(false));
        assertFalse(testSubject.isCancelled());
    }
}
