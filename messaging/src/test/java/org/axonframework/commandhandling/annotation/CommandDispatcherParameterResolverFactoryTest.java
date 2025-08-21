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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.gateway.CommandDispatcher;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.ContextAwareCommandDispatcher;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link CommandDispatcherParameterResolverFactory}.
 *
 * @author Steven van Beelen
 */
class CommandDispatcherParameterResolverFactoryTest {

    private final Configuration configuration = mock(Configuration.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);

    private CommandDispatcherParameterResolverFactory testSubject;

    @BeforeEach
    void setUp() {
        when(configuration.getComponent(CommandGateway.class)).thenReturn(commandGateway);

        testSubject = new CommandDispatcherParameterResolverFactory(configuration);
    }

    @Test
    void injectsCommandDispatcherBasedOnProcessingContext() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();

        Method method = getClass().getMethod("methodWithCommandDispatcherParameter", CommandDispatcher.class);
        ParameterResolver<?> instance = testSubject.createInstance(method, method.getParameters(), 0);
        assertNotNull(instance);
        Object injectedParameter = instance.resolveParameterValue(processingContext);
        assertInstanceOf(ContextAwareCommandDispatcher.class, injectedParameter);
    }

    @Test
    void doesNotInjectIntoGenericParameter() throws Exception {
        Method method = getClass().getMethod("methodWithOtherParameter", Object.class);
        ParameterResolver<?> instance = testSubject.createInstance(method, method.getParameters(), 0);
        assertNull(instance);
    }

    public void methodWithCommandDispatcherParameter(CommandDispatcher dispatcher) {
        // This method is used to test the CommandDispatcherParameterResolverFactory
    }

    public void methodWithOtherParameter(Object otherParameter) {
        // This method is used to test the CommandDispatcherParameterResolverFactory
    }
}