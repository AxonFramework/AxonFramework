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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import org.junit.*;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ExtendedMessageListenerContainerTest {

    private ExtendedMessageListenerContainer testSubject;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Channel channel;

    @Before
    public void setUp() throws Exception {
        testSubject = new ExtendedMessageListenerContainer();
        connectionFactory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(anyBoolean())).thenReturn(channel);
    }

    @Test
    public void testExclusiveByDefault() throws IOException {
        testSubject.setConnectionFactory(connectionFactory);

        testSubject.getConnectionFactory().createConnection().createChannel(true).basicConsume("test", new DefaultConsumer(channel));

        verify(channel, never()).basicConsume(isA(String.class), isA(Consumer.class));
        verify(channel, never()).basicConsume(isA(String.class), anyBoolean(), isA(Consumer.class));
        verify(channel, never()).basicConsume(isA(String.class), anyBoolean(), anyString(), isA(Consumer.class));
        verify(channel, never()).basicConsume(isA(String.class), anyBoolean(), anyString(), anyBoolean(), eq(false), anyMap(), isA(Consumer.class));
        verify(channel).basicConsume(isA(String.class), anyBoolean(), anyString(), anyBoolean(), eq(true), anyMap(), isA(Consumer.class));
    }

    @Test
    public void testNonExclusive() throws IOException {
        testSubject.setConnectionFactory(connectionFactory);
        testSubject.setExclusive(false);

        testSubject.getConnectionFactory().createConnection().createChannel(true).basicConsume("test", new DefaultConsumer(channel));

        verify(channel, never()).basicConsume(isA(String.class), anyBoolean(), anyString(), anyBoolean(), eq(true), anyMap(), isA(Consumer.class));
    }
}
