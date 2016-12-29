package org.axonframework.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ConfigurationResourceInjectorTest {

    private Configuration configuration;
    private ConfigurationResourceInjector testSubject;

    @Before
    public void setUp() throws Exception {
        configuration = DefaultConfigurer.defaultConfiguration().buildConfiguration();
        testSubject = new ConfigurationResourceInjector(configuration);
    }

    @Test
    public void testInjectorHasResource() throws Exception {
        Saga saga = new Saga();
        testSubject.injectResources(saga);

        assertSame(configuration.commandBus(), saga.commandBus);
        assertSame(configuration.commandGateway(), saga.commandGateway);
        assertNull(saga.inexistent);
    }

    public static class Saga {

        @Inject
        private CommandBus commandBus;

        @Inject
        private String inexistent;

        private CommandGateway commandGateway;

        @Inject
        public void setCommandGateway(CommandGateway commandGateway) {
            this.commandGateway = commandGateway;
        }
    }
}
