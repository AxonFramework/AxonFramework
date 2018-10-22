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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.utils.MockException;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class FutureCallbackTest {

    private static final CommandMessage<Object> COMMAND_MESSAGE = GenericCommandMessage.asCommandMessage("Test");
    private static final CommandResultMessage<String> COMMAND_RESPONSE_MESSAGE =
            asCommandResultMessage("Hello world");
    private volatile FutureCallback<Object, Object> testSubject;
    private volatile Object resultFromParallelThread;
    private static final int THREAD_JOIN_TIMEOUT = 1000;

    @Before
    public void setUp() {
        testSubject = new FutureCallback<>();
    }

    @Test
    public void testOnSuccess() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get();
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        assertTrue(t.isAlive());
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        t.join(THREAD_JOIN_TIMEOUT);
        assertEquals(COMMAND_RESPONSE_MESSAGE, resultFromParallelThread);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testOnFailure() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get();
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        assertTrue(t.isAlive());
        RuntimeException exception = new MockException();
        testSubject.onResult(COMMAND_MESSAGE, asCommandResultMessage(exception));
        t.join(THREAD_JOIN_TIMEOUT);
        assertTrue(resultFromParallelThread instanceof ExecutionException);
        assertEquals(exception, ((Exception) resultFromParallelThread).getCause());
    }

    @Test
    public void testOnSuccessForLimitedTime_Timeout() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get(1, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        t.join(1000);
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        assertTrue(resultFromParallelThread instanceof TimeoutException);
    }

    @Test
    public void testOnSuccessForLimitedTime_InTime() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        assertTrue(t.isAlive());
        assertFalse(testSubject.isDone());
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        assertTrue(testSubject.isDone());
        t.join(THREAD_JOIN_TIMEOUT);
        assertEquals(COMMAND_RESPONSE_MESSAGE, resultFromParallelThread);
    }
}
