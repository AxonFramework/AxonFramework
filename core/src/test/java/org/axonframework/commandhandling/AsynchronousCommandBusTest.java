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

package org.axonframework.commandhandling;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousCommandBusTest {

    private MessageHandlerInterceptor handlerInterceptor;
    private MessageDispatchInterceptor dispatchInterceptor;
    private MessageHandler<CommandMessage<?>> commandHandler;
    private ExecutorService executorService;
    private AsynchronousCommandBus testSubject;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        commandHandler = mock(MessageHandler.class);
        executorService = mock(ExecutorService.class);
        dispatchInterceptor = mock(MessageDispatchInterceptor.class);
        handlerInterceptor = mock(MessageHandlerInterceptor.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executorService).execute(isA(Runnable.class));
        testSubject = new AsynchronousCommandBus(executorService);
        testSubject.registerDispatchInterceptor(dispatchInterceptor);
        testSubject.registerHandlerInterceptor(handlerInterceptor);
        when(dispatchInterceptor.handle(isA(CommandMessage.class))).thenAnswer(invocation -> invocation.getArguments()[0]);
        when(handlerInterceptor.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchWithCallback() throws Exception {
        testSubject.subscribe(Object.class.getName(), commandHandler);
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.dispatch(command, mockCallback);

        InOrder inOrder = inOrder(mockCallback, executorService, commandHandler, dispatchInterceptor, handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class));
        inOrder.verify(mockCallback).onSuccess(eq(command), isNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchWithoutCallback() throws Exception {
        MessageHandler<CommandMessage<?>> commandHandler = mock(MessageHandler.class);
        testSubject.subscribe(Object.class.getName(), commandHandler);
        testSubject.dispatch(asCommandMessage(new Object()));

        InOrder inOrder = inOrder(executorService, commandHandler, dispatchInterceptor, handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class));
    }

    @Test
    public void testShutdown_ExecutorServiceUsed() {
        testSubject.shutdown();

        verify(executorService).shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NoHandlerForCommandException.class)
    public void testExceptionIsThrownWhenNoHandlerIsRegistered() {
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("test"));

    }

    @Test
    public void testShutdown_ExecutorUsed() {
        Executor executor = mock(Executor.class);
        new AsynchronousCommandBus(executor).shutdown();

        verify(executor, never()).execute(any(Runnable.class));
    }
}
