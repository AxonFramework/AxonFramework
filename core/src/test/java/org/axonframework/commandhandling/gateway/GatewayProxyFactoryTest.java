/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.annotation.MetaData;
import org.axonframework.common.lock.DeadlockException;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
public class GatewayProxyFactoryTest {

    private CommandBus mockCommandBus;
    private GatewayProxyFactory testSubject;
    private CompleteGateway gateway;
    private RetryScheduler mockRetryScheduler;
    private CommandCallback callback;

    @Before
    public void setUp() {
        mockCommandBus = mock(CommandBus.class);
        mockRetryScheduler = mock(RetryScheduler.class);
        testSubject = new GatewayProxyFactory(mockCommandBus, mockRetryScheduler);
        callback = spy(new StringCommandCallback());
        testSubject.registerCommandCallback(new CommandCallback<Object, String>() {
            @Override
            public void onSuccess(CommandMessage<?> commandMessage, String result) {
            }

            @Override
            public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
            }
        });
        testSubject.registerCommandCallback(callback);
        gateway = testSubject.createGateway(CompleteGateway.class);
    }

    @Test//(timeout = 2000)
    public void testGateway_FireAndForget() {
        final Object metaTest = new Object();
        gateway.fireAndForget("Command", null, metaTest, "value");
        verify(mockCommandBus).dispatch(argThat(new TypeSafeMatcher<CommandMessage<Object>>() {
            @Override
            public boolean matchesSafely(CommandMessage<Object> item) {
                return item.getMetaData().get("test") == metaTest
                        && "value".equals(item.getMetaData().get("key"));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("A command with 2 meta data entries");
            }
        }), isA(RetryingCallback.class));
    }

    @Test(timeout = 2000)
    public void testGateway_FireAndForgetWithoutRetryScheduler() {
        final Object metaTest = new Object();
        GatewayProxyFactory testSubject = new GatewayProxyFactory(mockCommandBus);
        CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        gateway.fireAndForget("Command", org.axonframework.messaging.MetaData.from(singletonMap("otherKey", "otherVal")),
                              metaTest, "value");
        // in this case, no callback is used
        verify(mockCommandBus).dispatch(argThat(new TypeSafeMatcher<CommandMessage<Object>>() {
            @Override
            public boolean matchesSafely(CommandMessage<Object> item) {
                return item.getMetaData().get("test") == metaTest
                        && "otherVal".equals(item.getMetaData().get("otherKey"))
                        && "value".equals(item.getMetaData().get("key"));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("A command with 2 meta data entries");
            }
        }));
    }

    @Test(timeout = 2000)
    public void testGateway_Timeout() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new CountDown(cdl)).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                   isA(CommandCallback.class));
        Thread t = new Thread(() -> gateway.fireAndWait("Command"));
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        assertTrue(t.isAlive());
        t.interrupt();
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValue_Returns() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        doAnswer(new Success(cdl, "ReturnValue")).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                isA(CommandCallback.class));
        Thread t = new Thread(() -> result.set(gateway.waitForReturnValue("Command")));
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertEquals("ReturnValue", result.get());
        verify(callback).onSuccess(any(), eq("ReturnValue"));
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValue_UndeclaredException() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        doAnswer(new Failure(cdl, new ExpectedException())).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                          isA(CommandCallback.class));
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.waitForReturnValue("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof CommandExecutionException);
        assertTrue(error.get().getCause() instanceof ExpectedException);
        verify(callback).onFailure(any(), isA(ExpectedException.class));
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValue_Interrupted() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.waitForReturnValue("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertNull(error.get());
    }

    @Test
    public void testGatewayWithReturnValue_RuntimeException() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        RuntimeException runtimeException = new RuntimeException();
        doAnswer(new Failure(null, runtimeException))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
            try {
                result.set(gateway.waitForReturnValue("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        assertNull("Did not expect ReturnValue", result.get());
        assertSame("Expected exact instance of RunTimeException being propagated", runtimeException, error.get());
        verify(callback).onFailure(any(), isA(RuntimeException.class));
    }

    @Test(timeout = 2000)
    public void testGatewayWaitForException_Interrupted() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                gateway.waitForException("Command");
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof InterruptedException);
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_Returns() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new Success(cdl, "OK!")).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                        isA(CommandCallback.class));
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        assertTrue(cdl.await(1, TimeUnit.SECONDS));
        t.interrupt();
        // the return type is void, so return value is ignored
        assertNull("Did not expect ReturnValue", result.get());
        assertNull("Did not expect exception", error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_Timeout() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertNull("Did not expect exception", error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_TimeoutException() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitWithTimeoutParameterAndException("Command", 1, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof TimeoutException);
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_Interrupted() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.SECONDS);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertNull("Did not expect exception", error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndWaitForCheckedException() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new Failure(cdl, new ExpectedException())).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                          isA(CommandCallback.class));
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitForCheckedException("Command");
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        assertTrue(cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof ExpectedException);
        verify(callback).onFailure(any(), isA(ExpectedException.class));
    }

    @Test(timeout = 2000)
    public void testFireAndGetFuture() throws InterruptedException {
        final AtomicReference<Future<Object>> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.fireAndGetFuture("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        assertNotNull("Expected to get a Future return value", result.get());
        assertNull(error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndGetFutureWithTimeout() throws Throwable {
        final AtomicReference<Future<Object>> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.futureWithTimeout("Command", 100, TimeUnit.SECONDS));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        if (error.get() != null) {
            throw error.get();
        }
        assertNotNull("Expected to get a Future return value", result.get());
    }

    @Test(timeout = 2000)
    public void testRetrySchedulerInvokedOnFailure() throws Throwable {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        doAnswer(new Failure(new SomeRuntimeException())).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                        isA(CommandCallback.class));
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.waitForReturnValue("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        verify(mockRetryScheduler).scheduleRetry(isA(CommandMessage.class),
                                                 isA(SomeRuntimeException.class),
                                                 anyList(),
                                                 any(Runnable.class));
        assertNotNull(error.get());
        assertNull("Did not Expect to get a Future return value", result.get());
    }

    @Test(timeout = 2000)
    public void testRetrySchedulerNotInvokedOnCheckedException() throws Throwable {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        doAnswer(new Failure(new ExpectedException())).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                     isA(CommandCallback.class));
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.waitForReturnValue("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        verify(mockRetryScheduler, never()).scheduleRetry(isA(CommandMessage.class),
                                                          any(RuntimeException.class),
                                                          anyList(),
                                                          any(Runnable.class));
        assertNotNull(error.get());
        assertNull("Did not Expect to get a Future return value", result.get());
    }

    @Test(timeout = 2000)
    public void testRetrySchedulerInvokedOnExceptionCausedByDeadlock() throws Throwable {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        doAnswer(new Failure(new RuntimeException(new DeadlockException("Mock"))))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        try {
            result.set(gateway.waitForReturnValue("Command"));
        } catch (Exception e) {
            error.set(e);
        }
        verify(mockRetryScheduler).scheduleRetry(isA(CommandMessage.class),
                                                 any(RuntimeException.class),
                                                 anyList(),
                                                 any(Runnable.class));
        assertNotNull(error.get());
        assertNull("Did not Expect to get a Future return value", result.get());
    }

    @Test(timeout = 2000)
    public void testCreateGateway_WaitForResultAndInvokeCallbacks_Success() {
        CountDownLatch cdl = new CountDownLatch(1);

        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        doAnswer(new Success(cdl, "OK"))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Object result = gateway.fireAndWaitAndInvokeCallbacks("Command", callback1, callback2);
        assertEquals(0, cdl.getCount());

        assertNotNull(result);
        verify(callback1).onSuccess(any(), eq(result));
        verify(callback2).onSuccess(any(), eq(result));
    }


    @Test(timeout = 2000)
    public void testCreateGateway_WaitForResultAndInvokeCallbacks_Failure() {
        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        final RuntimeException exception = new RuntimeException();
        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        try {
            gateway.fireAndWaitAndInvokeCallbacks("Command", callback1, callback2);
            fail("Expected exception");
        } catch (RuntimeException e) {
            verify(callback1).onFailure(any(), eq(exception));
            verify(callback2).onFailure(any(), eq(exception));
        }
    }

    @Test(timeout = 2000)
    public void testCreateGateway_AsyncWithCallbacks_Success() {
        CountDownLatch cdl = new CountDownLatch(1);

        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        doAnswer(new Success(cdl, "OK"))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        assertEquals(0, cdl.getCount());

        verify(callback1).onSuccess(any(), eq("OK"));
        verify(callback2).onSuccess(any(), eq("OK"));
    }

    @Test(timeout = 2000)
    public void testCreateGateway_AsyncWithCallbacks_Success_ButReturnTypeDoesntMatchCallback() {
        CountDownLatch cdl = new CountDownLatch(1);

        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        doAnswer(new Success(cdl, 42))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        assertEquals(0, cdl.getCount());

        verify(callback1).onSuccess(any(), eq(42));
        verify(callback2).onSuccess(any(), eq(42));
        verify(callback, never()).onSuccess(any(), anyObject());
    }

    @Test(timeout = 2000)
    public void testCreateGateway_AsyncWithCallbacks_Failure() {
        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        final RuntimeException exception = new RuntimeException();
        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        verify(callback1).onFailure(any(), eq(exception));
        verify(callback2).onFailure(any(), eq(exception));
    }

    @Test(timeout = 2000)
    public void testRetrySchedulerNotInvokedOnExceptionCausedByDeadlockAndActiveUnitOfWork() throws Throwable {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        doAnswer(new Failure(new RuntimeException(new DeadlockException("Mock"))))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        UnitOfWork<CommandMessage<?>> uow = DefaultUnitOfWork.startAndGet(null);
        try {
            result.set(gateway.waitForReturnValue("Command"));
        } catch (Exception e) {
            error.set(e);
        } finally {
            uow.rollback();
        }
        verify(mockRetryScheduler, never()).scheduleRetry(isA(CommandMessage.class),
                                                          any(RuntimeException.class),
                                                          anyList(),
                                                          any(Runnable.class));
        assertNotNull(error.get());
        assertNull("Did not Expect to get a Future return value", result.get());
    }

    @Test(timeout = 2000)
    public void testCreateGateway_EqualsAndHashCode() {
        CompleteGateway gateway2 = testSubject.createGateway(CompleteGateway.class);

        assertNotSame(gateway, gateway2);
        assertFalse(gateway.equals(gateway2));
        assertNotNull(gateway.hashCode());
        assertNotNull(gateway2.hashCode());
    }

    private interface CompleteGateway {

        void fireAndForget(Object command, org.axonframework.messaging.MetaData meta,
                           @MetaData("test") Object metaTest, @MetaData("key") Object metaKey);

        String waitForReturnValue(Object command);

        void waitForException(Object command) throws InterruptedException;

        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void fireAndWait(Object command);

        void fireAndWaitWithTimeoutParameter(Object command, long timeout, TimeUnit unit);

        Object fireAndWaitWithTimeoutParameterAndException(Object command, long timeout, TimeUnit unit)
                throws TimeoutException;

        Object fireAndWaitForCheckedException(Object command) throws ExpectedException;

        Future<Object> fireAndGetFuture(Object command);

        Future<Object> futureWithTimeout(Object command, int timeout, TimeUnit unit);

        Object fireAndWaitAndInvokeCallbacks(Object command, CommandCallback first, CommandCallback second);

        void fireAsyncWithCallbacks(Object command, CommandCallback first, CommandCallback second);
    }

    public static class ExpectedException extends Exception {

    }

    private static class CountDown implements Answer {

        private final CountDownLatch cdl;

        public CountDown(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            cdl.countDown();
            return null;
        }
    }

    private static class Success implements Answer {

        private final CountDownLatch cdl;
        private final Object returnValue;

        public Success(CountDownLatch cdl, Object returnValue) {
            this.cdl = cdl;
            this.returnValue = returnValue;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            cdl.countDown();
            ((CommandCallback) invocation.getArguments()[1]).onSuccess((CommandMessage) invocation.getArguments()[0],
                                                                       returnValue);
            return null;
        }
    }

    public static class StringCommandCallback implements CommandCallback<Object, String> {

        @Override
        public void onSuccess(CommandMessage<?> commandMessage, String result) {
        }

        @Override
        public void onFailure(CommandMessage commandMessage, Throwable cause) {
        }
    }

    private class Failure implements Answer {

        private final CountDownLatch cdl;
        private final Exception e;

        public Failure(CountDownLatch cdl, Exception e) {
            this.cdl = cdl;
            this.e = e;
        }

        public Failure(Exception e) {
            this(null, e);
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            if (cdl != null) {
                cdl.countDown();
            }
            ((CommandCallback) invocation.getArguments()[1]).onFailure((CommandMessage) invocation.getArguments()[0],
                                                                       e);
            return null;
        }
    }

    private class SomeRuntimeException extends RuntimeException {

    }
}
