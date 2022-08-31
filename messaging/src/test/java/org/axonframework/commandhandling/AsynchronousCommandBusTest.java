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

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class AsynchronousCommandBusTest {

    private MessageHandlerInterceptor<CommandMessage<?>> handlerInterceptor;
    private MessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor;
    private MessageHandler<CommandMessage<?>> commandHandler;
    private ExecutorService executorService;
    private AsynchronousCommandBus testSubject;
    private TestSpanFactory spanFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        commandHandler = mock(MessageHandler.class);
        executorService = mock(ExecutorService.class);
        dispatchInterceptor = mock(MessageDispatchInterceptor.class);
        handlerInterceptor = mock(MessageHandlerInterceptor.class);
        spanFactory = new TestSpanFactory();
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executorService).execute(isA(Runnable.class));
        testSubject = AsynchronousCommandBus.builder().executor(executorService).spanFactory(spanFactory).build();
        testSubject.registerDispatchInterceptor(dispatchInterceptor);
        testSubject.registerHandlerInterceptor(handlerInterceptor);
        when(dispatchInterceptor.handle(isA(CommandMessage.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        when(handlerInterceptor.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceed());
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatchWithCallback() throws Exception {
        testSubject.subscribe(Object.class.getName(), commandHandler);
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCallback.wrap(any())).thenCallRealMethod();
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.dispatch(command, mockCallback);

        spanFactory.verifySpanCompleted("SimpleCommandBus.dispatch");
        spanFactory.verifySpanPropagated("SimpleCommandBus.dispatch", command);

        spanFactory.verifySpanCompleted("SimpleCommandBus.handle");

        InOrder inOrder = inOrder(mockCallback,
                                  executorService,
                                  commandHandler,
                                  dispatchInterceptor,
                                  handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class));
        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandResultMessage<Object>> responseCaptor = ArgumentCaptor
                .forClass(CommandResultMessage.class);
        inOrder.verify(mockCallback).onResult(commandCaptor.capture(), responseCaptor.capture());
        assertEquals(command, commandCaptor.getValue());
        assertNull(responseCaptor.getValue().getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatchWithoutCallback() throws Exception {
        MessageHandler<CommandMessage<?>> commandHandler = mock(MessageHandler.class);
        testSubject.subscribe(Object.class.getName(), commandHandler);
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.dispatch(command, NoOpCallback.INSTANCE);

        spanFactory.verifySpanCompleted("SimpleCommandBus.dispatch");
        spanFactory.verifySpanPropagated("SimpleCommandBus.dispatch", command);


        spanFactory.verifySpanCompleted("SimpleCommandBus.handle");

        InOrder inOrder = inOrder(executorService, commandHandler, dispatchInterceptor, handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class));
    }

    @Test
    void shutdown_ExecutorServiceUsed() {
        testSubject.shutdown();

        verify(executorService).shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    void exceptionIsThrownWhenNoHandlerIsRegistered() {
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        when(callback.wrap(any())).thenCallRealMethod();
        CommandMessage<Object> command = asCommandMessage("test");
        testSubject.dispatch(command, callback);
        //noinspection rawtypes
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(eq(command), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
        spanFactory.verifySpanHasException("SimpleCommandBus.dispatch", NoHandlerForCommandException.class);
    }

    @Test
    void shutdown_ExecutorUsed() {
        Executor executor = mock(Executor.class);
        AsynchronousCommandBus.builder().executor(executor).build().shutdown();

        verify(executor, never()).execute(any(Runnable.class));
    }
}
