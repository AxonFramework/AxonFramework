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
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * {@link ParameterResolverFactory} implementation that provides a {@link ParameterResolver} for parameters of type
 * {@link ProcessingContext}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ProcessingContextParameterResolverFactory implements ParameterResolverFactory {

    private static final ProcessingContextParameterResolver INSTANCE = new ProcessingContextParameterResolver();

    @Override
    public ParameterResolver<ProcessingContext> createInstance(Executable executable, Parameter[] parameters,
                                                               int parameterIndex) {

        Parameter parameter = parameters[parameterIndex];
        if (parameter.getType().isAssignableFrom(ProcessingContext.class)) {
            return INSTANCE;
        }
        return null;
    }

    private static class ProcessingContextParameterResolver implements ParameterResolver<ProcessingContext> {

        @Override
        public ProcessingContext resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
            return processingContext;
        }

        @Override
        public boolean matches(Message<?> message, ProcessingContext processingContext) {
            return true;
        }
    }
}
