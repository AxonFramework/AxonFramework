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

import java.util.ArrayList;
import java.util.List;

import org.axonframework.messaging.annotation.ClasspathHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring factory bean that creates a HandlerEnhancerDefinition instance that is capable of resolving parameter values
 * as Spring Beans, in addition to the default behavior defined by Axon.
 *
 * @author Allard Buijze
 * @see ClasspathHandlerEnhancerDefinition
 * @since 2.3.1
 */
public class SpringHandlerEnhancerDefinitionBean implements FactoryBean<HandlerEnhancerDefinition>,
        BeanClassLoaderAware, InitializingBean, ApplicationContextAware {

    private final List<HandlerEnhancerDefinition> enhancers = new ArrayList<>();
    private ClassLoader classLoader;
    private ApplicationContext applicationContext;

    @Override
    public HandlerEnhancerDefinition getObject() throws Exception {
        return MultiHandlerEnhancerDefinition.ordered(enhancers);
    }

    @Override
    public Class<?> getObjectType() {
        return ParameterResolverFactory.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        enhancers.add(new ClasspathHandlerEnhancerDefinition(classLoader));
    }

    /**
     * Defines any additional handler enhancers that should be used. By default, the HandlerEnhancerDefinitions are
     * found on the classpath, as well as a SpringBeanParameterResolverFactory are registered.
     *
     * @param additionalFactories The extra enhancers to register
     * @see SpringBeanParameterResolverFactory
     * @see ClasspathParameterResolverFactory
     */
    public void setAdditionalFactories(List<HandlerEnhancerDefinition> additionalFactories) {
        this.enhancers.addAll(additionalFactories);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
