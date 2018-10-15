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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.lock.DeadlockException;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonMap;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
@SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
public class CommandGatewayFactoryTest {

    private CommandBus mockCommandBus;
    private CommandGatewayFactory testSubject;
    private CompleteGateway gateway;
    private RetryScheduler mockRetryScheduler;
    private CommandCallback callback;

    @Before
    public void setUp() {
        mockCommandBus = mock(CommandBus.class);
        mockRetryScheduler = mock(RetryScheduler.class);
        testSubject = CommandGatewayFactory.builder()
                                           .commandBus(mockCommandBus)
                                           .retryScheduler(mockRetryScheduler)
                                           .build();
        callback = spy(new StringCommandCallback());
        testSubject.registerCommandCallback((commandMessage, commandResultMessage) -> { },
                                            ResponseTypes.instanceOf(String.class));
        testSubject.registerCommandCallback(callback, ResponseTypes.instanceOf(String.class));
        gateway = testSubject.createGateway(CompleteGateway.class);
    }

    @Test//(timeout = 2000)
    public void testGatewayFireAndForget() {
        doAnswer(i -> {
            ((CommandCallback) i.getArguments()[1]).onResult((CommandMessage) i.getArguments()[0],
                                                             asCommandResultMessage(null));
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        testSubject.registerCommandCallback(callback, ResponseTypes.instanceOf(Void.class));

        final Object metaTest = new Object();
        gateway.fireAndForget("Command", null, metaTest, "value");
        verify(mockCommandBus).dispatch(argThat(x -> x.getMetaData().get("test") == metaTest
                && "value".equals(x.getMetaData().get("key"))), isA(RetryingCallback.class));

        // check that the callback is invoked, despite the null return value
        verify(callback).onResult(isA(CommandMessage.class), any());
    }

    @Test(timeout = 2000)
    public void testGatewayFireAndForgetWithoutRetryScheduler() {
        final Object metaTest = new Object();
        CommandGatewayFactory testSubject = CommandGatewayFactory.builder().commandBus(mockCommandBus).build();
        CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        gateway.fireAndForget("Command",
                              MetaData.from(singletonMap("otherKey", "otherVal")),
                              metaTest,
                              "value");
        // in this case, no callback is used
        verify(mockCommandBus).dispatch(argThat(x -> x.getMetaData().get("test") == metaTest
                && "otherVal".equals(x.getMetaData().get("otherKey"))
                && "value".equals(x.getMetaData().get("key"))));
    }

    @Test(timeout = 2000)
    public void testGatewayTimeout() throws InterruptedException {
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
    public void testGatewayWithReturnValueReturns() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        CommandResultMessage<String> returnValue = asCommandResultMessage("ReturnValue");
        doAnswer(new Success(cdl, returnValue)).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                              isA(CommandCallback.class));
        Thread t = new Thread(() -> result.set(gateway.waitForReturnValue("Command")));
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertEquals("ReturnValue", result.get());
        verify(callback).onResult(any(), eq(returnValue));
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValueUndeclaredException() throws InterruptedException {
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
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(ExpectedException.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValueInterrupted() throws InterruptedException {
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
    public void testGatewayWithReturnValueRuntimeException() {
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
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(RuntimeException.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test(timeout = 2000)
    public void testGatewayWaitForExceptionInterrupted() throws InterruptedException {
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
    public void testFireAndWaitWithTimeoutParameterReturns() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new Success(cdl, asCommandResultMessage("OK!")))
                .when(mockCommandBus)
                .dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
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
    public void testFireAndWaitWithTimeoutParameterTimeout() throws InterruptedException {
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
    public void testFireAndWaitWithTimeoutParameterTimeoutException() throws InterruptedException {
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
    public void testFireAndWaitWithTimeoutParameterInterrupted() throws InterruptedException {
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
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(ExpectedException.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
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
    public void testFireAndGetCompletableFuture() throws InterruptedException {
        final AtomicReference<CompletableFuture<Object>> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.fireAndGetCompletableFuture("Command"));
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
    public void testFireAndGetCompletionStageWithTimeout() throws Throwable {
        final AtomicReference<CompletionStage<Object>> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(gateway.fireAndGetCompletionStage("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        t.join();
        if (error.get() != null) {
            throw error.get();
        }
        assertNotNull("Expected to get a CompletionStage return value", result.get());
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
    public void testRetrySchedulerInvokedOnExceptionCausedByDeadlock() {
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
    public void testCreateGatewayWaitForResultAndInvokeCallbacksSuccess() {
        CountDownLatch cdl = new CountDownLatch(1);

        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        CommandResultMessage<String> ok = asCommandResultMessage("OK");
        doAnswer(new Success(cdl, ok))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Object result = gateway.fireAndWaitAndInvokeCallbacks("Command", callback1, callback2);
        assertEquals(0, cdl.getCount());

        assertNotNull(result);
        verify(callback1).onResult(any(), eq(ok));
        verify(callback2).onResult(any(), eq(ok));
    }

    @Test(timeout = 2000)
    public void testCreateGatewayWaitForResultAndInvokeCallbacksFailure() {
        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        final RuntimeException exception = new RuntimeException();
        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        try {
            gateway.fireAndWaitAndInvokeCallbacks("Command", callback1, callback2);
            fail("Expected exception");
        } catch (RuntimeException e) {
            ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                    ArgumentCaptor.forClass(CommandResultMessage.class);
            verify(callback1).onResult(any(), commandResultMessageCaptor.capture());
            verify(callback2).onResult(any(), commandResultMessageCaptor.capture());
            assertEquals(2, commandResultMessageCaptor.getAllValues().size());
            assertEquals(exception, commandResultMessageCaptor.getAllValues().get(0).exceptionResult());
            assertEquals(exception, commandResultMessageCaptor.getAllValues().get(1).exceptionResult());
        }
    }

    @Test(timeout = 2000)
    public void testCreateGatewayAsyncWithCallbacksSuccess() {
        CountDownLatch cdl = new CountDownLatch(1);

        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        CommandResultMessage<String> ok = asCommandResultMessage("OK");
        doAnswer(new Success(cdl, ok))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        assertEquals(0, cdl.getCount());

        verify(callback1).onResult(any(), eq(ok));
        verify(callback2).onResult(any(), eq(ok));
    }

    @Test(timeout = 2000)
    public void testCreateGatewayAsyncWithCallbacksSuccessButReturnTypeDoesNotMatchCallback() {
        CountDownLatch cdl = new CountDownLatch(1);

        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        CommandResultMessage<Object> resultMessage = asCommandResultMessage(42);
        doAnswer(new Success(cdl, resultMessage))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        assertEquals(0, cdl.getCount());

        verify(callback1).onResult(any(), eq(resultMessage));
        verify(callback2).onResult(any(), eq(resultMessage));
        verify(callback, never()).onResult(any(), any());
    }

    @Test(timeout = 2000)
    public void testCreateGatewayAsyncWithCallbacksFailure() {
        final CommandCallback callback1 = mock(CommandCallback.class);
        final CommandCallback callback2 = mock(CommandCallback.class);

        final RuntimeException exception = new RuntimeException();
        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback1).onResult(any(), commandResultMessageCaptor.capture());
        verify(callback2).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(2, commandResultMessageCaptor.getAllValues().size());
        assertEquals(exception, commandResultMessageCaptor.getAllValues().get(0).exceptionResult());
        assertEquals(exception, commandResultMessageCaptor.getAllValues().get(1).exceptionResult());
    }

    @Test(timeout = 2000)
    public void testCreateGatewayCompletableFutureFailure() {
        final RuntimeException exception = new RuntimeException();
        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        CompletableFuture future = gateway.fireAndGetCompletableFuture("Command");
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
    }

    @Test(timeout = 2000)
    public void testCreateGatewayCompletableFutureSuccessfulResult() throws Throwable {
        doAnswer(invocationOnMock -> {
            ((CommandCallback) invocationOnMock.getArguments()[1])
                    .onResult((CommandMessage) invocationOnMock.getArguments()[0],
                              asCommandResultMessage("returnValue"));
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        CompletableFuture future = gateway.fireAndGetCompletableFuture("Command");
        assertTrue(future.isDone());
        assertEquals(future.get(), "returnValue");
    }

    @Test(timeout = 2000)
    public void testCreateGatewayFutureSuccessfulResult() throws Throwable {
        doAnswer(invocationOnMock -> {
            ((CommandCallback) invocationOnMock.getArguments()[1])
                    .onResult((CommandMessage) invocationOnMock.getArguments()[0],
                              asCommandResultMessage("returnValue"));
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Future future = gateway.fireAndGetFuture("Command");
        assertTrue(future.isDone());
        assertEquals(future.get(), "returnValue");
    }

    @Test(timeout = 2000)
    public void testRetrySchedulerNotInvokedOnExceptionCausedByDeadlockAndActiveUnitOfWork() {
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
    public void testCreateGatewayEqualsAndHashCode() {
        CompleteGateway gateway2 = testSubject.createGateway(CompleteGateway.class);

        assertNotSame(gateway, gateway2);
        assertNotEquals(gateway, gateway2);
    }

    @SuppressWarnings("UnusedReturnValue")
    private interface CompleteGateway {

        void fireAndForget(Object command, MetaData meta,
                           @MetaDataValue("test") Object metaTest, @MetaDataValue("key") Object metaKey);

        String waitForReturnValue(Object command);

        void waitForException(Object command) throws InterruptedException;

        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void fireAndWait(Object command);

        void fireAndWaitWithTimeoutParameter(Object command, long timeout, TimeUnit unit);

        Object fireAndWaitWithTimeoutParameterAndException(Object command, long timeout, TimeUnit unit)
                throws TimeoutException;

        Object fireAndWaitForCheckedException(Object command) throws ExpectedException;

        Future<Object> fireAndGetFuture(Object command);

        CompletableFuture<Object> fireAndGetCompletableFuture(Object command);

        CompletionStage<Object> fireAndGetCompletionStage(Object command);

        CompletableFuture<Object> futureWithTimeout(Object command, int timeout, TimeUnit unit);

        Object fireAndWaitAndInvokeCallbacks(Object command, CommandCallback first, CommandCallback second);

        void fireAsyncWithCallbacks(Object command, CommandCallback first, CommandCallback second);
    }

    private static class ExpectedException extends Exception {

    }

    private static class CountDown implements Answer {

        private final CountDownLatch cdl;

        CountDown(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            cdl.countDown();
            return null;
        }
    }

    private static class Success implements Answer {

        private final CountDownLatch cdl;
        private final CommandResultMessage<?> returnValue;

        Success(CountDownLatch cdl, CommandResultMessage<?> returnValue) {
            this.cdl = cdl;
            this.returnValue = returnValue;
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            cdl.countDown();
            ((CommandCallback) invocation.getArguments()[1]).onResult((CommandMessage) invocation.getArguments()[0],
                                                                      returnValue);
            return null;
        }
    }

    public static class StringCommandCallback implements CommandCallback<Object, String> {

        @Override
        public void onResult(CommandMessage<?> commandMessage,
                             CommandResultMessage<? extends String> commandResultMessage) {
        }
    }

    private class Failure implements Answer {

        private final CountDownLatch cdl;
        private final Exception e;

        Failure(CountDownLatch cdl, Exception e) {
            this.cdl = cdl;
            this.e = e;
        }

        Failure(Exception e) {
            this(null, e);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            if (cdl != null) {
                cdl.countDown();
            }
            ((CommandCallback) invocation.getArguments()[1]).onResult((CommandMessage) invocation.getArguments()[0],
                                                                       asCommandResultMessage(e));
            return null;
        }
    }

    private class SomeRuntimeException extends RuntimeException {

    }
}
