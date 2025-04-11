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
 * {@link ParameterResolverFactory} that will ensure that any {@link ProcessingContextBindableComponent} is bound to the
 * {@link ProcessingContext} when resolving the parameter value. It achieves this by decorating the
 * {@link ParameterResolverFactory} and any {@link ParameterResolver} it creates. Any
 * {@link ProcessingContextBindableComponent} produces by the wrapped {@link ParameterResolver} will be
 * {@link ProcessingContextBindableComponent#forProcessingContext(ProcessingContext) bound to the context}.
 *
 * @param delegate The {@link ParameterResolverFactory} to delegate to.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
record ProcessingContextBindingParameterResolverFactory(ParameterResolverFactory delegate)
        implements ParameterResolverFactory {

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters,
                                               int parameterIndex) {
        ParameterResolver<?> result = delegate.createInstance(executable, parameters, parameterIndex);
        if (result != null) {
            return new ProcessingContextBindingParameterResolver(result);
        }
        return null;
    }


    record ProcessingContextBindingParameterResolver(ParameterResolver<?> result)
            implements ParameterResolver<Object> {

        @Override
        public Object resolveParameterValue(Message<?> message,
                                            ProcessingContext processingContext) {
            Object parameterResult = result.resolveParameterValue(message, processingContext);
            if (parameterResult instanceof ProcessingContextBindableComponent<?> bindableComponent) {
                return bindableComponent.forProcessingContext(processingContext);
            }
            return parameterResult;
        }

        @Override
        public boolean matches(Message<?> message, ProcessingContext processingContext) {
            return result.matches(message, processingContext);
        }

        @Override
        public Class<?> supportedPayloadType() {
            return result.supportedPayloadType();
        }
    }
}
