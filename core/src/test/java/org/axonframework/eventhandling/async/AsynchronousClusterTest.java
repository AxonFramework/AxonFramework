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

package org.axonframework.eventhandling.async;

import org.axonframework.common.DirectExecutor;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.TransactionManager;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.Executor;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousClusterTest {

    private TransactionManager mockTransactionManager;
    private Executor executor;
    private AsynchronousCluster testSubject;

    @Before
    public void setUp() throws Exception {
        mockTransactionManager = mock(TransactionManager.class);
        executor = mock(Executor.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(executor).execute(isA(Runnable.class));
        testSubject = new AsynchronousCluster("async", executor, mockTransactionManager,
                                              new SequentialPerAggregatePolicy(),
                                              new DefaultErrorHandler(RetryPolicy.proceed()));
    }

    @Test
    public void testSimpleConfig_ProceedOnFailure() {
        testSubject = new AsynchronousCluster("async", new DirectExecutor(), new SequentialPerAggregatePolicy());

        EventListener mockEventListener = mock(EventListener.class);
        testSubject.subscribe(mockEventListener);

        doThrow(new MockException()).when(mockEventListener).handle(isA(EventMessage.class));

        testSubject.publish(asEventMessage(new Object()));
        testSubject.publish(asEventMessage(new Object()));

        verify(mockEventListener, times(2)).handle(isA(EventMessage.class));
    }

    @Test
    public void testSubscriptions() throws Exception {
        EventListener mockEventListener = mock(EventListener.class);
        testSubject.subscribe(mockEventListener);
        assertTrue(testSubject.getMembers().contains(mockEventListener));

        testSubject.unsubscribe(mockEventListener);
        assertFalse(testSubject.getMembers().contains(mockEventListener));
    }

    @Test
    public void testEventsScheduledForHandling() {
        final GenericEventMessage<String> message1 = new GenericEventMessage<String>("Message 1");
        final GenericEventMessage<String> message2 = new GenericEventMessage<String>("Message 2");

        testSubject.publish(message1, message2);

        verify(executor, times(2)).execute(isA(Runnable.class));
        verify(mockTransactionManager, times(2)).startTransaction();
        verify(mockTransactionManager, times(2)).commitTransaction(any());
    }

    @Test
    public void testAllEventHandlersInvokedBeforePropagatingExceptions() {
        EventListener mockEventListener1 = mock(EventListener.class);
        EventListener mockEventListener2 = mock(EventListener.class);
        EventListener mockEventListener3 = mock(EventListener.class);

        testSubject.subscribe(mockEventListener1);
        testSubject.subscribe(mockEventListener2);
        testSubject.subscribe(mockEventListener3);

        doThrow(new MockException()).when(mockEventListener1).handle(isA(EventMessage.class));
        doThrow(new MockException()).when(mockEventListener2).handle(isA(EventMessage.class));
        doThrow(new MockException()).when(mockEventListener3).handle(isA(EventMessage.class));

        final GenericEventMessage message = new GenericEventMessage("test");
        testSubject.publish(message);

        verify(mockEventListener1).handle(message);
        verify(mockEventListener2).handle(message);
        verify(mockEventListener3).handle(message);
    }
}
