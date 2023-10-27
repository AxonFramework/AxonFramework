/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.messaging.ResultMessage;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class FutureCallbackTest {

    private static final CommandMessage<Object> COMMAND_MESSAGE = GenericCommandMessage.asCommandMessage("Test");
    private static final CommandResultMessage<String> COMMAND_RESPONSE_MESSAGE =
            asCommandResultMessage("Hello world");
    private static final int THREAD_JOIN_TIMEOUT = 1000;
    private volatile FutureCallback<Object, Object> testSubject;
    private volatile Object resultFromParallelThread;

    @BeforeEach
    void setUp() {
        testSubject = new FutureCallback<>();
    }

    @Test
    void onSuccess() throws InterruptedException {
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
    void onFailure() throws InterruptedException {
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
        assertTrue(resultFromParallelThread instanceof CommandResultMessage);
        assertEquals(exception, ((CommandResultMessage) resultFromParallelThread).exceptionResult());
    }

    @Test
    void onSuccessForLimitedTime_Timeout() throws InterruptedException {
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
    void onResultReturnsMessageWithTimeoutExceptionOnTimeout() throws InterruptedException {
        Thread t = new Thread(() -> {
            resultFromParallelThread = testSubject.getResult(1, TimeUnit.NANOSECONDS);
        });
        t.start();
        t.join(1000);
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        assertTrue(resultFromParallelThread instanceof ResultMessage);
        assertTrue(((ResultMessage) resultFromParallelThread).exceptionResult() instanceof TimeoutException);
    }

    @Test
    void onResultUnwrapsExecutionResult() throws InterruptedException {
        Thread t = new Thread(() -> {
            resultFromParallelThread = testSubject.getResult();
        });
        t.start();
        testSubject.completeExceptionally(new MockException("Mocking an exception"));
        t.join();
        assertTrue(resultFromParallelThread instanceof ResultMessage);
        assertTrue(((ResultMessage) resultFromParallelThread).exceptionResult() instanceof MockException);
    }

    @Test
    void getThrowsExecutionException() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                testSubject.get();
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        testSubject.completeExceptionally(new MockException("Mocking an exception"));
        t.join();
        assertTrue(resultFromParallelThread instanceof ExecutionException);
    }

    @Test
    void onSuccessForLimitedTime_InTime() throws InterruptedException {
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
