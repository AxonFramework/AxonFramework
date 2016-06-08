/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.config.annotation;

import org.axonframework.commandhandling.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SupportedCommandNamesAware;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.MessageHandler;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link CommandHandler}
 * annotated methods.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class AnnotationCommandHandlerBeanPostProcessor
        extends AbstractAnnotationHandlerBeanPostProcessor<MessageHandler<CommandMessage<?>>, AnnotationCommandHandlerAdapter> {

    @Override
    protected Class<?>[] getAdapterInterfaces() {
        return new Class[]{MessageHandler.class, SupportedCommandNamesAware.class};
    }

    @Override
    protected boolean isPostProcessingCandidate(Class<?> targetClass) {
        return hasCommandHandlerMethod(targetClass);
    }

    @Override
    protected AnnotationCommandHandlerAdapter initializeAdapterFor(Object bean,
                                                                   ParameterResolverFactory parameterResolverFactory) {
        return new AnnotationCommandHandlerAdapter(bean, parameterResolverFactory);
    }

    private boolean hasCommandHandlerMethod(Class<?> beanClass) {
        final AtomicBoolean result = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(beanClass, new HasEventHandlerAnnotationMethodCallback(result));
        return result.get();
    }

    private static final class HasEventHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {

        private final AtomicBoolean result;

        private HasEventHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if (method.isAnnotationPresent(CommandHandler.class)) {
                result.set(true);
            }
        }
    }
}