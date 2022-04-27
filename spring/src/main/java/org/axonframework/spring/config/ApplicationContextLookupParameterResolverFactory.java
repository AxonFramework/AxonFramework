/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * FactoryBean implementation that create a ParameterResolverFactory, which auto-detects beans implementing
 * ParameterResolverFactory beans in the application context.
 *
 * @author Allard Buijze
 * @since 2.1.2
 * @deprecated Use Spring Boot autoconfiguration or register the individual beans explicitly.
 */
@Deprecated
public class ApplicationContextLookupParameterResolverFactory implements FactoryBean<ParameterResolverFactory>,
        ApplicationContextAware, InitializingBean {

    private final List<ParameterResolverFactory> factories;
    private volatile ParameterResolverFactory parameterResolverFactory;
    private ApplicationContext applicationContext;

    /**
     * Creates an instance, using the given {@code defaultFactories}. These are added, regardless of the beans
     * discovered in the application context.
     *
     * @param defaultFactories The ParameterResolverFactory instances to add by default
     */
    public ApplicationContextLookupParameterResolverFactory(List<ParameterResolverFactory> defaultFactories) {
        this.factories = new ArrayList<>(defaultFactories);
    }

    @Override
    public ParameterResolverFactory getObject() {
        return parameterResolverFactory;
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
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        factories.addAll(applicationContext.getBeansOfType(ParameterResolverFactory.class).values());
        parameterResolverFactory = MultiParameterResolverFactory.ordered(factories);
    }
}
