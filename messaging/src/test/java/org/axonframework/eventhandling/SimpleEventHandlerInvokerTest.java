/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.junit.*;
import org.mockito.*;

import java.util.List;

import static org.axonframework.utils.EventTestUtils.createEvent;
import static org.axonframework.utils.EventTestUtils.createEvents;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class SimpleEventHandlerInvokerTest {

    @Test
    public void testSingleEventPublication() throws Exception {
        EventMessageHandler mockHandler1 = mock(EventMessageHandler.class);
        EventMessageHandler mockHandler2 = mock(EventMessageHandler.class);
        SimpleEventHandlerInvoker subject =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers("test", mockHandler1, mockHandler2)
                                         .build();

        EventMessage<?> event = createEvent();
        subject.handle(event, Segment.ROOT_SEGMENT);
        InOrder inOrder = inOrder(mockHandler1, mockHandler2);
        inOrder.verify(mockHandler1).handle(event);
        inOrder.verify(mockHandler2).handle(event);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRepeatedEventPublication() throws Exception {
        EventMessageHandler mockHandler1 = mock(EventMessageHandler.class);
        EventMessageHandler mockHandler2 = mock(EventMessageHandler.class);
        SimpleEventHandlerInvoker subject =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers("test", mockHandler1, mockHandler2)
                                         .build();

        List<? extends EventMessage<?>> events = createEvents(2);
        for (EventMessage<?> event : events) {
            subject.handle(event, Segment.ROOT_SEGMENT);
        }
        InOrder inOrder = inOrder(mockHandler1, mockHandler2);
        inOrder.verify(mockHandler1).handle(events.get(0));
        inOrder.verify(mockHandler2).handle(events.get(0));
        inOrder.verify(mockHandler1).handle(events.get(1));
        inOrder.verify(mockHandler2).handle(events.get(1));
        inOrder.verifyNoMoreInteractions();
    }
}
