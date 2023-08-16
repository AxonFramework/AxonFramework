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

package org.axonframework.spring.config;

import org.axonframework.spring.config.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.axonframework.spring.config.annotation.AnnotationQueryHandlerBeanPostProcessor;
import org.axonframework.spring.config.annotation.SpringContextHandlerDefinitionBuilder;
import org.axonframework.spring.config.annotation.SpringContextParameterResolverFactoryBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import javax.annotation.Nonnull;

/**
 * Spring @Configuration related class that adds Axon Annotation PostProcessors to the BeanDefinitionRegistry.
 *
 * @author Allard Buijze
 * @see AnnotationDriven
 * @since 2.3
 * @deprecated Use Spring Boot autoconfiguration or register the individual beans explicitly. {@link AnnotationDriven}
 * contains a list of look-up and registration components to replace this component with.
 */
@Deprecated
public class AnnotationDrivenRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * The bean name used for registering the {@link AnnotationCommandHandlerBeanPostProcessor}.
     */
    private static final String COMMAND_HANDLER_BEAN_NAME = "__axon-annotation-command-handler-bean-post-processor";
    private static final String QUERY_HANDLER_BEAN_NAME = "__axon-annotation-query-handler-bean-post-processor";


    @Override
    public void registerBeanDefinitions(@Nonnull AnnotationMetadata importingClassMetadata,
                                        @Nonnull BeanDefinitionRegistry registry) {
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
        beanDefinition.getPropertyValues().add("parameterResolverFactory",
                                               SpringContextParameterResolverFactoryBuilder.getBeanReference(registry));
        beanDefinition.getPropertyValues().add("handlerDefinition",
                                               SpringContextHandlerDefinitionBuilder.getBeanReference(registry));

        registry.registerBeanDefinition(COMMAND_HANDLER_BEAN_NAME, beanDefinition);
    }

    public void registerAnnotationQueryHandlerBeanPostProcessor(BeanDefinitionRegistry registry) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationQueryHandlerBeanPostProcessor.class);
        beanDefinition.getPropertyValues().add("parameterResolverFactory",
                                               SpringContextParameterResolverFactoryBuilder.getBeanReference(registry));
        beanDefinition.getPropertyValues().add("handlerDefinition",
                                               SpringContextHandlerDefinitionBuilder.getBeanReference(registry));

        registry.registerBeanDefinition(QUERY_HANDLER_BEAN_NAME, beanDefinition);
    }
}
