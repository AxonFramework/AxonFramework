/*
 * Copyright (c) 2010-2023. Axon Framework
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
package org.axonframework.spring.config.annotation;

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.spring.config.AbstractAnnotationHandlerBeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link QueryHandler}
 * annotated methods.
 *
 * @author Marc Gathier
 * @since 3.1
 * @deprecated Replaced by the {@link org.axonframework.spring.config.MessageHandlerLookup} and {@link
 * org.axonframework.spring.config.MessageHandlerConfigurer}.
 */
@Deprecated
public class AnnotationQueryHandlerBeanPostProcessor extends AbstractAnnotationHandlerBeanPostProcessor<QueryHandlerAdapter, AnnotationQueryHandlerAdapter> {
    @Override
    protected Class<?>[] getAdapterInterfaces() {
        return new Class[]{QueryHandlerAdapter.class, MessageHandler.class};
    }

    @Override
    protected boolean isPostProcessingCandidate(Class<?> targetClass) {
        return this.hasQueryHandlerMethod(targetClass);
    }

    private boolean hasQueryHandlerMethod(Class<?> beanClass) {
        AtomicBoolean result = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(beanClass, new HasQueryHandlerAnnotationMethodCallback(result));
        return result.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AnnotationQueryHandlerAdapter initializeAdapterFor(Object o,
                                                                 ParameterResolverFactory parameterResolverFactory,
                                                                 HandlerDefinition handlerDefinition) {
        return new AnnotationQueryHandlerAdapter<>(o, parameterResolverFactory, handlerDefinition);
    }

    private class HasQueryHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {
        private final AtomicBoolean result;

        public HasQueryHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        @Override
        public void doWith(@Nonnull Method method) throws IllegalArgumentException {
            if (AnnotationUtils.findAnnotationAttributes(method, QueryHandler.class).isPresent()) {
                result.set(true);
            }
        }
    }
}
