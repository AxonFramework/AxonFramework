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

package org.axonframework.eventhandling;

import org.axonframework.domain.GenericEventMessage;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousClusterTest {

    private TransactionManager mockTransactionManager;
    private Executor executor;
    private AsynchronousCluster testSubject;
    private Cluster mockCluster;

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
        mockCluster = mock(Cluster.class);
        testSubject = new AsynchronousCluster(executor, mockTransactionManager, new SequentialPerAggregatePolicy(),
                                              mockCluster, RetryPolicy.SKIP_FAILED_EVENT, 10, 100);
    }

    @Test
    public void testSubscriptionsDelegatedToUnderlyingCluster() throws Exception {
        EventListener mockEventListener = mock(EventListener.class);
        testSubject.subscribe(mockEventListener);
        verify(mockCluster).subscribe(mockEventListener);

        testSubject.unsubscribe(mockEventListener);
        verify(mockCluster).unsubscribe(mockEventListener);

        testSubject.getMembers();
        verify(mockCluster).getMembers();

        testSubject.getMetaData();
        verify(mockCluster).getMetaData();
    }

    @Test
    public void testEventsScheduledForHandling() {
        final GenericEventMessage<String> message1 = new GenericEventMessage<String>("Message 1");
        final GenericEventMessage<String> message2 = new GenericEventMessage<String>("Message 2");

        testSubject.publish(message1, message2);

        // events are split into different calls because there might be different sequencing requirements for each
        verify(mockCluster).publish(message1);
        verify(mockCluster).publish(message2);

        verify(executor, times(2)).execute(isA(Runnable.class));
        verify(mockTransactionManager, times(2)).startTransaction();
        verify(mockTransactionManager, times(2)).commitTransaction(any());
    }
}
