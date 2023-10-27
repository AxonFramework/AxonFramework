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

import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import javax.annotation.Nonnull;

/**
 * A {@link BeanDefinitionRegistryPostProcessor} implementation that scans for Saga types and registers a {@link
 * SpringSagaConfigurer configurer} for each Saga found.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class SpringSagaLookup implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SpringSagaLookup.class);

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            logger.warn("Given bean factory is not a BeanDefinitionRegistry. Cannot auto-configure Sagas");
            return;
        }

        String[] sagas = beanFactory.getBeanNamesForAnnotation(Saga.class);
        for (String saga : sagas) {
            if (beanFactory.containsBeanDefinition(saga + "$$Registrar")) {
                logger.info("Registrar for {} already available. Skipping configuration", saga);
                break;
            }

            Saga sagaAnnotation = beanFactory.findAnnotationOnBean(saga, Saga.class);
            Class<?> sagaType = beanFactory.getType(saga);

            BeanDefinitionBuilder beanDefinitionBuilder =
                    BeanDefinitionBuilder.genericBeanDefinition(SpringSagaConfigurer.class)
                                         .addConstructorArgValue(sagaType);

            if (sagaAnnotation != null && !"".equals(sagaAnnotation.sagaStore())) {
                beanDefinitionBuilder.addPropertyValue("sagaStore", sagaAnnotation.sagaStore());
            }
            BeanDefinitionRegistry bdRegistry = (BeanDefinitionRegistry) beanFactory;
            bdRegistry.registerBeanDefinition(saga + "$$Registrar",
                                              beanDefinitionBuilder.getBeanDefinition());
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(
            @Nonnull BeanDefinitionRegistry beanDefinitionRegistry
    ) throws BeansException {
        // No action required.
    }
}
