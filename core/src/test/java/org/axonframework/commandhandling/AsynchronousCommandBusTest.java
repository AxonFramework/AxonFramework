package org.axonframework.commandhandling;

import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousCommandBusTest {

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
        commandBus.dispatch(new Object(), mockCallback);

        verify(executorService).execute(isA(Runnable.class));
        verify(commandHandler).handle(isA(Object.class), isA(UnitOfWork.class));
        verify(mockCallback).onSuccess(isNull());
    }

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
        commandBus.dispatch(new Object());

        verify(executorService).execute(isA(Runnable.class));
        verify(commandHandler).handle(isA(Object.class), isA(UnitOfWork.class));
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
