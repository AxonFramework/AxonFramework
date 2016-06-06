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

package org.axonframework.spring.messaging.eventbus;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.spring.messaging.StubDomainEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringMessagingEventBusTest {

    private SpringMessagingEventBus testSubject;
    private EventProcessor mockEventProcessor;
    private SubscribableChannel mockChannel;

    @Before
    public void setUp() {
        testSubject = new SpringMessagingEventBus();
        mockEventProcessor = mock(EventProcessor.class);
        mockChannel = mock(SubscribableChannel.class);
        testSubject.setChannel(mockChannel);
    }

    @Test
    public void testSubscribeListener() {
        testSubject.subscribe(new SimpleEventProcessor("test"));

        verify(mockChannel).subscribe(isA(MessageHandler.class));
    }

    @Test
    public void testUnsubscribeListener() throws Exception {
        Registration subscription = testSubject.subscribe(new SimpleEventProcessor("test"));
        subscription.close();

        verify(mockChannel).unsubscribe(isA(MessageHandler.class));
    }

    @Test
    public void testUnsubscribeListener_UnsubscribedTwice() throws Exception {
        Registration subscription = testSubject.subscribe(new SimpleEventProcessor("test"));
        subscription.close();
        subscription.close();

        verify(mockChannel).unsubscribe(any(MessageHandler.class));
    }

    @Test
    public void testSubscribeListener_SubscribedTwice() {

        SimpleEventProcessor eventProcessor = new SimpleEventProcessor("test");
        testSubject.subscribe(eventProcessor);
        testSubject.subscribe(eventProcessor);

        verify(mockChannel).subscribe(isA(MessageHandler.class));
    }

    @Test
    public void testPublishEvent() {
        StubDomainEvent event = new StubDomainEvent();

        testSubject.publish(new GenericEventMessage<>(event));

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
