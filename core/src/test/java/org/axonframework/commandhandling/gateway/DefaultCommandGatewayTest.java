/*
 * Copyright (c) 2010-2014. Axon Framework
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
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.correlation.CorrelationDataHolder;
import org.hamcrest.CustomTypeSafeMatcher;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
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
        CorrelationDataHolder.clear();
        mockCommandBus = mock(CommandBus.class);
        mockRetryScheduler = mock(RetryScheduler.class);
        mockCommandMessageTransformer = mock(CommandDispatchInterceptor.class);
        when(mockCommandMessageTransformer.handle(isA(CommandMessage.class))).thenAnswer(invocation -> invocation.getArguments()[0]);
        testSubject = new DefaultCommandGateway(mockCommandBus, mockRetryScheduler, mockCommandMessageTransformer);
    }

    @After
    public void tearDown() throws Exception {
        CorrelationDataHolder.clear();
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendWithCallback_CommandIsRetried() {
        doAnswer(invocation -> {
            ((CommandCallback) invocation.getArguments()[1])
                    .onFailure(new RuntimeException(new RuntimeException()));
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        when(mockRetryScheduler.scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class), isA(List.class),
                                              isA(Runnable.class)))
                .thenAnswer(new RescheduleCommand())
                .thenReturn(false);
        final AtomicReference<Object> actualResult = new AtomicReference<>();
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
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendWithoutCallback_CommandIsRetried() {
        doAnswer(invocation -> {
            ((CommandCallback) invocation.getArguments()[1])
                    .onFailure(new RuntimeException(new RuntimeException()));
            return null;
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
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendAndWait_CommandIsRetried() {
        final RuntimeException failure = new RuntimeException(new RuntimeException());
        doAnswer(invocation -> {
            ((CommandCallback) invocation.getArguments()[1]).onFailure(failure);
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
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockRetryScheduler, times(2)).scheduleRetry(isA(CommandMessage.class), isA(RuntimeException.class),
                                                           captor.capture(), isA(Runnable.class));
        verify(mockCommandBus, times(2)).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(2, captor.getValue().size());
        assertEquals(2, ((Class<? extends Throwable>[]) captor.getValue().get(0)).length);
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    public void testSendAndWaitWithTimeout_CommandIsRetried() {
        final RuntimeException failure = new RuntimeException(new RuntimeException());
        doAnswer(invocation -> {
            ((CommandCallback) invocation.getArguments()[1]).onFailure(failure);
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
    public void testSendAndWait_NullOnInterrupt() {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));

        assertNull(testSubject.sendAndWait("Hello"));
        assertTrue("Interrupt flag should be set on thread", Thread.interrupted());
        verify(mockCommandBus).dispatch(isA(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendAndWaitWithTimeout_NullOnInterrupt() {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
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

    @SuppressWarnings("unchecked")
    @Test
    public void testCorrelationDataIsAttachedToCommandAsObject() throws Exception {
        CorrelationDataHolder.setCorrelationData(Collections.singletonMap("correlationId", "test"));
        testSubject.send("Hello");

        verify(mockCommandBus).dispatch(argThat(new CustomTypeSafeMatcher<CommandMessage<?>>("header correlationId") {
            @Override
            protected boolean matchesSafely(CommandMessage<?> item) {
                return "test".equals(item.getMetaData().get("correlationId"));
            }
        }), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCorrelationDataIsAttachedToCommandAsMessage() throws Exception {
        final Map<String, String> data = new HashMap<>();
        data.put("correlationId", "test");
        data.put("header", "someValue");
        CorrelationDataHolder.setCorrelationData(data);
        testSubject.send(new GenericCommandMessage<>("Hello", Collections.singletonMap("header", "value")));

        verify(mockCommandBus).dispatch(argThat(new CustomTypeSafeMatcher<CommandMessage<?>>(
                "header 'correlationId' and 'header'") {
            @Override
            protected boolean matchesSafely(CommandMessage<?> item) {
                return "test".equals(item.getMetaData().get("correlationId"))
                        && "value".equals(item.getMetaData().get("header"));
            }
        }), isA(CommandCallback.class));
    }

    private static class RescheduleCommand implements Answer<Boolean> {

        @Override
        public Boolean answer(InvocationOnMock invocation) throws Throwable {
            ((Runnable) invocation.getArguments()[3]).run();
            return true;
        }
    }
}
