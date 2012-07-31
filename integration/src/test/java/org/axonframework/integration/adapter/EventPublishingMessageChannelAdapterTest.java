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

package org.axonframework.integration.adapter;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.integration.StubDomainEvent;
import org.junit.*;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.message.GenericMessage;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventPublishingMessageChannelAdapterTest {

    private EventBus mockEventBus;
    private EventPublishingMessageChannelAdapter testSubject;
    private EventFilter mockFilter;

    @Before
    public void setUp() {
        mockEventBus = mock(EventBus.class);
        testSubject = new EventPublishingMessageChannelAdapter(mockEventBus);
        mockFilter = mock(EventFilter.class);
    }

    @Test
    public void testMessagePayloadIsPublished() {
        StubDomainEvent event = new StubDomainEvent();
        testSubject.handleMessage(new GenericMessage<Object>(event));

        verify(mockEventBus).publish(isA(EventMessage.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test(expected = MessageRejectedException.class)
    public void testFilterRefusesEventMessage() {
        when(mockFilter.accept(isA(Class.class))).thenReturn(false);
        testSubject = new EventPublishingMessageChannelAdapter(mockEventBus, mockFilter);

        testSubject.handleMessage(new GenericMessage<Object>(new StubDomainEvent()));
    }
}
