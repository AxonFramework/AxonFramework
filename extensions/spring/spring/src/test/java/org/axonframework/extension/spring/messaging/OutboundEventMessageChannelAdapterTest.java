/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.extension.spring.messaging;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.extension.spring.utils.StubDomainEvent;
import org.junit.jupiter.api.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link OutboundEventMessageChannelAdapter}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class OutboundEventMessageChannelAdapterTest {

    private OutboundEventMessageChannelAdapter testSubject;
    private EventBus mockEventBus;
    private MessageChannel mockChannel;

    @BeforeEach
    void setUp() {
        mockEventBus = mock(EventBus.class);
        mockChannel = mock(MessageChannel.class);
        testSubject = new OutboundEventMessageChannelAdapter(mockEventBus, mockChannel);
    }

    @Test
    void messageForwardedToChannel() {
        StubDomainEvent event = new StubDomainEvent();
        EventMessage testMessage =
                new GenericEventMessage(new MessageType("event"), event);
        testSubject.handle(singletonList(testMessage), null);

        verify(mockChannel).send(messageWithPayload(event));
    }

    @Test
    void eventListenerRegisteredOnInit() {
        verify(mockEventBus, never()).subscribe(any());
        testSubject.afterPropertiesSet();
        verify(mockEventBus).subscribe(any());
    }

    @Test
    void filterBlocksEvents() {
        testSubject = new OutboundEventMessageChannelAdapter(
                mockEventBus, mockChannel, m -> !m.payloadType().isAssignableFrom(Class.class)
        );
        testSubject.handle(singletonList(newDomainEvent()), null);
        verify(mockEventBus, never()).publish(any(ProcessingContext.class), isA(EventMessage.class));
    }

    private EventMessage newDomainEvent() {
        return new GenericEventMessage(new MessageType("event"), "Mock");
    }

    private Message messageWithPayload(final StubDomainEvent event) {
        return argThat(x -> event.equals(x.getPayload()));
    }
}
