/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring factory bean that creates a HandlerDefinition instance that is capable of resolving parameter values as Spring
 * Beans, in addition to the default behavior defined by Axon.
 *
 * @author Tyler Thrailkill
 * @author Milan Savic
 * @see ClasspathHandlerDefinition
 * @since 3.3
 */
public class SpringHandlerDefinitionBean implements FactoryBean<HandlerDefinition>,
        BeanClassLoaderAware, InitializingBean, ApplicationContextAware {

    private final List<HandlerDefinition> definitions = new ArrayList<>();
    private ClassLoader classLoader;
    private ApplicationContext applicationContext;

    /**
     * Initializes definition bean with assumption that application context will be injected.
     */
    public SpringHandlerDefinitionBean() {
        // nothing to do, application context will be injected by spring
    }

    /**
     * Initializes definition bean with given application context.
     *
     * @param applicationContext application context
     */
    public SpringHandlerDefinitionBean(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        initialize();
    }

    @Override
    public HandlerDefinition getObject() {
        return MultiHandlerDefinition.ordered(definitions);
    }

    @Override
    public Class<?> getObjectType() {
        return HandlerDefinition.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    /**
     * Defines any additional handler definitions that should be used. By default, the HandlerDefinitions are found on
     * the classpath, as well as a SpringBeanParameterResolverFactory are registered.
     *
     * @param additionalFactories The extra definitions to register
     * @see SpringBeanParameterResolverFactory
     * @see ClasspathHandlerDefinition
     */
    public void setAdditionalHandlers(List<HandlerDefinition> additionalFactories) {
        this.definitions.addAll(additionalFactories);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void initialize() {
        definitions.addAll(ClasspathHandlerDefinition.forClassLoader(classLoader).getDelegates());
        Map<String, HandlerDefinition> definitionsFound = applicationContext.getBeansOfType(HandlerDefinition.class);
        definitions.addAll(definitionsFound.values());
    }
}
