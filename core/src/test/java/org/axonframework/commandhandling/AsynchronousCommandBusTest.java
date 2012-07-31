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

import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousCommandBusTest {

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchWithCallback() throws Throwable {
        Executor executorService = mock(ExecutorService.class);
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(executorService).execute(isA(Runnable.class));
        AsynchronousCommandBus commandBus = new AsynchronousCommandBus(executorService);
        CommandHandler commandHandler = mock(CommandHandler.class);
        commandBus.subscribe(Object.class, commandHandler);
        CommandCallback<Object> mockCallback = mock(CommandCallback.class);
        commandBus.dispatch(asCommandMessage(new Object()), mockCallback);

        verify(executorService).execute(isA(Runnable.class));
        verify(commandHandler).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
        verify(mockCallback).onSuccess(isNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchWithoutCallback() throws Throwable {
        Executor executorService = mock(ExecutorService.class);
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(executorService).execute(isA(Runnable.class));
        AsynchronousCommandBus commandBus = new AsynchronousCommandBus(executorService);
        CommandHandler commandHandler = mock(CommandHandler.class);
        commandBus.subscribe(Object.class, commandHandler);
        commandBus.dispatch(asCommandMessage(new Object()));

        verify(executorService).execute(isA(Runnable.class));
        verify(commandHandler).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
    }

    @Test
    public void testShutdown_ExecutorServiceUsed() {
        ExecutorService executor = mock(ExecutorService.class);
        new AsynchronousCommandBus(executor).shutdown();

        verify(executor).shutdown();
    }

    @Test
    public void testShutdown_ExecutorUsed() {
        Executor executor = mock(Executor.class);
        new AsynchronousCommandBus(executor).shutdown();

        verify(executor, never()).execute(any(Runnable.class));
    }
}
