/*
 * Copyright (c) 2010-2016. Axon Framework
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

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;

import org.axonframework.common.Priority;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.spring.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * ParameterResolverFactory implementation that resolves parameters in the Spring Application Context. A parameter can
 * be resolved as a Spring bean if there is exactly one bean assignable to the parameter type. If multiple beans are
 * available the desired one can be designated with a {@link Qualifier}
 * annotation on the parameter. By absence of a {@link Qualifier}
 * annotation the bean marked as primary will be chosen.
 * Note that when multiple beans are marked as primary, either one can be selected as parameter value.
 *
 * @author Allard Buijze
 * @since 2.1
 */
@Priority(Priority.LOW)
public class SpringBeanHandlerEnhancerDefinition implements HandlerEnhancerDefinition, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringBeanHandlerEnhancerDefinition.class);

    private ApplicationContext applicationContext;

    /**
     * Default constructor, which relies on Spring to inject the application context.
     */
    public SpringBeanHandlerEnhancerDefinition() {
    }

    /**
     * Convenience constructor to use when an instance is not managed by Spring, but an application context is
     * available.
     *
     * @param applicationContext The application context providing access to beans that may be used as parameters
     */
    public SpringBeanHandlerEnhancerDefinition(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        MessageHandlingMember<T> wrappedHandler = handler;
        if (wrapperDefinitions != null) {
            for (HandlerEnhancerDefinition definition : wrapperDefinitions) {
                wrappedHandler = definition.wrapHandler(wrappedHandler);
            }
        }
        return wrappedHandler;
        return new SpringBeanHandlerEnhancerDefinition(applicationContext);
    }

    private MessageHandlingMember<T> wrapped(MessageHandlingMember<T> handler,
                                             Iterable<HandlerEnhancerDefinition> wrapperDefinitions) {

    }

    private static class SpringBeanParameterResolver implements ParameterResolver<Object> {

        private final AutowireCapableBeanFactory beanFactory;
        private final String beanName;

        public SpringBeanParameterResolver(AutowireCapableBeanFactory beanFactory, String beanName) {
            this.beanFactory = beanFactory;
            this.beanName = beanName;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return beanFactory.getBean(beanName);
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }
}
