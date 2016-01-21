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

import org.axonframework.common.Registration;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleEventProcessorTest {

    private SimpleEventProcessor testSubject;
    private EventListener eventListener;

    @Before
    public void setUp() throws Exception {
        testSubject = new SimpleEventProcessor("eventProcessor");
        eventListener = mock(EventListener.class);
    }

    @Test
    public void testSubscribeMember() throws Exception {
        testSubject.subscribe(eventListener);
        EventMessage<?> eventMessage = new GenericEventMessage<>("Some event");
        testSubject.handle(eventMessage);
        verify(eventListener).handle(eventMessage);
    }

    @Test
    public void testSubscribeOrderedMembers() throws Exception {
        OrderResolver mockOrderResolver = mock(OrderResolver.class);
        EventListener eventListener2 = mock(EventListener.class);
        when(mockOrderResolver.orderOf(eventListener)).thenReturn(1);
        when(mockOrderResolver.orderOf(eventListener2)).thenReturn(2);
        testSubject = new SimpleEventProcessor("eventProcessor", mockOrderResolver);
        testSubject.subscribe(eventListener2);
        testSubject.subscribe(eventListener);
        EventMessage<?> eventMessage = new GenericEventMessage<>("Some event");
        testSubject.handle(eventMessage);
        // the eventListener instance must come first
        InOrder inOrder = inOrder(eventListener, eventListener2);
        inOrder.verify(eventListener).handle(eventMessage);
        inOrder.verify(eventListener2).handle(eventMessage);
    }

    @Test
    public void testUnsubscribeMember() {
        testSubject.subscribe(eventListener).cancel();
        testSubject.handle(new GenericEventMessage<>("Some event"));
        verifyZeroInteractions(eventListener);
    }

    @Test
    public void testMetaDataAvailable() {
        assertNotNull(testSubject.getMetaData());
    }

    @Test
    public void testPublishEvent() throws Exception {
        testSubject.subscribe(eventListener);
        EventMessage event = new GenericEventMessage<>(new Object());
        testSubject.handle(event);

        verify(eventListener).handle(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRegisterInterceptor() throws Exception {
        testSubject.subscribe(eventListener);
        MessageHandlerInterceptor<EventMessage<?>> interceptor = mock(MessageHandlerInterceptor.class);
        Registration registration = testSubject.registerInterceptor(interceptor);
        EventMessage event = new GenericEventMessage<>(new Object());
        when(interceptor.handle(any(), any())).then(invocation -> {
            ((InterceptorChain<EventMessage<?>>) invocation.getArguments()[1]).proceed();
            return null;
        });
        testSubject.handle(event, event);

        verify(eventListener, times(2)).handle(event);
        verify(interceptor, times(2)).handle(any(UnitOfWork.class), any(InterceptorChain.class));

        registration.cancel();
        testSubject.handle(event);

        verifyNoMoreInteractions(interceptor);
    }
}
