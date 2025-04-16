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

package org.axonframework.eventhandling.gateway;

import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventAppenderParameterResolverFactoryTest {

    private final NewConfiguration configuration = mock(NewConfiguration.class);
    private final EventSink eventSink = mock(EventSink.class);
    private final MessageTypeResolver messageTypeResolver = mock(MessageTypeResolver.class);
    private final EventAppenderParameterResolverFactory testSubject = new EventAppenderParameterResolverFactory(
            configuration);

    @BeforeEach
    void setUp() {
        when(configuration.getComponent(EventSink.class)).thenReturn(eventSink);
        when(configuration.getComponent(MessageTypeResolver.class)).thenReturn(messageTypeResolver);
    }

    @Test
    void injectsEventAppenderBasedOnProcessingContext() throws Exception {
        ProcessingContext processingContext = new StubProcessingContext();

        Method method = getClass().getMethod("methodWithEventAppenderParameter", EventAppender.class);
        ParameterResolver<?> instance = testSubject.createInstance(method, method.getParameters(), 0);
        assertNotNull(instance);
        Object injectedParameter = instance.resolveParameterValue(mock(EventMessage.class), processingContext);
        assertInstanceOf(ProcessingContextEventAppender.class, injectedParameter);
    }


    public void methodWithEventAppenderParameter(
            EventAppender eventAppender
    ) {
        // This method is used to test the EventAppenderParameterResolverFactory
    }
}