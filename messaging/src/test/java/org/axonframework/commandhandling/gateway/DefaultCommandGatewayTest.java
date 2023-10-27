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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultCommandGateway}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class DefaultCommandGatewayTest {

    private DefaultCommandGateway testSubject;
    private CommandBus mockCommandBus;
    private RetryScheduler mockRetryScheduler;
    private MessageDispatchInterceptor<CommandMessage<?>> mockCommandMessageTransformer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockCommandBus = mock(CommandBus.class);
        mockRetryScheduler = mock(RetryScheduler.class);
        mockCommandMessageTransformer = mock(MessageDispatchInterceptor.class);
        when(mockCommandMessageTransformer.handle(isA(CommandMessage.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        testSubject = DefaultCommandGateway.builder()
                                           .commandBus(mockCommandBus)
                                           .retryScheduler(mockRetryScheduler)
                                           .dispatchInterceptors(mockCommandMessageTransformer)
                                           .build();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void sendWithCallbackCommandIsRetried() {
        doAnswer(invocation -> {
            ((CommandCallback<Object, Object>) invocation.getArguments()[1])
                    .onResult((CommandMessage<Object>) invocation.getArguments()[0],
                              asCommandResultMessage(new RuntimeException(new RuntimeException())));
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);
        //noinspection rawtypes
        final AtomicReference<CommandResultMessage> actualResult = new AtomicReference<>();
        testSubject.send("Command",
                         (CommandCallback<Object, Object>) (commandMessage, commandResultMessage) -> actualResult
                                 .set(commandResultMessage));
        verify(mockCommandMessageTransformer).handle(isA(CommandMessage.class));
        //noinspection rawtypes
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertTrue(actualResult.get().isExceptional());
        assertTrue(actualResult.get().exceptionResult() instanceof RuntimeException);
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void sendWithoutCallbackCommandIsRetried() {
        doAnswer(invocation -> {
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage(new RuntimeException(new RuntimeException()))
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);

        CompletableFuture<?> future = testSubject.send("Command");

        verify(mockCommandMessageTransformer).handle(isA(CommandMessage.class));
        //noinspection rawtypes
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void sendWithoutCallback() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage("returnValue")
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        CompletableFuture<?> future = testSubject.send("Command");

        assertTrue(future.isDone());
        assertEquals("returnValue", future.get());
    }

    @Test
    void sendWithoutCallbackCustomizedCallbackIsCalled() throws ExecutionException, InterruptedException {
        AtomicBoolean finalCallbackCalled = new AtomicBoolean(false);
        AtomicBoolean customizedCallbackCalled = new AtomicBoolean(false);
        testSubject = DefaultCommandGateway.builder()
                                           .commandBus(mockCommandBus)
                                           .retryScheduler(mockRetryScheduler)
                                           .dispatchInterceptors(mockCommandMessageTransformer)
                                           .commandCallback((commandMessage, commandResultMessage) -> {
                                               customizedCallbackCalled.set(true);
                                               // The customized callback should be called before the final callback
                                               assertFalse(finalCallbackCalled.get());
                                           })
                                           .build();
        doAnswer(invocation -> {
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage("returnValue")
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        CompletableFuture<?> future = testSubject.send("Command")
                                                 .whenComplete((o, throwable) -> {
                                                     finalCallbackCalled.set(true);
                                                 });

        assertTrue(future.isDone());
        assertTrue(customizedCallbackCalled.get());
        assertEquals("returnValue", future.get());
    }


    @SuppressWarnings({"unchecked"})
    @Test
    void sendAndWaitCommandIsRetried() {
        final RuntimeException failure = new RuntimeException(new RuntimeException());
        doAnswer(invocation -> {
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0], asCommandResultMessage(failure)
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);

        try {
            testSubject.sendAndWait("Command");
        } catch (RuntimeException rte) {
            assertSame(failure, rte);
        }

        verify(mockCommandMessageTransformer).handle(isA(CommandMessage.class));
        //noinspection rawtypes
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void sendAndWaitWithTimeoutCommandIsRetried() {
        final RuntimeException failure = new RuntimeException(new RuntimeException());
        doAnswer(invocation -> {
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0], asCommandResultMessage(failure)
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);

        try {
            testSubject.sendAndWait("Command", 1, TimeUnit.SECONDS);
        } catch (RuntimeException rte) {
            assertSame(failure, rte);
        }

        verify(mockCommandMessageTransformer).handle(isA(CommandMessage.class));
        //noinspection rawtypes
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendAndWaitNullOnInterrupt() {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        assertNull(testSubject.sendAndWait("Hello"));
        assertTrue(Thread.interrupted(), "Interrupt flag should be set on thread");
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendAndWaitWithTimeoutNullOnInterrupt() {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        try {
            testSubject.sendAndWait("Hello", 60, TimeUnit.SECONDS);
            testSubject.sendAndWait("Hello", 60, TimeUnit.SECONDS);
            fail("Expected interrupted exception");
        } catch (CommandExecutionException e) {
            assertTrue(e.getCause() instanceof InterruptedException);
        }
        assertTrue(Thread.interrupted(), "Interrupt flag should be set on thread");
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendAndWaitWithTimeoutNullOnTimeout() {
        try {
            assertNull(testSubject.sendAndWait("Hello", 10, TimeUnit.MILLISECONDS));
            fail("Expected interrupted exception");
        } catch (CommandExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
        }
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void correlationDataIsAttachedToCommandAsObject() {
        UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        unitOfWork.registerCorrelationDataProvider(message -> Collections.singletonMap("correlationId", "test"));
        testSubject.send("Hello");

        verify(mockCommandBus).dispatch(argThat(x -> "test".equals(x.getMetaData().get("correlationId"))),
                                        isA(CommandCallback.class));

        CurrentUnitOfWork.clear(unitOfWork);
    }

    @SuppressWarnings("unchecked")
    @Test
    void correlationDataIsAttachedToCommandAsMessage() {
        final Map<String, String> data = new HashMap<>();
        data.put("correlationId", "test");
        data.put("header", "someValue");
        UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        unitOfWork.registerCorrelationDataProvider(message -> data);
        testSubject.send(new GenericCommandMessage<>("Hello", Collections.singletonMap("header", "value")));

        verify(mockCommandBus).dispatch(argThat(x -> "test".equals(x.getMetaData().get("correlationId"))
                && "value".equals(x.getMetaData().get("header"))), isA(CommandCallback.class));

        CurrentUnitOfWork.clear(unitOfWork);
    }

    @Test
    void payloadExtractionProblemsReportedInException() throws ExecutionException, InterruptedException {
        doAnswer(i -> {
            CommandCallback<String, String> callback = i.getArgument(1);
            callback.onResult(i.getArgument(0), new GenericCommandResultMessage<String>("result") {
                private static final long serialVersionUID = -5443344481326465863L;

                @Override
                public String getPayload() {
                    throw new MockException("Faking serialization problem");
                }
            });
            return null;
        }).when(mockCommandBus).dispatch(any(), any());

        CompletableFuture<String> actual = testSubject.send("command");
        assertTrue(actual.isDone());
        assertTrue(actual.isCompletedExceptionally());
        assertEquals("Faking serialization problem", actual.exceptionally(Throwable::getMessage).get());
    }

    @Test
    void sendAndWaitAttachesMetaData() {
        //noinspection unchecked
        doAnswer(invocation -> {
            //noinspection unchecked
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage("returnValue")
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        String expectedPayload = "command";
        MetaData expectedMetaData = MetaData.with("key", "value");
        testSubject.sendAndWait(expectedPayload, expectedMetaData);

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<?>> messageCaptor = ArgumentCaptor.forClass(CommandMessage.class);

        //noinspection unchecked
        verify(mockCommandBus).dispatch(messageCaptor.capture(), isA(CommandCallback.class));
        CommandMessage<?> result = messageCaptor.getValue();

        assertEquals(expectedPayload, result.getPayload());
        assertEquals(expectedMetaData, result.getMetaData());
    }

    @Test
    void sendAndWaitWithTimeoutAttachesMetaData() {
        //noinspection unchecked
        doAnswer(invocation -> {
            //noinspection unchecked
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage("returnValue")
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        String expectedPayload = "command";
        MetaData expectedMetaData = MetaData.with("key", "value");
        testSubject.sendAndWait(expectedPayload, expectedMetaData, 10, TimeUnit.MILLISECONDS);

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<?>> messageCaptor = ArgumentCaptor.forClass(CommandMessage.class);

        //noinspection unchecked
        verify(mockCommandBus).dispatch(messageCaptor.capture(), isA(CommandCallback.class));
        CommandMessage<?> result = messageCaptor.getValue();

        assertEquals(expectedPayload, result.getPayload());
        assertEquals(expectedMetaData, result.getMetaData());
    }

    @Test
    void sendAttachesMetaData() {
        //noinspection unchecked
        doAnswer(invocation -> {
            //noinspection unchecked
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage("returnValue")
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        String expectedPayload = "command";
        MetaData expectedMetaData = MetaData.with("key", "value");
        testSubject.send(expectedPayload, expectedMetaData);

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<?>> messageCaptor = ArgumentCaptor.forClass(CommandMessage.class);

        //noinspection unchecked
        verify(mockCommandBus).dispatch(messageCaptor.capture(), isA(CommandCallback.class));
        CommandMessage<?> result = messageCaptor.getValue();

        assertEquals(expectedPayload, result.getPayload());
        assertEquals(expectedMetaData, result.getMetaData());
    }

    @Test
    void commandCallbackIsCustomized() {
        AtomicBoolean customizedCallbackIsCalled = new AtomicBoolean();

        testSubject = DefaultCommandGateway.builder()
                                           .commandBus(mockCommandBus)
                                           .retryScheduler(mockRetryScheduler)
                                           .dispatchInterceptors(mockCommandMessageTransformer)
                                           .commandCallback((commandMessage, commandResultMessage) -> customizedCallbackIsCalled.set(
                                                   true))
                                           .build();

        //noinspection unchecked
        doAnswer(invocation -> {
            //noinspection unchecked
            ((CommandCallback<Object, Object>) invocation.getArguments()[1]).onResult(
                    (CommandMessage<Object>) invocation.getArguments()[0],
                    asCommandResultMessage("returnValue")
            );
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        String expectedPayload = "command";
        testSubject.send(expectedPayload, MetaData.emptyInstance());

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<?>> messageCaptor = ArgumentCaptor.forClass(CommandMessage.class);

        //noinspection unchecked
        verify(mockCommandBus).dispatch(messageCaptor.capture(), isA(CommandCallback.class));
        CommandMessage<?> result = messageCaptor.getValue();

        assertEquals(expectedPayload, result.getPayload());
        assertTrue(customizedCallbackIsCalled.get());
    }

    private static class RescheduleCommand implements Answer<Boolean> {

        @Override
        public Boolean answer(InvocationOnMock invocation) {
            ((Runnable) invocation.getArguments()[3]).run();
            return true;
        }
    }
}
