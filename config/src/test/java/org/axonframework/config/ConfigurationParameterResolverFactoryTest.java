/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.axonframework.messaging.QualifiedName.dottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationParameterResolverFactoryTest {

    private Configuration configuration;
    private ConfigurationParameterResolverFactory testSubject;
    private Parameter[] parameters;
    private Method method;
    private CommandBus commandBus;

    @BeforeEach
    void setUp() throws Exception {
        configuration = mock(Configuration.class);
        commandBus = new SimpleCommandBus();
        when(configuration.getComponent(CommandBus.class)).thenReturn(commandBus);
        testSubject = new ConfigurationParameterResolverFactory(configuration);

        method = getClass().getMethod("donorMethod", String.class, CommandBus.class);
        parameters = method.getParameters();
    }

    @Test
    void returnsNullOnUnavailableParameter() {
        assertNull(testSubject.createInstance(method, parameters, 0));

        verify(configuration).getComponent(String.class);
    }

    @Test
    void configurationContainsRequestedParameter() {
        ParameterResolver<?> actual = testSubject.createInstance(method, parameters, 1);
        Message<String> testMessage = new GenericMessage<>(dottedName("test.message"), "test");

        assertNotNull(actual);
        assertSame(commandBus, actual.resolveParameterValue(testMessage, null));

        verify(configuration).getComponent(CommandBus.class);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void donorMethod(String type, CommandBus commandBus) {

    }
}
