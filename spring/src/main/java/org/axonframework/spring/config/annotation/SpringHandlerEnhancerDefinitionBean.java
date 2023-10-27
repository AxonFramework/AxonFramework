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

import org.axonframework.messaging.annotation.ClasspathHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Spring factory bean that creates a HandlerEnhancerDefinition instance that is capable of resolving parameter values
 * as Spring Beans, in addition to the default behavior defined by Axon.
 *
 * @author Milan Savic
 * @see ClasspathHandlerEnhancerDefinition
 * @since 3.3
 * @deprecated Replaced by the {@link HandlerDefinitionFactoryBean}.
 */
@Deprecated
public class SpringHandlerEnhancerDefinitionBean implements FactoryBean<HandlerEnhancerDefinition>,
        BeanClassLoaderAware, InitializingBean, ApplicationContextAware {

    private final List<HandlerEnhancerDefinition> enhancers = new ArrayList<>();
    private ClassLoader classLoader;
    private ApplicationContext applicationContext;

    /**
     * Initializes definition bean with assumption that application context will be injected.
     */
    public SpringHandlerEnhancerDefinitionBean() {
        // nothing to do, application context will be injected by spring
    }

    /**
     * Initializes definition bean with given application context.
     *
     * @param applicationContext application context
     */
    public SpringHandlerEnhancerDefinitionBean(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        initialize();
    }

    @Override
    public void setBeanClassLoader(@Nonnull ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public HandlerEnhancerDefinition getObject() {
        return MultiHandlerEnhancerDefinition.ordered(enhancers);
    }

    @Override
    public Class<?> getObjectType() {
        return MultiHandlerEnhancerDefinition.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Defines any additional handler enhancer definitions that should be used. By default, the
     * HandlerEnhancerDefinitions are found on the classpath, as well as a SpringBeanParameterResolverFactory are
     * registered.
     *
     * @param additionalFactories The extra definitions to register
     * @see SpringBeanParameterResolverFactory
     * @see ClasspathHandlerEnhancerDefinition
     */
    public void setAdditionalHandlers(List<HandlerEnhancerDefinition> additionalFactories) {
        this.enhancers.addAll(additionalFactories);
    }

    private void initialize() {
        enhancers.addAll(ClasspathHandlerEnhancerDefinition.forClassLoader(classLoader).getDelegates());
        Map<String, HandlerEnhancerDefinition> enhancersFound = beansOfTypeIncludingAncestors(applicationContext, HandlerEnhancerDefinition.class);
        enhancers.addAll(enhancersFound.values());
    }
}
