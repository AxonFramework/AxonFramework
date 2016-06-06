/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class PublishingEventProcessorTest {

    @Test
    public void testSingleEventPublication() throws Exception {
        EventListener mockListener1 = mock(EventListener.class);
        EventListener mockListener2 = mock(EventListener.class);
        EventProcessor subject = new PublishingEventProcessor("test", mockListener1, mockListener2);

        EventMessage<?> event = createEvent();
        subject.accept(Collections.singletonList(event));
        InOrder inOrder = inOrder(mockListener1, mockListener2);
        inOrder.verify(mockListener1).handle(event);
        inOrder.verify(mockListener2).handle(event);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testEventListPublication() throws Exception {
        EventListener mockListener1 = mock(EventListener.class);
        EventListener mockListener2 = mock(EventListener.class);
        EventProcessor subject = new PublishingEventProcessor("test", mockListener1, mockListener2);

        List<? extends EventMessage<?>> events = createEvents(2);
        subject.accept(events);
        InOrder inOrder = inOrder(mockListener1, mockListener2);
        inOrder.verify(mockListener1).handle(events.get(0));
        inOrder.verify(mockListener2).handle(events.get(0));
        inOrder.verify(mockListener1).handle(events.get(1));
        inOrder.verify(mockListener2).handle(events.get(1));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRepeatedEventPublication() throws Exception {
        EventListener mockListener1 = mock(EventListener.class);
        EventListener mockListener2 = mock(EventListener.class);
        EventProcessor subject = new PublishingEventProcessor("test", mockListener1, mockListener2);

        EventMessage<?> event1 = createEvent();
        EventMessage<?> event2 = createEvent();
        subject.accept(Collections.singletonList(event1));
        subject.accept(Collections.singletonList(event2));
        InOrder inOrder = inOrder(mockListener1, mockListener2);
        inOrder.verify(mockListener1).handle(event1);
        inOrder.verify(mockListener2).handle(event1);
        inOrder.verify(mockListener1).handle(event2);
        inOrder.verify(mockListener2).handle(event2);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInterceptorInvokedOncePerMessage() throws Exception {
        EventListener mockListener1 = mock(EventListener.class);
        EventListener mockListener2 = mock(EventListener.class);
        EventProcessor subject = new PublishingEventProcessor("test", mockListener1, mockListener2);
        MessageHandlerInterceptor<EventMessage<?>> mockInterceptor = mock(MessageHandlerInterceptor.class);
        when(mockInterceptor.handle(any(), any())).then(invocation -> {
            InterceptorChain interceptorChain = (InterceptorChain) invocation.getArguments()[1];
            return interceptorChain.proceed();
        });
        Registration registration = subject.registerInterceptor(mockInterceptor);

        EventMessage<?> event = createEvent();
        subject.accept(Collections.singletonList(event));
        InOrder inOrder = inOrder(mockInterceptor, mockListener1, mockListener2);
        inOrder.verify(mockInterceptor).handle(any(), any());
        inOrder.verify(mockListener1).handle(event);
        inOrder.verify(mockListener2).handle(event);
        inOrder.verifyNoMoreInteractions();

        reset(mockInterceptor, mockListener1, mockListener2);
        registration.close();
        subject.accept(Collections.singletonList(event));
        inOrder.verify(mockInterceptor, never()).handle(any(), any());
        inOrder.verify(mockListener1).handle(event);
        inOrder.verify(mockListener2).handle(event);
        inOrder.verifyNoMoreInteractions();
    }

}
