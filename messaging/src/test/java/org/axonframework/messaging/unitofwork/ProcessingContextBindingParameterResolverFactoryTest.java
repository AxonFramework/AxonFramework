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

package org.axonframework.messaging.unitofwork;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessingContextBindingParameterResolverFactoryTest {

    private final ParameterResolverFactory wrappedFactory = mock(ParameterResolverFactory.class);
    private final ParameterResolverFactory factory = new ProcessingContextBindingParameterResolverFactory(wrappedFactory);

    @SuppressWarnings("unchecked")
    @Test
    void bindsComponentIfDelegateParameterResolverReturnsBindableComponent() throws Exception {
        // Given
        Message<?> message = mock(Message.class);
        ProcessingContext processingContext = new StubProcessingContext();

        ProcessingContextBindableComponent<Object> component = mock(ProcessingContextBindableComponent.class);
        ProcessingContextBindableComponent<Object> boundComponent = mock(ProcessingContextBindableComponent.class);
        when(component.forProcessingContext(processingContext)).thenReturn(boundComponent);

        ParameterResolver<Object> delegateParameterResolver = mock(ParameterResolver.class);
        Method method = Arrays.stream(this.getClass().getDeclaredMethods()).findFirst().get();
        Parameter[] parameters = {};
        int parameterIndex = 0;

        when(wrappedFactory.createInstance(method, parameters, parameterIndex)).thenReturn(delegateParameterResolver);
        when(delegateParameterResolver.resolveParameterValue(any(), any())).thenReturn(component);

        // When
        Object parameterValue = factory.createInstance(method, parameters, parameterIndex)
                                       .resolveParameterValue(message, processingContext);

        // Then
        assertSame(boundComponent, parameterValue);
        verify(component).forProcessingContext(processingContext);
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsParameterAsIsIfIsNotBindableComponent() throws Exception {
        // Given
        Message<?> message = mock(Message.class);
        ProcessingContext processingContext = new StubProcessingContext();

        Object parameterValue = new Object();
        ParameterResolver<Object> delegateParameterResolver = mock(ParameterResolver.class);
        Method method = Arrays.stream(this.getClass().getDeclaredMethods()).findFirst().get();
        Parameter[] parameters = {};
        int parameterIndex = 0;

        when(wrappedFactory.createInstance(method, parameters, parameterIndex)).thenReturn(delegateParameterResolver);
        when(delegateParameterResolver.resolveParameterValue(any(), any())).thenReturn(parameterValue);

        // When
        Object result = factory.createInstance(method, parameters, parameterIndex)
                               .resolveParameterValue(message, processingContext);

        // Then
        assertSame(parameterValue, result);
    }
}