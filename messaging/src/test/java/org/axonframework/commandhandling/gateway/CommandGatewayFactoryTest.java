/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

import static java.util.Collections.singletonMap;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link CommandGatewayFactory}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
@SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
class CommandGatewayFactoryTest {

    private CommandBus mockCommandBus;
    private RetryScheduler mockRetryScheduler;
    private CompleteGateway gateway;
    @SuppressWarnings("rawtypes")
    private CommandCallback callback;

    private CommandGatewayFactory testSubject;

    @BeforeEach
    void setUp() {
        mockCommandBus = mock(CommandBus.class);
        mockRetryScheduler = mock(RetryScheduler.class);
        testSubject = CommandGatewayFactory.builder()
                                           .commandBus(mockCommandBus)
                                           .retryScheduler(mockRetryScheduler)
                                           .build();
        callback = spy(new StringCommandCallback());

        testSubject.registerCommandCallback(
                (commandMessage, commandResultMessage) -> {
                },
                ResponseTypes.instanceOf(String.class)
        );
        testSubject.registerCommandCallback(callback, ResponseTypes.instanceOf(String.class));
        gateway = testSubject.createGateway(CompleteGateway.class);
    }

    @Test
    @Timeout(value = 2)
    void testGatewayFireAndForget() {
        final Object metaTest = new Object();

        doAnswer(new Success(asCommandResultMessage(null)))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        testSubject.registerCommandCallback(callback, ResponseTypes.instanceOf(Void.class));

        gateway.fireAndForget("Command", null, metaTest, "value");

        verify(mockCommandBus).dispatch(
                argThat(x -> x.getMetaData().get("test") == metaTest && "value".equals(x.getMetaData().get("key"))),
                isA(RetryingCallback.class)
        );

        // Check that the callback is invoked, despite the null return value,
        //  as we have registered it under Void response type
        verify(callback).onResult(isA(CommandMessage.class), any());
    }

    @Test
    @Timeout(value = 2)
    void testGatewayFireAndForgetWithoutRetryScheduler() {
        final Object metaTest = new Object();

        CommandGatewayFactory testSubject = CommandGatewayFactory.builder().commandBus(mockCommandBus).build();
        CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);

        gateway.fireAndForget("Command", MetaData.from(singletonMap("otherKey", "otherVal")), metaTest, "value");

        verify(mockCommandBus).dispatch(argThat(
                x -> x.getMetaData().get("test") == metaTest
                        && "otherVal".equals(x.getMetaData().get("otherKey"))
                        && "value".equals(x.getMetaData().get("key"))
        ));
    }

    @Test
    @Timeout(value = 2)
    void testGatewayTimeout() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(new CountDown(latch))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Thread t = new Thread(() -> gateway.fireAndWait("Command"));
        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Expected command bus to be invoked");
        assertTrue(t.isAlive());
        t.interrupt();
    }

    @Test
    @Timeout(value = 2)
    void testGatewayWithReturnValueReturns() throws InterruptedException {
        String expectedReturnValue = "ReturnValue";
        CommandResultMessage<String> returnValue = asCommandResultMessage(expectedReturnValue);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();

        doAnswer(new Success(latch, returnValue))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Thread t = new Thread(() -> result.set(gateway.waitForReturnValue("Command")));
        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Expected command bus to be invoked");
        t.join();
        assertEquals(expectedReturnValue, result.get());
        verify(callback).onResult(any(), eq(returnValue));
    }

    @Test
    @Timeout(value = 2)
    void testGatewayWithReturnValueUndeclaredException() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        doAnswer(new Failure(latch, new ExpectedException()))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Thread t = new Thread(() -> {
            try {
                result.set(gateway.waitForReturnValue("Command"));
            } catch (Throwable e) {
                error.set(e);
            }
        });

        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Expected command bus to be invoked");
        t.join();

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof CommandExecutionException);
        assertTrue(error.get().getCause() instanceof ExpectedException);

        ArgumentCaptor<CommandResultMessage<?>> commandResultMessageCaptor = forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(ExpectedException.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    @Timeout(value = 2)
    void testGatewayWithReturnValueInterrupted() throws InterruptedException {
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

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof CommandExecutionException, "Expected CommandExecutionException");
        assertTrue(error.get().getCause() instanceof InterruptedException, "Expected wrapped InterruptedException");
    }

    @Test
    void testGatewayWithReturnValueRuntimeException() {
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

        assertNull(result.get(), "Did not expect ReturnValue");
        assertSame(runtimeException, error.get(), "Expected exact instance of RunTimeException being propagated");

        ArgumentCaptor<CommandResultMessage<?>> commandResultMessageCaptor = forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(RuntimeException.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    @Timeout(value = 2)
    void testGatewayWaitForExceptionInterrupted() throws InterruptedException {
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

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof InterruptedException);
    }

    @Test
    @Timeout(value = 2)
    void testGatewayWaitForUndeclaredInterruptedException() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t = new Thread(() -> {
            try {
                gateway.waitForReturnValue("Command");
            } catch (Throwable e) {
                error.set(e);
            }
        });

        t.start();
        t.interrupt();
        t.join();

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof CommandExecutionException);
    }

    @Test
    @Timeout(value = 2)
    void testFireAndWaitWithTimeoutParameterReturns() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        doAnswer(new Success(latch, asCommandResultMessage("OK!")))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                error.set(e);
            }
        });

        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        t.interrupt();

        // The return type of `fireAndWaitWithTimeoutParameter` is void, so return value is ignored
        assertNull(result.get(), "Did not expect ReturnValue");
        assertNull(error.get(), "Did not expect exception");
    }

    @Test
    @Timeout(value = 2)
    void testFireAndWaitWithTimeoutParameterTimeout() throws InterruptedException {
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

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof CommandExecutionException, "Expected CommandExecutionException");
        assertTrue(error.get().getCause() instanceof TimeoutException, "Expected wrapped InterruptedException");
    }

    @Test
    @Timeout(value = 2)
    void testFireAndWaitWithTimeoutParameterTimeoutException() throws InterruptedException {
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

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof TimeoutException);
    }

    @Test
    @Timeout(value = 2)
    void testFireAndWaitWithTimeoutParameterInterrupted() throws InterruptedException {
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

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof CommandExecutionException, "Expected CommandExecutionException");
        assertTrue(error.get().getCause() instanceof InterruptedException, "Expected wrapped InterruptedException");
    }

    @Test
    @Timeout(value = 2)
    void testFireAndWaitForCheckedException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        doAnswer(new Failure(latch, new ExpectedException()))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Thread t = new Thread(() -> {
            try {
                gateway.fireAndWaitForCheckedException("Command");
            } catch (Throwable e) {
                error.set(e);
            }
        });

        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        t.join();

        assertNull(result.get(), "Did not expect ReturnValue");
        assertTrue(error.get() instanceof ExpectedException);

        ArgumentCaptor<CommandResultMessage<?>> commandResultMessageCaptor = forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(ExpectedException.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    @Timeout(value = 2)
    void testFireAndGetFuture() throws InterruptedException {
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

        assertNotNull(result.get(), "Expected to get a Future return value");
        assertNull(error.get());
    }

    @Test
    @Timeout(value = 2)
    void testFireAndGetCompletableFuture() throws InterruptedException {
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

        assertNotNull(result.get(), "Expected to get a Future return value");
        assertNull(error.get());
    }

    @Test
    @Timeout(value = 2)
    void testFireAndGetFutureWithTimeout() throws Throwable {
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

        assertNotNull(result.get(), "Expected to get a Future return value");
        assertNull(error.get());
    }

    @Test
    @Timeout(value = 2)
    void testFireAndGetCompletionStageWithTimeout() throws Throwable {
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

        assertNotNull(result.get(), "Expected to get a CompletionStage return value");
        assertNull(error.get());
    }

    @Test
    @Timeout(value = 2)
    void testRetrySchedulerInvokedOnFailure() throws Throwable {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        doAnswer(new Failure(new SomeRuntimeException()))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

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
        assertNull(result.get(), "Did not Expect to get a Future return value");
    }

    @Test
    @Timeout(value = 2)
    void testRetrySchedulerNotInvokedOnCheckedException() throws Throwable {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        doAnswer(new Failure(new ExpectedException()))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

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
        assertNull(result.get(), "Did not Expect to get a Future return value");
    }

    @Test
    @Timeout(value = 2)
    void testRetrySchedulerInvokedOnExceptionCausedByDeadlock() {
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
        assertNull(result.get(), "Did not Expect to get a Future return value");
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayWaitForResultAndInvokeCallbacksSuccess() {
        CountDownLatch latch = new CountDownLatch(1);
        CommandResultMessage<String> resultMessage = asCommandResultMessage("OK");
        final CommandCallback<Object, String> callback1 = mock(CommandCallback.class);
        final CommandCallback<Object, String> callback2 = mock(CommandCallback.class);

        doAnswer(new Success(latch, resultMessage))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Object result = gateway.fireAndWaitAndInvokeCallbacks("Command", callback1, callback2);
        assertEquals(0, latch.getCount());

        assertNotNull(result);
        verify(callback1).onResult(any(), eq(resultMessage));
        verify(callback2).onResult(any(), eq(resultMessage));
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayWaitForResultAndInvokeCallbacksFailure() {
        final RuntimeException exception = new RuntimeException();
        final CommandCallback<Object, ?> callback1 = mock(CommandCallback.class);
        final CommandCallback<Object, ?> callback2 = mock(CommandCallback.class);

        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        try {
            gateway.fireAndWaitAndInvokeCallbacks("Command", callback1, callback2);
            fail("Expected exception");
        } catch (RuntimeException e) {
            //noinspection rawtypes
            ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor = forClass(CommandResultMessage.class);
            verify(callback1).onResult(any(), commandResultMessageCaptor.capture());
            verify(callback2).onResult(any(), commandResultMessageCaptor.capture());

            assertEquals(2, commandResultMessageCaptor.getAllValues().size());
            assertEquals(exception, commandResultMessageCaptor.getAllValues().get(0).exceptionResult());
            assertEquals(exception, commandResultMessageCaptor.getAllValues().get(1).exceptionResult());
        }
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayAsyncWithCallbacksSuccess() {
        CountDownLatch latch = new CountDownLatch(1);
        CommandResultMessage<String> resultMessage = asCommandResultMessage("OK");
        final CommandCallback<Object, String> callback1 = mock(CommandCallback.class);
        final CommandCallback<Object, String> callback2 = mock(CommandCallback.class);

        doAnswer(new Success(latch, resultMessage))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        assertEquals(0, latch.getCount());

        verify(callback1).onResult(any(), eq(resultMessage));
        verify(callback2).onResult(any(), eq(resultMessage));
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayAsyncWithCallbacksSuccessButReturnTypeDoesNotMatchCallback() {
        CountDownLatch latch = new CountDownLatch(1);
        CommandResultMessage<Object> resultMessage = asCommandResultMessage(42);
        final CommandCallback<Object, Object> callback1 = mock(CommandCallback.class);
        final CommandCallback<Object, Object> callback2 = mock(CommandCallback.class);

        doAnswer(new Success(latch, resultMessage))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);
        assertEquals(0, latch.getCount());

        verify(callback1).onResult(any(), eq(resultMessage));
        verify(callback2).onResult(any(), eq(resultMessage));
        verify(callback, never()).onResult(any(), any());
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayAsyncWithCallbacksFailure() {
        final RuntimeException exception = new RuntimeException();
        final CommandCallback<Object, ?> callback1 = mock(CommandCallback.class);
        final CommandCallback<Object, ?> callback2 = mock(CommandCallback.class);

        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        gateway.fireAsyncWithCallbacks("Command", callback1, callback2);

        //noinspection rawtypes
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                forClass(CommandResultMessage.class);
        verify(callback1).onResult(any(), commandResultMessageCaptor.capture());
        verify(callback2).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(2, commandResultMessageCaptor.getAllValues().size());
        assertEquals(exception, commandResultMessageCaptor.getAllValues().get(0).exceptionResult());
        assertEquals(exception, commandResultMessageCaptor.getAllValues().get(1).exceptionResult());
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayCompletableFutureFailure() {
        final RuntimeException exception = new RuntimeException();

        doAnswer(new Failure(exception))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        CompletableFuture<Object> future = gateway.fireAndGetCompletableFuture("Command");

        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayCompletableFutureSuccessfulResult() throws Throwable {
        String expectedReturnValue = "returnValue";

        doAnswer(new Success(asCommandResultMessage(expectedReturnValue)))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        CompletableFuture<Object> future = gateway.fireAndGetCompletableFuture("Command");

        assertTrue(future.isDone());
        assertEquals(expectedReturnValue, future.get());
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayFutureSuccessfulResult() throws Throwable {
        String expectedReturnValue = "returnValue";

        doAnswer(new Success(asCommandResultMessage(expectedReturnValue)))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Future<Object> future = gateway.fireAndGetFuture("Command");

        assertTrue(future.isDone());
        assertEquals("returnValue", future.get());
    }

    @Test
    @Timeout(value = 2)
    void testRetrySchedulerNotInvokedOnExceptionCausedByDeadlockAndActiveUnitOfWork() {
        final AtomicReference<Object> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        UnitOfWork<CommandMessage<?>> uow = DefaultUnitOfWork.startAndGet(null);

        doAnswer(new Failure(new RuntimeException(new DeadlockException("Mock"))))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

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
        assertNull(result.get(), "Did not Expect to get a Future return value");
    }

    @Test
    @Timeout(value = 2)
    void testCreateGatewayEqualsAndHashCode() {
        CompleteGateway gateway2 = testSubject.createGateway(CompleteGateway.class);

        assertNotSame(gateway, gateway2);
        assertNotEquals(gateway, gateway2);
    }

    @Test
    void testDifferentCommandCallbackResultTypesInvocationsAreAllInvoked() {
        String expectedResult = "OK";
        AtomicBoolean stringCallbackInvocation = new AtomicBoolean(false);
        AtomicBoolean integerCallbackInvocation = new AtomicBoolean(false);

        CommandResultMessage<String> resultMessage = asCommandResultMessage(expectedResult);
        CommandCallback<Object, String> stringCallback = (command, result) -> stringCallbackInvocation.set(true);
        CommandCallback<Object, Integer> integerCallback = (command, result) -> integerCallbackInvocation.set(true);

        doAnswer(new Success(resultMessage))
                .when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        Object result = gateway.fireAsyncWithCallbacksOfSpecificResultType("Command", stringCallback, integerCallback);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        assertTrue(stringCallbackInvocation.get());
        assertTrue(integerCallbackInvocation.get());
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

        Object fireAndWaitAndInvokeCallbacks(Object command,
                                             CommandCallback<Object, ?> first,
                                             CommandCallback<Object, ?> second);

        void fireAsyncWithCallbacks(Object command,
                                    CommandCallback<Object, ?> first,
                                    CommandCallback<Object, ?> second);

        Object fireAsyncWithCallbacksOfSpecificResultType(Object command,
                                                          CommandCallback<Object, String> stringCallback,
                                                          CommandCallback<Object, Integer> integerCallback);
    }

    private static class StringCommandCallback implements CommandCallback<Object, String> {

        @Override
        public void onResult(@Nonnull CommandMessage<?> commandMessage,
                             @Nonnull CommandResultMessage<? extends String> commandResultMessage) {
        }
    }

    private static class SomeRuntimeException extends RuntimeException {

    }

    private static class ExpectedException extends Exception {

    }

    private static class Success implements Answer<Object> {

        private final CountDownLatch latch;
        private final CommandResultMessage<?> returnValue;

        Success(CommandResultMessage<?> returnValue) {
            this(new CountDownLatch(1), returnValue);
        }

        Success(CountDownLatch latch, CommandResultMessage<?> returnValue) {
            this.latch = latch;
            this.returnValue = returnValue;
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            latch.countDown();
            //noinspection rawtypes
            ((CommandCallback) invocation.getArguments()[1])
                    .onResult((CommandMessage) invocation.getArguments()[0], returnValue);
            return null;
        }
    }

    private static class Failure implements Answer<Object> {

        private final CountDownLatch latch;
        private final Exception e;

        Failure(CountDownLatch latch, Exception e) {
            this.latch = latch;
            this.e = e;
        }

        Failure(Exception e) {
            this(null, e);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            if (latch != null) {
                latch.countDown();
            }
            //noinspection rawtypes
            ((CommandCallback) invocation.getArguments()[1])
                    .onResult((CommandMessage) invocation.getArguments()[0], asCommandResultMessage(e));
            return null;
        }
    }

    private static class CountDown implements Answer<Object> {

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
}
