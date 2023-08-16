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

import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.spring.config.ApplicationContextLookupParameterResolverFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;

import javax.annotation.Nonnull;

/**
 * Creates and registers a bean definition for a Spring Context aware ParameterResolverFactory. It ensures that only one
 * such instance exists for each ApplicationContext.
 *
 * @author Allard Buijze
 * @since 2.1
 * @deprecated Use Spring Boot autoconfiguration or register the individual beans explicitly.
 */
@Deprecated
public final class SpringContextParameterResolverFactoryBuilder {

    private static final String PARAMETER_RESOLVER_FACTORY_BEAN_NAME = "__axon-parameter-resolver-factory";

    private SpringContextParameterResolverFactoryBuilder() {
    }

    /**
     * Create, if necessary, a bean definition for a ParameterResolverFactory and returns the reference to bean for use
     * in other Bean Definitions.
     *
     * @param registry The registry in which to look for an already existing instance
     * @return a BeanReference to the BeanDefinition for the ParameterResolverFactory
     */
    public static RuntimeBeanReference getBeanReference(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(PARAMETER_RESOLVER_FACTORY_BEAN_NAME)) {
            final ManagedList<BeanDefinition> factories = new ManagedList<>();
            factories.add(BeanDefinitionBuilder.genericBeanDefinition(ClasspathParameterResolverFactoryBean.class)
                                               .getBeanDefinition());
            factories.add(BeanDefinitionBuilder.genericBeanDefinition(SpringBeanDependencyResolverFactory.class)
                                               .getBeanDefinition());
            factories.add(BeanDefinitionBuilder.genericBeanDefinition(SpringBeanParameterResolverFactory.class)
                                               .getBeanDefinition());
            AbstractBeanDefinition def =
                    BeanDefinitionBuilder.genericBeanDefinition(ApplicationContextLookupParameterResolverFactory.class)
                                         .addConstructorArgValue(factories)
                                         .getBeanDefinition();
            def.setPrimary(true);
            registry.registerBeanDefinition(PARAMETER_RESOLVER_FACTORY_BEAN_NAME, def);
        }
        return new RuntimeBeanReference(PARAMETER_RESOLVER_FACTORY_BEAN_NAME);
    }

    private static class ClasspathParameterResolverFactoryBean implements BeanClassLoaderAware, InitializingBean,
            FactoryBean<ParameterResolverFactory> {

        private ClassLoader classLoader;
        private ParameterResolverFactory factory;

        @Override
        public ParameterResolverFactory getObject() {
            return factory;
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
        public void afterPropertiesSet() {
            this.factory = ClasspathParameterResolverFactory.forClassLoader(classLoader);
        }

        @Override
        public void setBeanClassLoader(@Nonnull ClassLoader classLoader) {
            this.classLoader = classLoader;
        }
    }
}
