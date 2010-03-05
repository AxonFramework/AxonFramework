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

package org.axonframework.integration.adapter;

import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.integration.StubDomainEvent;
import org.junit.*;
import org.mockito.*;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;

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
    }

    @Test
    public void testMessageForwardedToChannel() {
        StubDomainEvent event = new StubDomainEvent();
        testSubject.handle(event);

        verify(mockChannel).send(messageWithPayload(event));
    }

    @Test
    public void testEventListenerRegisteredOnInit() throws Exception {
        verify(mockEventBus, never()).subscribe(testSubject);
        testSubject.afterPropertiesSet();
        verify(mockEventBus).subscribe(testSubject);
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testFilterBlocksEvents() throws Exception {
        when(mockFilter.accept(isA(Class.class))).thenReturn(false);
        testSubject = new EventListeningMessageChannelAdapter(mockEventBus, mockChannel, mockFilter);
        testSubject.handle(new StubDomainEvent());
        verify(mockEventBus, never()).publish(isA(Event.class));
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
