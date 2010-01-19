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

import org.axonframework.core.DomainEvent;
import org.axonframework.core.StubDomainEvent;
import org.junit.*;
import org.springframework.integration.message.GenericMessage;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class MessageHandlerAdapterTest {

    @SuppressWarnings({"unchecked"})
    @Test
    public void testMessageForwarded() {
        EventListener mockEventListener = mock(EventListener.class);
        MessageHandlerAdapter adapter = new MessageHandlerAdapter(mockEventListener);

        StubDomainEvent payload = new StubDomainEvent();
        when(mockEventListener.canHandle(isA(Class.class))).thenReturn(true).thenReturn(false);
        adapter.handleMessage(new GenericMessage<DomainEvent>(payload));
        adapter.handleMessage(new GenericMessage<DomainEvent>(new StubDomainEvent()));

        verify(mockEventListener, times(1)).handle(payload);
        verify(mockEventListener, times(2)).canHandle(StubDomainEvent.class);
        verifyNoMoreInteractions(mockEventListener);
    }

}
