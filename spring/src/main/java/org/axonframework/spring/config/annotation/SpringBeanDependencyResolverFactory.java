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

import org.axonframework.common.Priority;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * ParameterResolverFactory implementation that resolves parameters using Spring dependency resolution. Mark a parameter
 * as {@link org.springframework.beans.factory.annotation.Autowired} to resolve said parameter using Spring dependency
 * resolution.
 *
 * @author Joel Feinstein
 * @see Autowired
 * @since 4.5
 */
@Priority(Priority.HIGH)
public class SpringBeanDependencyResolverFactory implements ParameterResolverFactory {

    private final ApplicationContext applicationContext;

    /**
     * Default constructor requiring an application context, for use internally by Axon.
     *
     * @param applicationContext The Spring application context for the executing application
     */
    public SpringBeanDependencyResolverFactory(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        final Optional<Boolean> ann =
                AnnotationUtils.findAnnotationAttribute(parameters[parameterIndex], Autowired.class, "required");

        if (!ann.isPresent()) {
            return null;
        }

        final boolean required = ann.get();
        final MethodParameter methodParameter;

        if (executable instanceof Method) {
            methodParameter = new MethodParameter((Method) executable, parameterIndex);
        } else {
            methodParameter = new MethodParameter((Constructor<?>) executable, parameterIndex);
        }

        final DependencyDescriptor dependencyDescriptor = new DependencyDescriptor(methodParameter, required);
        return new SpringBeanDependencyResolver(
                applicationContext.getAutowireCapableBeanFactory(), dependencyDescriptor
        );
    }

    private static class SpringBeanDependencyResolver implements ParameterResolver<Object> {

        private final AutowireCapableBeanFactory beanFactory;
        private final DependencyDescriptor dependencyDescriptor;

        public SpringBeanDependencyResolver(AutowireCapableBeanFactory beanFactory,
                                            DependencyDescriptor dependencyDescriptor) {
            this.beanFactory = beanFactory;
            this.dependencyDescriptor = dependencyDescriptor;
        }

        @Override
        public Object resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
            return beanFactory.resolveDependency(dependencyDescriptor, null);
        }

        @Override
        public boolean matches(Message<?> message, ProcessingContext processingContext) {
            return true;
        }
    }
}
