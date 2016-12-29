package org.axonframework.boot;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.AbstractCommandGateway;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.serialization.Serializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@ContextConfiguration(classes = {
        AxonAutoConfiguration.class})
@TestPropertySource("classpath:test.jgroups.application.properties")
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationWithJGroupsTest {

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
    }
}
