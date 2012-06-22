package org.axonframework.eventhandling.amqp.spring;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringAMQPTerminalTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private SpringAMQPTerminal testSubject;
    private ConnectionFactory connectionFactory;
    private Serializer serializer;

    @Before
    public void setUp() throws Exception {
        testSubject = new SpringAMQPTerminal();
        connectionFactory = mock(ConnectionFactory.class);
        serializer = mock(Serializer.class);
        testSubject.setConnectionFactory(connectionFactory);
        testSubject.setExchangeName("mockExchange");
        testSubject.setSerializer(serializer);
        testSubject.setTransactional(true);
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
        GenericEventMessage<String> message = new GenericEventMessage<String>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<byte[]>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<byte[]>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(true), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel).txCommit();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithUnitOfWork() throws IOException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<String>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<byte[]>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<byte[]>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(true), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel, never()).close();

        uow.commit();
        verify(transactionalChannel).txCommit();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithUnitOfWorkRollback() throws IOException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<String>("Message");
        when(serializer.serialize(message.getPayload(), byte[].class))
                .thenReturn(new SimpleSerializedObject<byte[]>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<byte[]>(new byte[0], byte[].class));
        testSubject.publish(message);

        verify(transactionalChannel).basicPublish(eq("mockExchange"), eq("java.lang"),
                                                  eq(true), eq(false),
                                                  any(AMQP.BasicProperties.class), isA(byte[].class));
        verify(transactionalChannel, never()).txRollback();
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel, never()).close();

        uow.rollback();
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel).txRollback();
        verify(transactionalChannel).close();
    }

}
