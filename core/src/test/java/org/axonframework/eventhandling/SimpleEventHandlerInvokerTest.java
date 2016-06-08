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

import org.junit.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * @author Rene de Waele
 */
public class SimpleEventHandlerInvokerTest {

    @Test
    public void testSingleEventPublication() throws Exception {
        EventListener mockListener1 = mock(EventListener.class);
        EventListener mockListener2 = mock(EventListener.class);
        SimpleEventHandlerInvoker subject = new SimpleEventHandlerInvoker("test", mockListener1, mockListener2);

        EventMessage<?> event = createEvent();
        subject.handle(event);
        InOrder inOrder = inOrder(mockListener1, mockListener2);
        inOrder.verify(mockListener1).handle(event);
        inOrder.verify(mockListener2).handle(event);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRepeatedEventPublication() throws Exception {
        EventListener mockListener1 = mock(EventListener.class);
        EventListener mockListener2 = mock(EventListener.class);
        SimpleEventHandlerInvoker subject = new SimpleEventHandlerInvoker("test", mockListener1, mockListener2);

        List<? extends EventMessage<?>> events = createEvents(2);
        for (EventMessage<?> event : events) {
            subject.handle(event);
        }
        InOrder inOrder = inOrder(mockListener1, mockListener2);
        inOrder.verify(mockListener1).handle(events.get(0));
        inOrder.verify(mockListener2).handle(events.get(0));
        inOrder.verify(mockListener1).handle(events.get(1));
        inOrder.verify(mockListener2).handle(events.get(1));
        inOrder.verifyNoMoreInteractions();
    }
}
