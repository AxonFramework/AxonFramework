package org.axonframework.boot;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.AbstractCommandGateway;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter;
import org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.discovery.noop.NoopDiscoveryClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;


@ContextConfiguration(classes = {
        AxonAutoConfigurationWithSpringCloudTest.TestContext.class,
        AxonAutoConfiguration.class,
        NoopDiscoveryClientAutoConfiguration.class
})
@TestPropertySource("classpath:test.springcloud.application.properties")
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationWithSpringCloudTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("localSegment")
    private CommandBus localSegment;

    @Autowired
    private CommandBus commandBus;

    @Test
    public void testContextInitialization() throws Exception {
        assertNotNull(applicationContext);

        assertNotNull(commandBus);
        assertNotNull(localSegment);
        assertNotSame(commandBus, localSegment);
        assertNotNull(applicationContext.getBean(EventBus.class));
        CommandGateway gateway = applicationContext.getBean(CommandGateway.class);
        assertTrue(gateway instanceof DefaultCommandGateway);
        assertSame(((AbstractCommandGateway) gateway).getCommandBus(), commandBus);
        assertNotNull(gateway);
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean(AxonAutoConfiguration.DistributedCommandBusProperties.class));

        assertNotNull(applicationContext.getBean(SpringCloudCommandRouter.class));
        assertNotNull(applicationContext.getBean(SpringHttpCommandBusConnector.class));
    }

    @Configuration
    public static class TestContext {

        @Bean
        public RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

    }

}
