package org.axonframework.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfigurationParameterResolverFactoryTest {
    private Configuration configuration;
    private ConfigurationParameterResolverFactory testSubject;
    private Parameter[] parameters;
    private Method method;
    private CommandBus commandBus;

    @Before
    public void setUp() throws Exception {
        configuration = mock(Configuration.class);
        commandBus = new SimpleCommandBus();
        when(configuration.getComponent(CommandBus.class)).thenReturn(commandBus);
        testSubject = new ConfigurationParameterResolverFactory(configuration);

        method = getClass().getMethod("donorMethod", String.class, CommandBus.class);
        parameters = method.getParameters();
    }

    @Test
    public void testReturnsNullOnUnavailableParameter() throws Exception {
        assertNull(testSubject.createInstance(method, parameters, 0));

        verify(configuration).getComponent(String.class);
    }

    @Test
    public void testConfigurationContainsRequestedParameter() throws Exception {
        ParameterResolver<?> actual = testSubject.createInstance(method, parameters, 1);
        assertNotNull(actual);
        assertSame(commandBus, actual.resolveParameterValue(new GenericMessage<>("test")));

        verify(configuration).getComponent(CommandBus.class);
    }

    @SuppressWarnings("unused")
    public void donorMethod(String type, CommandBus commandBus) {

    }
}
