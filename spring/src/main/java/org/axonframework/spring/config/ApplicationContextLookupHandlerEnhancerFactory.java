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

package org.axonframework.spring.config;

import java.util.ArrayList;
import java.util.List;

import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * FactoryBean implementation that create a HandlerEnhancerDefinition, which auto-detects beans implementing
 * HandlerEnhancerDefinition beans in the application context.
 *
 * @author Allard Buijze, Tyler Thrailkill
 * @since 3.3
 */
public class ApplicationContextLookupHandlerEnhancerFactory implements FactoryBean<HandlerEnhancerDefinition>,
        ApplicationContextAware, InitializingBean {

    private final List<HandlerEnhancerDefinition> factories;
    private volatile HandlerEnhancerDefinition handlerEnhancerDefinition;
    private ApplicationContext applicationContext;

    /**
     * Creates an instance, using the given {@code defaultFactories}. These are added, regardless of the beans
     * discovered in the application context.
     *
     * @param defaultFactories The HandlerEnhancerDefinition instances to add by default
     */
    public ApplicationContextLookupHandlerEnhancerFactory(List<HandlerEnhancerDefinition> defaultFactories) {
        this.factories = new ArrayList<>(defaultFactories);
    }

    @Override
    public HandlerEnhancerDefinition getObject() throws Exception {
        return handlerEnhancerDefinition;
    }

    @Override
    public Class<?> getObjectType() {
        return MultiParameterResolverFactory.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        factories.addAll(applicationContext.getBeansOfType(HandlerEnhancerDefinition.class).values());
        handlerEnhancerDefinition = MultiParameterResolverFactory.ordered(factories);
    }
}
