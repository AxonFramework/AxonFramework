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

package org.axonframework.eventhandling.amqp.spring;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.amqp.DefaultAMQPMessageConverter;
import org.axonframework.eventhandling.io.EventMessageWriter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.Charset;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventProcessorMessageListenerTest {

    @Test
    public void testMessageListenerInvokesAllEventProcessors() throws Exception {
        Serializer serializer = new XStreamSerializer();
        EventProcessor eventProcessor = mock(EventProcessor.class);
        EventProcessorMessageListener testSubject = new EventProcessorMessageListener(new DefaultAMQPMessageConverter(serializer));
        testSubject.addEventProcessor(eventProcessor);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EventMessageWriter outputStream = new EventMessageWriter(new DataOutputStream(baos), serializer);
        outputStream.writeEventMessage(new GenericEventMessage<>("Event"));
        testSubject.onMessage(new Message(baos.toByteArray(), new MessageProperties()));

        verify(eventProcessor).accept(argThat(new TypeSafeMatcher<EventMessage>() {
            @Override
            public boolean matchesSafely(EventMessage item) {
                return "Event".equals(item.getPayload());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("EventMessage with String payload");
            }
        }));
    }

    @Test
    public void testMessageListenerIgnoredOnDeserializationFailure() throws Exception {
        Serializer serializer = new XStreamSerializer();
        EventProcessor eventProcessor = mock(EventProcessor.class);
        EventProcessorMessageListener testSubject = new EventProcessorMessageListener(new DefaultAMQPMessageConverter(serializer));
        testSubject.addEventProcessor(eventProcessor);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EventMessageWriter outputStream = new EventMessageWriter(new DataOutputStream(baos), serializer);
        outputStream.writeEventMessage(new GenericEventMessage<>("Event"));
        byte[] body = baos.toByteArray();
        body = new String(body, Charset.forName("UTF-8")).replace("string", "strong")
                                                         .getBytes(Charset.forName("UTF-8"));
        testSubject.onMessage(new Message(body, new MessageProperties()));

        verify(eventProcessor, never()).accept(any(EventMessage.class));
    }
}
