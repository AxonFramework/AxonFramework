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

package org.axonframework.contextsupport.spring;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * Spring @Configuration related class that adds Axon Annotation PostProcessors to the BeanDefinitionRegistry.
 *
 * @author Allard Buijze
 * @see org.axonframework.contextsupport.spring.AnnotationDriven
 * @since 2.3
 */
public class AnnotationDrivenConfiguration implements ImportBeanDefinitionRegistrar {

    private static final String ANNOTATION_TYPE = AnnotationDriven.class.getName();

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        final AnnotationConfigurationBeanDefinitionParser parser = new AnnotationConfigurationBeanDefinitionParser();
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(ANNOTATION_TYPE);
        parser.registerAnnotationCommandHandlerBeanPostProcessor(getCommandBus(attributes), getPhase(attributes),
                                                                 getUnsubscribeOnShutdown(attributes),
                                                                 registry);
        parser.registerAnnotationEventListenerBeanPostProcessor(getEventBus(attributes), getPhase(attributes),
                                                                getUnsubscribeOnShutdown(attributes),
                                                                registry);
    }

    private String getEventBus(Map<String, Object> attributes) {
        final Object eventBus = attributes.get("eventBus");
        return eventBus == null ? null : eventBus.toString();
    }

    private String getCommandBus(Map<String, Object> attributes) {
        final Object commandBus = attributes.get("commandBus");
        return commandBus == null ? null : commandBus.toString();
    }

    private String getUnsubscribeOnShutdown(Map<String, Object> attributes) {
        final Boolean unsubscribeOnShutdown = (Boolean) attributes.get("unsubscribeOnShutdown");
        return unsubscribeOnShutdown == null ? null : Boolean.toString(unsubscribeOnShutdown);
    }

    private String getPhase(Map<String, Object> attributes) {
        final Integer phase = (Integer) attributes.get("phase");
        return phase == null ? null : Integer.toString(phase);
    }
}
