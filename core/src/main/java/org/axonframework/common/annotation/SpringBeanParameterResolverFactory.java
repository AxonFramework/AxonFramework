/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextStoppedEvent;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * @author Allard Buijze
 */
public class SpringBeanParameterResolverFactory extends ParameterResolverFactory
        implements BeanFactoryPostProcessor, ApplicationContextAware, ApplicationListener {

    private static final Logger logger = LoggerFactory.getLogger(SpringBeanParameterResolverFactory.class);

    private ApplicationContext applicationContext;

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                               Annotation[] parameterAnnotations) {
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
                        return new FixedValueParameterResolver<Object>(beansFound.get(bean.getValue()));
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

    /**
     * This implementation always returns <code>false</code>, to indicate that payload resolution is not supported for
     * this factory
     *
     * @return <code>false</code>
     */
    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ParameterResolverFactory.registerFactory(this);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextStoppedEvent || event instanceof ContextClosedEvent) {
            if (applicationContext == null
                    || applicationContext.equals(((ApplicationContextEvent) event).getApplicationContext())) {
                ParameterResolverFactory.unregisterFactory(this);
            }
        }
    }
}
