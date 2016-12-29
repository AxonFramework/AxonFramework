package org.axonframework.boot;

import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.serialization.Serializer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

@ContextConfiguration(classes = {RabbitAutoConfiguration.class,
        AxonAutoConfiguration.class})
@RunWith(SpringRunner.class)
@EnableConfigurationProperties
public class AxonAutoConfigurationWithAMQP {

    @BeforeClass
    public static void setUp() throws Exception {
        System.setProperty("axon.amqp.exchange", "test");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testContextInitialization() throws Exception {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean(AMQPMessageConverter.class));
        assertNotNull(applicationContext.getBean(SpringAMQPPublisher.class));
    }
}
