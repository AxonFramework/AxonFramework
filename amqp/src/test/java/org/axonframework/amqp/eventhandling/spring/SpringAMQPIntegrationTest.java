package org.axonframework.amqp.eventhandling.spring;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.axonframework.amqp.eventhandling.AMQPMessage;
import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNoException;

@ContextConfiguration(classes = SpringAMQPIntegrationTest.Context.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringAMQPIntegrationTest {

    @Autowired
    private SimpleMessageListenerContainer listenerContainer;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private SpringAMQPMessageSource springAMQPMessageSource;

    @Autowired
    private AMQPMessageConverter messageConverter;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Before
    public void setUp() {
        try {
            amqpAdmin.declareQueue(new Queue("testQueue", false, false, true));
            amqpAdmin.declareExchange(new FanoutExchange("testExchange", false, true));
            amqpAdmin.declareBinding(new Binding("testQueue", Binding.DestinationType.QUEUE, "testExchange", "", null));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @After
    public void tearDown() {
        try {
            listenerContainer.stop();
            amqpAdmin.deleteExchange("testExchange");
            amqpAdmin.deleteQueue("testQueue");
        } catch (Exception e) {
            // whatever
        }
    }

    @DirtiesContext
    @Test
    public void testReadFromAMQP() throws Exception {

        listenerContainer.setMessageListener(springAMQPMessageSource);
        CountDownLatch cdl = new CountDownLatch(100);
        springAMQPMessageSource.subscribe(em -> em.forEach(m -> cdl.countDown()));
        listenerContainer.start();

        Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);

        try {
            for (int i = 0; i < 100; i++) {
                AMQPMessage message = messageConverter.createAMQPMessage(asEventMessage("test" + i));
                channel.basicPublish("testExchange", message.getRoutingKey(), false, false,
                                     message.getProperties(), message.getBody());
            }
        } finally {
            channel.close();
            connection.close();
        }

        assertTrue(cdl.await(10, TimeUnit.SECONDS));
    }

    @DirtiesContext
    @Test
    public void testPublishMessagesFromEventBus() throws Exception {
        SimpleEventBus messageSource = new SimpleEventBus();
        SpringAMQPPublisher publisher = new SpringAMQPPublisher(messageSource);
        publisher.setConnectionFactory(connectionFactory);
        publisher.setExchangeName("testExchange");
        publisher.setMessageConverter(messageConverter);
        publisher.start();

        messageSource.publish(asEventMessage("test1"), asEventMessage("test2"));

        Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        try {
            assertEquals("test1", readMessage(channel).getPayload());
            assertEquals("test2", readMessage(channel).getPayload());
        } finally {
            channel.close();
            connection.close();
        }
    }

    private EventMessage readMessage(Channel channel) throws IOException {
        GetResponse message = channel.basicGet("testQueue", true);
        assertNotNull("Expected message on the queue", message);
        return messageConverter.readAMQPMessage(message.getBody(), message.getProps().getHeaders()).orElse(null);
    }

    @Configuration
    public static class Context {

        @Bean
        public SpringAMQPMessageSource springAMQPMessageSource() {
            return new SpringAMQPMessageSource(messageConverter());
        }

        @Bean
        public AMQPMessageConverter messageConverter() {
            return new DefaultAMQPMessageConverter(seralizer());
        }

        @Bean
        public Serializer seralizer() {
            return new XStreamSerializer();
        }

        @Bean
        public SimpleMessageListenerContainer rabbitListener() {
            SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer(connectionFactory());
            listenerContainer.setQueueNames("testQueue");
            listenerContainer.setAutoStartup(false);
            return listenerContainer;
        }

        @Bean
        public ConnectionFactory connectionFactory() {
            return new CachingConnectionFactory("localhost");
        }

        @Bean
        public AmqpAdmin amqpAdmin() {
            return new RabbitAdmin(connectionFactory());
        }
    }
}
