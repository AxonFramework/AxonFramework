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

package org.axonframework.amqp.eventhandling.spring;

import com.rabbitmq.client.Channel;
import org.axonframework.amqp.eventhandling.AMQPMessage;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringAMQPMessageSourceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testMessageListenerInvokesAllEventProcessors() throws Exception {
        Serializer serializer = new XStreamSerializer();
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        DefaultAMQPMessageConverter messageConverter = new DefaultAMQPMessageConverter(serializer);
        SpringAMQPMessageSource testSubject = new SpringAMQPMessageSource(messageConverter);
        testSubject.subscribe(eventProcessor);

        AMQPMessage message = messageConverter.createAMQPMessage(GenericEventMessage.asEventMessage("test"));

        MessageProperties messageProperties = new MessageProperties();
        message.getProperties().getHeaders().forEach(messageProperties::setHeader);
        testSubject.onMessage(new Message(message.getBody(), messageProperties), mock(Channel.class));

        verify(eventProcessor).accept(argThat(new TypeSafeMatcher<List<? extends EventMessage<?>>>() {
            @Override
            public boolean matchesSafely(List<? extends EventMessage<?>> item) {
                return item.size() == 1 && item.get(0).getPayload().equals("test");
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("EventMessage with String payload");
            }
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMessageListenerIgnoredOnDeserializationFailure() throws Exception {
        Serializer serializer = new XStreamSerializer();
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        DefaultAMQPMessageConverter messageConverter = new DefaultAMQPMessageConverter(serializer);
        SpringAMQPMessageSource testSubject = new SpringAMQPMessageSource(messageConverter);
        testSubject.subscribe(eventProcessor);

        AMQPMessage message = messageConverter.createAMQPMessage(GenericEventMessage.asEventMessage("test"));

        MessageProperties messageProperties = new MessageProperties();
        message.getProperties().getHeaders().forEach(messageProperties::setHeader);
        // we make the message unreadable
        messageProperties.setHeader("axon-message-type", "strong");
        testSubject.onMessage(new Message(message.getBody(), messageProperties), mock(Channel.class));

        verify(eventProcessor, never()).accept(any(List.class));
    }
}
