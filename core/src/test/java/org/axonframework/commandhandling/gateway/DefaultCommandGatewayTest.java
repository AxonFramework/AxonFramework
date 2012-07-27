package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DefaultCommandGatewayTest {

    private DefaultCommandGateway testSubject;
    private CommandBus mockCommandBus;
    private RetryScheduler mockRetryScheduler;
    private CommandDispatchInterceptor mockCommandMessageTransformer;

    @Before
    public void setUp() throws Exception {
        mockCommandBus = mock(CommandBus.class);
        mockRetryScheduler = mock(RetryScheduler.class);
        mockCommandMessageTransformer = mock(CommandDispatchInterceptor.class);
        when(mockCommandMessageTransformer.handle(isA(CommandMessage.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });
        testSubject = new DefaultCommandGateway(mockCommandBus, mockRetryScheduler, mockCommandMessageTransformer);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendWithCallback_CommandIsRetried() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((CommandCallback) invocation.getArguments()[1]).onFailure(new RuntimeException(new RuntimeException()));
                return null;
            }
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);
        final AtomicReference<Object> actualResult = new AtomicReference<Object>();
        testSubject.send("Command", new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                actualResult.set(result);
            }

            @Override
            public void onFailure(Throwable cause) {
                actualResult.set(cause);
            }
        });
        verify(mockCommandMessageTransformer).handle(isA(CommandMessage.class));
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertTrue(actualResult.get() instanceof RuntimeException);
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[])captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendWithoutCallback_CommandIsRetried() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((CommandCallback) invocation.getArguments()[1]).onFailure(new RuntimeException(new RuntimeException()));
                return null;
            }
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);

        testSubject.send("Command");

        verify(mockCommandMessageTransformer).handle(isA(CommandMessage.class));
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[])captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendAndWait_CommandIsRetried() {
        final RuntimeException failure = new RuntimeException(new RuntimeException());
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((CommandCallback) invocation.getArguments()[1]).onFailure(failure);
                return null;
            }
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
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[])captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendAndWaitWithTimeout_CommandIsRetried() {
        final RuntimeException failure = new RuntimeException(new RuntimeException());
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((CommandCallback) invocation.getArguments()[1]).onFailure(failure);
                return null;
            }
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
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[])captor.getValue().get(0)).length);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendAndWait_NullOnInterrupt() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Thread.currentThread().interrupt();
                return null;
            }
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        assertNull(testSubject.sendAndWait("Hello"));
        assertTrue("Interrupt flag should be set on thread", Thread.interrupted());
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendAndWaitWithTimeout_NullOnInterrupt() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Thread.currentThread().interrupt();
                return null;
            }
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        assertNull(testSubject.sendAndWait("Hello", 60, TimeUnit.SECONDS));
        assertTrue("Interrupt flag should be set on thread", Thread.interrupted());
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendAndWaitWithTimeout_NullOnTimeout() {
        assertNull(testSubject.sendAndWait("Hello", 10, TimeUnit.MILLISECONDS));
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    private static class RescheduleCommand implements Answer<Boolean> {

        @Override
        public Boolean answer(InvocationOnMock invocation) throws Throwable {
            ((Runnable)invocation.getArguments()[3]).run();
            return true;
        }
    }
}
