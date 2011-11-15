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

package org.axonframework.integration.eventbus;

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.integration.StubDomainEvent;
import org.junit.*;
import org.mockito.*;
import org.springframework.integration.Message;
import org.springframework.integration.core.MessageHandler;
import org.springframework.integration.core.SubscribableChannel;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringIntegrationEventBusTest {

    private SpringIntegrationEventBus testSubject;
    private EventListener mockListener;
    private SubscribableChannel mockChannel;

    @Before
    public void setUp() {
        testSubject = new SpringIntegrationEventBus();
        mockListener = mock(EventListener.class);
        mockChannel = mock(SubscribableChannel.class);
        testSubject.setChannel(mockChannel);
    }

    @Test
    public void testSubscribeListener() {
        testSubject.subscribe(mockListener);

        verify(mockChannel).subscribe(isA(MessageHandler.class));
    }

    @Test
    public void testUnsubscribeListener() {
        testSubject.unsubscribe(mockListener);

        verify(mockChannel, never()).unsubscribe(isA(MessageHandler.class));

        testSubject.subscribe(mockListener);
        testSubject.unsubscribe(mockListener);

        verify(mockChannel).unsubscribe(isA(MessageHandler.class));
    }

    @Test
    public void testUnsubscribeListener_UnsubscribedTwice() {
        testSubject.unsubscribe(mockListener);

        verify(mockChannel, never()).unsubscribe(isA(MessageHandler.class));

        testSubject.subscribe(mockListener);
        testSubject.unsubscribe(mockListener);
        testSubject.unsubscribe(mockListener);

        verify(mockChannel).unsubscribe(any(MessageHandler.class));
    }

    @Test
    public void testSubscribeListener_SubscribedTwice() {
        testSubject.unsubscribe(mockListener);

        verify(mockChannel, never()).unsubscribe(isA(MessageHandler.class));

        testSubject.subscribe(mockListener);
        testSubject.subscribe(mockListener);

        verify(mockChannel).subscribe(isA(MessageHandler.class));
    }

    @Test
    public void testPublishEvent() {
        StubDomainEvent event = new StubDomainEvent();

        testSubject.publish(new GenericEventMessage<StubDomainEvent>(event));

        verify(mockChannel).send(messageContainingEvent(event));
    }

    private Message<?> messageContainingEvent(final StubDomainEvent event) {
        return argThat(new ArgumentMatcher<Message<?>>() {
            @Override
            public boolean matches(Object argument) {
                Message message = (Message) argument;
                return event.equals(message.getPayload());
            }
        });
    }
}
