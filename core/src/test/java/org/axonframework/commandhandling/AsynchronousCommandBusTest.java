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

import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Allard Buijze
 */
public class AsynchronousCommandBusTest {

    private CommandHandlerInterceptor handlerInterceptor;
    private CommandDispatchInterceptor dispatchInterceptor;
    private CommandHandler commandHandler;
    private ExecutorService executorService;
    private AsynchronousCommandBus testSubject;

    @Before
    public void setUp() throws Throwable {
        commandHandler = mock(CommandHandler.class);
        executorService = mock(ExecutorService.class);
        dispatchInterceptor = mock(CommandDispatchInterceptor.class);
        handlerInterceptor = mock(CommandHandlerInterceptor.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executorService).execute(isA(Runnable.class));
        testSubject = new AsynchronousCommandBus(executorService);
        testSubject.setDispatchInterceptors(Arrays.asList(dispatchInterceptor));
        testSubject.setHandlerInterceptors(Arrays.asList(handlerInterceptor));
        when(dispatchInterceptor.handle(isA(CommandMessage.class))).thenAnswer(invocation -> invocation.getArguments()[0]);
        when(handlerInterceptor.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[2]).proceed());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchWithCallback() throws Throwable {
        testSubject.subscribe(Object.class.getName(), commandHandler);
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        final CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.dispatch(command, mockCallback);

        InOrder inOrder = inOrder(mockCallback, executorService, commandHandler, dispatchInterceptor,
                                  handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(CommandMessage.class),
                                                  isA(UnitOfWork.class),
                                                  isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
        inOrder.verify(mockCallback).onSuccess(eq(command), isNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchWithoutCallback() throws Throwable {
        CommandHandler commandHandler = mock(CommandHandler.class);
        testSubject.subscribe(Object.class.getName(), commandHandler);
        testSubject.dispatch(asCommandMessage(new Object()));

        InOrder inOrder = inOrder(executorService, commandHandler, dispatchInterceptor, handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(CommandMessage.class),
                                                  isA(UnitOfWork.class),
                                                  isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
    }

    @Test
    public void testShutdown_ExecutorServiceUsed() {
        testSubject.shutdown();

        verify(executorService).shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCallbackIsInvokedWhenNoHandlerIsRegistered() {
        final CommandCallback callback = mock(CommandCallback.class);
        final CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("test");
        testSubject.dispatch(command, callback);

        verify(callback).onFailure(eq(command), isA(NoHandlerForCommandException.class));
    }

    @Test
    public void testShutdown_ExecutorUsed() {
        Executor executor = mock(Executor.class);
        new AsynchronousCommandBus(executor).shutdown();

        verify(executor, never()).execute(any(Runnable.class));
    }
}
