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

import org.axonframework.messaging.annotation.MultiHandlerDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * Creates and registers a bean definition for a Spring Context aware HandlerDefinition. It ensures that only
 * one such instance exists for each ApplicationContext.
 *
 * @author Tyler Thrailkill
 * @author Milan Savic
 * @since 3.3
 * @deprecated Replaced by the {@link HandlerDefinitionFactoryBean}.
 */
@Deprecated
public final class SpringContextHandlerDefinitionBuilder {

    private static final String HANDLER_DEFINITION_BEAN_NAME = "__axon-handler-definition";

    private SpringContextHandlerDefinitionBuilder() {
    }

    /**
     * Create, if necessary, a bean definition for a HandlerDefinition and returns the reference to bean for use in
     * other Bean Definitions.
     *
     * @param registry The registry in which to look for an already existing instance
     * @return a BeanReference to the BeanDefinition for the HandlerDefinition
     */
    public static RuntimeBeanReference getBeanReference(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(HANDLER_DEFINITION_BEAN_NAME)) {
            BeanDefinition definition = BeanDefinitionBuilder
                    .genericBeanDefinition(SpringHandlerDefinitionBean.class).getBeanDefinition();
            BeanDefinition enhancer = BeanDefinitionBuilder
                    .genericBeanDefinition(SpringHandlerEnhancerDefinitionBean.class).getBeanDefinition();

            AbstractBeanDefinition def = BeanDefinitionBuilder.genericBeanDefinition(MultiHandlerDefinition.class)
                                                              .addConstructorArgValue(definition)
                                                              .addConstructorArgValue(enhancer)
                                                              .getBeanDefinition();
            def.setPrimary(true);
            registry.registerBeanDefinition(HANDLER_DEFINITION_BEAN_NAME, def);
        }
        return new RuntimeBeanReference(HANDLER_DEFINITION_BEAN_NAME);
    }
}
