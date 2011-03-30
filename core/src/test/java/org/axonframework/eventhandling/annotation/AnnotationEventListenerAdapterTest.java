/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.SequencingPolicy;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.util.DirectExecutor;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationEventListenerAdapterTest {

    @Test
    public void testHandlerCorrectlyUsed() {
        AnnotatedEventHandler handler = mock(AnnotatedEventHandler.class);
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(handler, null);

        TransactionStatus transactionStatus = new TransactionStatus() {
        };
        StubDomainEvent event = new StubDomainEvent();
        adapter.beforeTransaction(transactionStatus);
        adapter.handle(event);
        adapter.afterTransaction(transactionStatus);

        verify(handler).handleEvent(event);
    }

    @Test
    public void testAdaptAsyncEventHandler_NoExecutor() {
        AsyncAnnotatedEventHandler handler = mock(AsyncAnnotatedEventHandler.class);
        try {
            new AnnotationEventListenerAdapter(handler, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("annotatedEventListener"));
            assertTrue(e.getMessage().contains("executor"));
        }
    }

    @Test
    public void testAdaptAsyncEventHandler_WithExecutor() {
        AsyncAnnotatedEventHandler handler = mock(AsyncAnnotatedEventHandler.class);
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(handler,
                                                                                    DirectExecutor.INSTANCE,
                                                                                    null);

        TransactionStatus transactionStatus = new TransactionStatus() {
        };
        StubDomainEvent event = new StubDomainEvent();
        adapter.beforeTransaction(transactionStatus);
        adapter.handle(event);
        adapter.afterTransaction(transactionStatus);

        verify(handler).beforeTransaction(transactionStatus);
        verify(handler).handleEvent(event);
        verify(handler).afterTransaction(transactionStatus);
    }

    @Test
    public void testAdaptSyncTransactionAwareEventHandler() {
        TransactionAwareSyncHandler handler = mock(TransactionAwareSyncHandler.class);
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(handler, null);

        TransactionStatus transactionStatus = new TransactionStatus() {
        };
        StubDomainEvent event = new StubDomainEvent();
        adapter.beforeTransaction(transactionStatus);
        adapter.handle(event);
        adapter.afterTransaction(transactionStatus);

        verify(handler).beforeTransaction(transactionStatus);
        verify(handler).handleEvent(event);
        verify(handler).afterTransaction(transactionStatus);
    }

    @Test
    public void testAdaptClassWithIllegalPolicy() {
        AsyncAnnotatedEventHandler_IllegalPolicy handler = new AsyncAnnotatedEventHandler_IllegalPolicy();
        try {
            new AnnotationEventListenerAdapter(handler, DirectExecutor.INSTANCE, null);
            fail("Expected UnsupportedPolicyException");
        } catch (UnsupportedPolicyException e) {
            assertTrue(e.getMessage().contains("no-arg constructor"));
        }
    }

    @Test
    public void testManagerWithAnnotatedMethods() {
        AnnotatedTransactionMethods bean = new AnnotatedTransactionMethods();
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(bean, null);
        adapter.beforeTransaction(null);
        adapter.afterTransaction(null);

        assertEquals(1, bean.beforeCallCount);
        assertEquals(1, bean.afterCallCount);
    }

    @Test
    public void testManagerWithField() {
        TransactionManagerField bean = new TransactionManagerField();
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(bean, null);
        adapter.beforeTransaction(null);
        adapter.afterTransaction(null);

        assertEquals(1, bean.getBeforeCalls());
        assertEquals(1, bean.getAfterCalls());
    }

    public static class AnnotatedTransactionMethods {

        private int beforeCallCount = 0;
        private int afterCallCount = 0;

        @BeforeTransaction
        public void beforeTransaction() {
            beforeCallCount++;
        }

        @AfterTransaction
        public void afterTransaction() {
            afterCallCount++;
        }
    }

    public static class TransactionManagerField {

        @TransactionManager
        private RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        int getBeforeCalls() {
            return transactionManager.beforeCallCount;
        }

        int getAfterCalls() {
            return transactionManager.afterCallCount;
        }
    }

    private static class RecordingTransactionManager implements org.axonframework.eventhandling.TransactionManager {

        private int beforeCallCount = 0;
        private int afterCallCount = 0;

        @Override
        public void beforeTransaction(TransactionStatus status) {
            beforeCallCount++;
        }

        @Override
        public void afterTransaction(TransactionStatus status) {
            afterCallCount++;
        }
    }

    private static class AnnotatedEventHandler {

        @EventHandler
        public void handleEvent(Event event) {
        }
    }

    @AsynchronousEventListener
    private static class AsyncAnnotatedEventHandler {

        @EventHandler
        public void handleEvent(Event event) {
        }

        @BeforeTransaction
        public void beforeTransaction(TransactionStatus status) {

        }

        @AfterTransaction
        public void afterTransaction(TransactionStatus status) {

        }
    }

    @AsynchronousEventListener(sequencingPolicyClass = WrongPolicy.class)
    private static class AsyncAnnotatedEventHandler_IllegalPolicy {

        @EventHandler
        public void handleEvent(Event event) {
        }

        @BeforeTransaction
        public void beforeTransaction(TransactionStatus status) {

        }

        @AfterTransaction
        public void afterTransaction(TransactionStatus status) {

        }
    }

    private static class TransactionAwareSyncHandler {

        @EventHandler
        public void handleEvent(Event event) {
        }

        @BeforeTransaction
        public void beforeTransaction(TransactionStatus status) {

        }

        @AfterTransaction
        public void afterTransaction(TransactionStatus status) {

        }
    }

    private class WrongPolicy implements SequencingPolicy<Event> {

        public WrongPolicy(Object anyParameter) {
            // this constructor makes it unsuitable as policy class
        }

        @Override
        public Object getSequenceIdentifierFor(Event event) {
            return null;
        }
    }
}
