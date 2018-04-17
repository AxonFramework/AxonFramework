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

import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.spring.config.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.axonframework.spring.config.annotation.AnnotationQueryHandlerBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.axonframework.spring.config.annotation.SpringContextParameterResolverFactoryBuilder.getBeanReference;

/**
 * Spring @Configuration related class that adds Axon Annotation PostProcessors to the BeanDefinitionRegistry.
 *
 * @author Allard Buijze
 * @see AnnotationDriven
 * @since 2.3
 */
public class AnnotationDrivenRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * The bean name used for registering the {@link AnnotationCommandHandlerBeanPostProcessor}.
     */
    private static final String COMMAND_HANDLER_BEAN_NAME = "__axon-annotation-command-handler-bean-post-processor";
    private static final String QUERY_HANDLER_BEAN_NAME = "__axon-annotation-query-handler-bean-post-processor";


    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        registerAnnotationCommandHandlerBeanPostProcessor(registry);
        registerAnnotationQueryHandlerBeanPostProcessor(registry);

    }

    /**
     * Create the {@link org.springframework.beans.factory.config.BeanDefinition} for the {@link
     * AnnotationCommandHandlerBeanPostProcessor} and register it.
     *
     * @param registry The registry containing bean definitions
     */
    public void registerAnnotationCommandHandlerBeanPostProcessor(BeanDefinitionRegistry registry) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationCommandHandlerBeanPostProcessor.class);
        beanDefinition.getPropertyValues().add("parameterResolverFactory", getBeanReference(registry));
        beanDefinition.getPropertyValues().add("handlerDefinitions", findBeans(HandlerDefinition.class, registry));
        beanDefinition.getPropertyValues().add("handlerEnhancerDefinitions",
                                               findBeans(HandlerEnhancerDefinition.class, registry));

        registry.registerBeanDefinition(COMMAND_HANDLER_BEAN_NAME, beanDefinition);
    }

    public void registerAnnotationQueryHandlerBeanPostProcessor(BeanDefinitionRegistry registry) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationQueryHandlerBeanPostProcessor.class);
        beanDefinition.getPropertyValues().add("parameterResolverFactory", getBeanReference(registry));
        beanDefinition.getPropertyValues().add("handlerDefinitions", findBeans(HandlerDefinition.class, registry));
        beanDefinition.getPropertyValues().add("handlerEnhancerDefinitions",
                                               findBeans(HandlerEnhancerDefinition.class, registry));

        registry.registerBeanDefinition(QUERY_HANDLER_BEAN_NAME, beanDefinition);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> findBeans(Class<T> klass, BeanDefinitionRegistry registry) {
        return Arrays.stream(registry.getBeanDefinitionNames())
                     .map(registry::getBeanDefinition)
                     .filter(bd -> klass.equals(bd.getClass()))
                     .map(bd -> (T) bd)
                     .collect(Collectors.toList());
    }
}
