/*
 * Copyright (c) 2010-2015. Axon Framework
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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.amqp.DefaultAMQPMessageConverter;
import org.axonframework.eventhandling.amqp.EventPublicationFailedException;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.NoTransactionManager;
import org.axonframework.unitofwork.TransactionManager;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringAMQPEventBusTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private SpringAMQPEventBus testSubject;
    private ConnectionFactory connectionFactory;
    private Serializer serializer;

    @Before
    public void setUp() throws Exception {
        testSubject = new SpringAMQPEventBus();
        connectionFactory = mock(ConnectionFactory.class);
        serializer = mock(Serializer.class);
        testSubject.setConnectionFactory(connectionFactory);
        testSubject.setExchangeName("mockExchange");
        testSubject.setTransactional(true);
        testSubject.setMessageConverter(new DefaultAMQPMessageConverter(serializer));
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testSendMessage_NoUnitOfWork() throws IOException {
        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(false), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel).txCommit();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithTransactionalUnitOfWork() throws IOException {
        TransactionManager<?> mockTransaction = new NoTransactionManager();
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(mockTransaction);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(transactionalChannel.isOpen()).thenReturn(true);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(false), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel, never()).close();

        uow.commit();
        verify(transactionalChannel).txCommit();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithTransactionalUnitOfWork_ChannelClosedBeforeCommit() throws IOException {
        TransactionManager<?> mockTransaction = new NoTransactionManager();
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(mockTransaction);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(transactionalChannel.isOpen()).thenReturn(false);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(false), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel, never()).close();

        try {
            uow.commit();
            fail("Expected exception");
        } catch (EventPublicationFailedException e) {
            assertNotNull(e.getMessage());
        }
        verify(transactionalChannel, never()).txCommit();
    }

    @Test
    public void testSendMessage_WithUnitOfWorkRollback() throws IOException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(false), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel, never()).txRollback();
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel, never()).close();

        uow.rollback();
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel).txRollback();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessageWithPublisherAck_UnitOfWorkCommitted()
            throws InterruptedException, IOException, TimeoutException {
        testSubject.setTransactional(false);
        testSubject.setWaitForPublisherAck(true);
        testSubject.setPublisherAckTimeout(123);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel channel = mock(Channel.class);

        when(channel.waitForConfirms()).thenReturn(true);
        when(connection.createChannel(false)).thenReturn(channel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        UnitOfWork uow = DefaultUnitOfWork.startAndGet();

        testSubject.publish(message);
        verify(channel, never()).waitForConfirms();

        uow.commit();

        verify(channel).confirmSelect();
        verify(channel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                     eq(false), eq(false),
                                     any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(channel).waitForConfirmsOrDie(123);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotSetPublisherAcksAfterTransactionalSetting() {
        testSubject.setTransactional(true);
        testSubject.setWaitForPublisherAck(true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotSetTransactionalBehaviorAfterPublisherAcks() {
        testSubject.setTransactional(false);

        testSubject.setWaitForPublisherAck(true);
        testSubject.setTransactional(true);
    }

    @Test
    public void testSendMessageWithPublisherAck_NoActiveUnitOfWork() throws InterruptedException, IOException {
        testSubject.setTransactional(false);
        testSubject.setWaitForPublisherAck(true);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel channel = mock(Channel.class);

        when(channel.waitForConfirms()).thenReturn(true);
        when(connection.createChannel(false)).thenReturn(channel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        testSubject.publish(message);
        verify(channel).confirmSelect();
        verify(channel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                     eq(false), eq(false),
                                     any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(channel).waitForConfirmsOrDie();
    }
}
