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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.amqp.DefaultAMQPConsumerConfiguration;
import org.axonframework.eventhandling.amqp.DefaultAMQPMessageConverter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ListenerContainerLifecycleManagerTest {

    private ListenerContainerLifecycleManager testSubject;
    private ConnectionFactory mockConnectionFactory;
    private Serializer serializer;
    private List<SimpleMessageListenerContainer> containersCreated = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        serializer = spy(new XStreamSerializer());
        mockConnectionFactory = mock(ConnectionFactory.class);

        testSubject = new ListenerContainerLifecycleManager() {
            @Override
            public SimpleMessageListenerContainer createContainer(SpringAMQPConsumerConfiguration config) {
                final SimpleMessageListenerContainer realContainer = super.createContainer(config);
                final SimpleMessageListenerContainer container = spy(realContainer);
                doAnswer(new CallRealMethodWithSpiedArgument(container)).when(container).setMessageListener(any());
                containersCreated.add(container);
                return container;
            }
        };
        testSubject.setConnectionFactory(mockConnectionFactory);
        testSubject.afterPropertiesSet();
    }

    @Test
    public void testTwoEventProcessorsForSingleQueue() {
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor1"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));
        assertEquals(1, containersCreated.size());
        Object messageListener = containersCreated.get(0).getMessageListener();
        verify((EventProcessorMessageListener) messageListener, never()).addEventProcessor(isA(SimpleEventProcessor.class));
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor2"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));

        assertTrue(messageListener instanceof EventProcessorMessageListener);
        // the first eventProcessor is added in the constructor
        verify((EventProcessorMessageListener) messageListener, times(1)).addEventProcessor(isA(SimpleEventProcessor.class));
    }

    @Test
    public void testTwoEventProcessorsForDifferentQueues() {
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor1"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));
        assertEquals(1, containersCreated.size());
        Object messageListener1 = containersCreated.get(0).getMessageListener();

        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor2"),
                                    new DefaultAMQPConsumerConfiguration("Queue2"),
                                    new DefaultAMQPMessageConverter(serializer));

        assertEquals(2, containersCreated.size());
        Object messageListener2 = containersCreated.get(1).getMessageListener();

        // the first eventProcessor is added in the constructor
        verify((EventProcessorMessageListener) messageListener1, never()).addEventProcessor(isA(SimpleEventProcessor.class));
        verify((EventProcessorMessageListener) messageListener2, never()).addEventProcessor(isA(SimpleEventProcessor.class));
    }

    @Test
    public void testLifecycleOperationsPropagatedToAllListeners() throws Exception {
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor1"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor2"),
                                    new DefaultAMQPConsumerConfiguration("Queue2"),
                                    new DefaultAMQPMessageConverter(serializer));

        assertEquals(2, containersCreated.size());
        SimpleMessageListenerContainer container1 = containersCreated.get(0);
        SimpleMessageListenerContainer container2 = containersCreated.get(1);

        testSubject.start();
        verify(container1).start();
        verify(container2).start();

        testSubject.stop();
        verify(container1).stop();
        verify(container2).stop();

        testSubject.destroy();
        verify(container1).destroy();
        verify(container2).destroy();
    }

    @Test
    public void testContainerManagerIsRunningIfAtLeastOneContainerIsRunning() throws Exception {
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor1"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor2"),
                                    new DefaultAMQPConsumerConfiguration("Queue2"),
                                    new DefaultAMQPMessageConverter(serializer));

        testSubject.start();
        assertTrue(testSubject.isRunning());

        containersCreated.get(0).stop();
        assertTrue(testSubject.isRunning());

        containersCreated.get(1).stop();
        assertFalse(testSubject.isRunning());
    }

    @Test(expected = AxonConfigurationException.class)
    public void testEventProcessorIsRejectedIfNoQueueSpecified() {
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor1"),
                                    new DefaultAMQPConsumerConfiguration(null),
                                    new DefaultAMQPMessageConverter(serializer));
    }

    @Test
    public void testEventProcessorIsCreatedAfterContainerStart() {
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor1"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));
        assertEquals(1, containersCreated.size());
        Object messageListener = containersCreated.get(0).getMessageListener();
        verify((EventProcessorMessageListener) messageListener, never()).addEventProcessor(isA(SimpleEventProcessor.class));
        testSubject.start();
        testSubject.registerEventProcessor(new SimpleEventProcessor("eventProcessor2"),
                                    new DefaultAMQPConsumerConfiguration("Queue1"),
                                    new DefaultAMQPMessageConverter(serializer));

        assertTrue(messageListener instanceof EventProcessorMessageListener);
        // the first eventProcessor is added in the constructor
        verify((EventProcessorMessageListener) messageListener, times(1)).addEventProcessor(isA(SimpleEventProcessor.class));
    }

    private static class CallRealMethodWithSpiedArgument implements Answer {

        private final SimpleMessageListenerContainer container;

        public CallRealMethodWithSpiedArgument(SimpleMessageListenerContainer container) {
            this.container = container;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            if (new MockUtil().isMock(invocation.getArguments()[0])) {
                return invocation.callRealMethod();
            }
            return invocation.getMethod().invoke(container, spy(invocation.getArguments()[0]));
        }
    }
}
