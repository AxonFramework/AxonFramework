/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler;

import org.axonframework.core.StubDomainEvent;
import org.axonframework.core.eventhandler.annotation.ConcurrentEventListener;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@ConcurrentEventListener
public class AsyncEventBusTest {

    private AsyncEventBus testSubject;
    private ExecutorService mockExecutor;

    @Before
    public void setUp() {
        testSubject = spy(new AsyncEventBus());
        mockExecutor = mock(ExecutorService.class);
        testSubject.setExecutor(mockExecutor);
    }

    @Test
    public void testInitialize_Defaults() throws Exception {
        testSubject = new AsyncEventBus();
        testSubject.start();

        Object actual = getFieldValue(testSubject, "executor");
        assertTrue(actual instanceof ThreadPoolExecutor);
    }

    @Test
    public void testDispatchEvent() {
        org.axonframework.core.eventhandler.EventListener mockEventListener = mock(org.axonframework.core.eventhandler.EventListener.class);
        when(mockEventListener.canHandle(StubDomainEvent.class)).thenReturn(true);
        when(mockEventListener.getEventSequencingPolicy()).thenReturn(new SequentialPerAggregatePolicy());
        testSubject.start();
        testSubject.subscribe(mockEventListener);
        testSubject.publish(new StubDomainEvent());

        verify(mockExecutor).execute(isA(Runnable.class));

        testSubject.unsubscribe(mockEventListener);
        testSubject.publish(new StubDomainEvent());
        verifyNoMoreInteractions(mockExecutor);
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testDispatchEvent_TransactionControlOnFullConcurrencyPolicy() {
        // Tests Issue #9 - Transaction ignored with FullConcurrencyPolicy
        // The scheduler should be used, even if there can only be 1 event to handle
        TransactionalEventListener mockEventListener = mock(TransactionalEventListener.class);
        when(mockEventListener.canHandle(isA(Class.class))).thenReturn(true);
        when(mockEventListener.getEventSequencingPolicy()).thenReturn(new FullConcurrencyPolicy());
        testSubject.start();
        testSubject.subscribe(mockEventListener);
        testSubject.publish(new StubDomainEvent());

        verify(mockExecutor).execute(isA(EventProcessingScheduler.class));

        testSubject.unsubscribe(mockEventListener);
        testSubject.publish(new StubDomainEvent());
        verifyNoMoreInteractions(mockExecutor);
    }

    @Test
    public void testExecutorNotShutDownOnDestroy() throws Exception {
        // the executor must only be shutdown if the event bus created it.
        testSubject.stop();
        verify(mockExecutor, never()).shutdown();
    }

    @Test
    public void testExecutorShutDownOnDestroy() throws Exception {
        // the executor must only be shutdown if the event bus created it.
        testSubject.setShutdownExecutorServiceOnStop(true);
        testSubject.stop();
        verify(mockExecutor).shutdown();
    }

    private Object getFieldValue(AsyncEventBus testSubject, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = testSubject.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(testSubject);
    }

    private interface TransactionalEventListener extends EventListener, TransactionAware {

    }
}
