/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.messaging.adapter;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.spring.messaging.StubDomainEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventListeningMessageChannelAdapterTest {

    private EventListeningMessageChannelAdapter testSubject;
    private EventBus mockEventBus;
    private MessageChannel mockChannel;
    private EventFilter mockFilter;

    @Before
    public void setUp() {
        mockEventBus = mock(EventBus.class);
        mockChannel = mock(MessageChannel.class);
        mockFilter = mock(EventFilter.class);
        testSubject = new EventListeningMessageChannelAdapter(mockEventBus, mockChannel);
        testSubject.setBeanName("stub");
    }

    @Test
    public void testMessageForwardedToChannel() {
        StubDomainEvent event = new StubDomainEvent();
        testSubject.handle(new GenericEventMessage<>(event));

        verify(mockChannel).send(messageWithPayload(event));
    }

    @Test
    public void testEventListenerRegisteredOnInit() throws Exception {
        verify(mockEventBus, never()).subscribe(any());
        testSubject.afterPropertiesSet();
        verify(mockEventBus).subscribe(any());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testFilterBlocksEvents() throws Exception {
        when(mockFilter.accept(isA(Class.class))).thenReturn(false);
        testSubject = new EventListeningMessageChannelAdapter(mockEventBus, mockChannel, mockFilter);
        testSubject.handle(newDomainEvent());
        verify(mockEventBus, never()).publish(isA(EventMessage.class));
    }

    private EventMessage<String> newDomainEvent() {
        return new GenericEventMessage<>("Mock");
    }

    private Message<?> messageWithPayload(final StubDomainEvent event) {
        return argThat(new ArgumentMatcher<Message<?>>() {
            @Override
            public boolean matches(Object argument) {
                return event.equals(((Message) argument).getPayload());
            }
        });
    }
}
