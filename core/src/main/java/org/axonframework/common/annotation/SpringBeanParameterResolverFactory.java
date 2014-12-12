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

package org.axonframework.common.annotation;

import org.axonframework.common.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * ParameterResolverFactory implementation that resolves parameters in the Spring Application Context. A parameter can
 * be resolved as a Spring bean if there is exactly one bean assignable to the parameter type. If multiple beans are
 * available and one is marked as primary, that bean is chosen. Note that when multiple beans are marked as primary,
 * either one can be selected as parameter value.
 *
 * @author Allard Buijze
 * @since 2.1
 */
@Priority(Priority.LOW)
public class SpringBeanParameterResolverFactory implements ParameterResolverFactory, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringBeanParameterResolverFactory.class);

    private ApplicationContext applicationContext;

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        if (applicationContext == null) {
            return null;
        }
        Map<String, ?> beansFound = applicationContext.getBeansOfType(parameterType);
        if (beansFound.isEmpty()) {
            return null;
        } else if (beansFound.size() > 1) {
            final AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
            if (beanFactory instanceof ConfigurableListableBeanFactory) {
                for (Map.Entry<String, ?> bean : beansFound.entrySet()) {
                    final ConfigurableListableBeanFactory clBeanFactory = (ConfigurableListableBeanFactory) beanFactory;
                    if (clBeanFactory.containsBeanDefinition(bean.getKey())
                            && clBeanFactory.getBeanDefinition(bean.getKey()).isPrimary()) {
                        return new FixedValueParameterResolver<Object>(bean.getValue());
                    }
                }
            }
            if (logger.isWarnEnabled()) {
                logger.warn("{} beans of type {} found, but none was marked as primary. Ignoring this parameter.",
                            beansFound.size(), parameterType.getSimpleName());
            }
            return null;
        } else {
            return new FixedValueParameterResolver<Object>(beansFound.values().iterator().next());
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
