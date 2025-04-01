/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ConfigurationParameterResolverFactory}.
 *
 * @author Allard Buijze
 */
class ConfigurationParameterResolverFactoryTest {

    private Method method;
    private Parameter[] parameters;
    private NewConfiguration configuration;
    private CommandBus commandBus;

    private ConfigurationParameterResolverFactory testSubject;

    @BeforeEach
    void setUp() throws Exception {
        method = getClass().getMethod("donorMethod", String.class, CommandBus.class);
        parameters = method.getParameters();
        configuration = mock(NewConfiguration.class);
        commandBus = new SimpleCommandBus();
        when(configuration.getOptionalComponent(CommandBus.class)).thenReturn(Optional.of(commandBus));

        testSubject = new ConfigurationParameterResolverFactory(configuration);
    }

    @Test
    void returnsNullOnUnavailableParameter() {
        assertNull(testSubject.createInstance(method, parameters, 0));

        verify(configuration).getOptionalComponent(String.class);
    }

    @Test
    void configurationContainsRequestedParameter() {
        ParameterResolver<?> actual = testSubject.createInstance(method, parameters, 1);
        Message<String> testMessage = new GenericMessage<>(new MessageType("message"), "test");

        assertNotNull(actual);
        assertSame(commandBus, actual.resolveParameterValue(testMessage, null));

        verify(configuration).getOptionalComponent(CommandBus.class);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void donorMethod(String type, CommandBus commandBus) {

    }
}
