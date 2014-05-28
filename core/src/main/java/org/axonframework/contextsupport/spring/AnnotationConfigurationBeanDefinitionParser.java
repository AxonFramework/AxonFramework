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

import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerBeanPostProcessor;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import static org.axonframework.contextsupport.spring.SpringContextParameterResolverFactoryBuilder.getBeanReference;

/**
 * The AnnotationConfigurationBeanDefinitionParser is responsible for parsing the annotation-config element from the
 * Axon namespace. The parser registers {@link org.springframework.beans.factory.config.BeanDefinition}s for an {@link
 * AnnotationCommandHandlerBeanPostProcessor} and an
 * {@link org.axonframework.eventhandling.annotation.AnnotationEventListenerBeanPostProcessor}, with optional
 * configuration for an explicit {@link org.axonframework.commandhandling.CommandBus}, {@link
 * org.axonframework.eventhandling.EventBus} and {@link java.util.concurrent.Executor} instance.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class AnnotationConfigurationBeanDefinitionParser extends AbstractBeanDefinitionParser {

    /**
     * The eventBus attribute text.
     */
    private static final String EVENT_BUS_ATTRIBUTE = "event-bus";
    /**
     * The commandBus attribute text.
     */
    private static final String COMMAND_BUS_ATTRIBUTE = "command-bus";

    /**
     * The bean name used for registering the {@link AnnotationEventListenerBeanPostProcessor}.
     */
    private static final String EVENT_LISTENER_BEAN_NAME = "__axon-annotation-event-listener-bean-post-processor";
    /**
     * The bean name used for registering the {@link AnnotationCommandHandlerBeanPostProcessor}.
     */
    private static final String COMMAND_HANDLER_BEAN_NAME = "__axon-annotation-command-handler-bean-post-processor";
    private static final String PHASE_ATTRIBUTE = "phase";
    private static final String UNSUBSCRIBE_ON_SHUTDOWN_ATTRIBUTE = "unsubscribe-handlers-on-shutdown";

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        String phase = element.hasAttribute(PHASE_ATTRIBUTE) ? element.getAttribute(PHASE_ATTRIBUTE) : null;
        String unsubscribe = element.hasAttribute(UNSUBSCRIBE_ON_SHUTDOWN_ATTRIBUTE) ? element.getAttribute(
                UNSUBSCRIBE_ON_SHUTDOWN_ATTRIBUTE) : null;
        String eventBus = element.hasAttribute(EVENT_BUS_ATTRIBUTE) ? element.getAttribute(EVENT_BUS_ATTRIBUTE) : null;
        String commandBus = element.hasAttribute(COMMAND_BUS_ATTRIBUTE) ? element
                .getAttribute(COMMAND_BUS_ATTRIBUTE) : null;
        registerAnnotationCommandHandlerBeanPostProcessor(commandBus, phase,
                                                          unsubscribe, parserContext.getRegistry());
        registerAnnotationEventListenerBeanPostProcessor(eventBus, phase, unsubscribe,
                                                         parserContext.getRegistry());
        return null;
    }

    /**
     * Create the {@link org.springframework.beans.factory.config.BeanDefinition} for the {@link
     * AnnotationEventListenerBeanPostProcessor} and register it.
     *
     * @param eventBus              The bean name of the event bus to subscribe to
     * @param phase                 The lifecycle phase for the post processor
     * @param unsubscribeOnShutdown Whether to unsubscribe beans on shutdown
     * @param registry              The registry containing bean definitions
     */
    public void registerAnnotationEventListenerBeanPostProcessor(String eventBus, String phase,
                                                                 String unsubscribeOnShutdown,
                                                                 BeanDefinitionRegistry registry) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationEventListenerBeanPostProcessor.class);
        beanDefinition.getPropertyValues().add("parameterResolverFactory",
                                               getBeanReference(registry));
        if (StringUtils.hasText(phase)) {
            beanDefinition.getPropertyValues().add("phase", phase);
        }
        if (StringUtils.hasText(unsubscribeOnShutdown)) {
            beanDefinition.getPropertyValues().add("unsubscribeOnShutdown", unsubscribeOnShutdown);
        }
        if (StringUtils.hasText(eventBus)) {
            RuntimeBeanReference beanReference = new RuntimeBeanReference(eventBus);
            beanDefinition.getPropertyValues().addPropertyValue("eventBus", beanReference);
        }
        registry.registerBeanDefinition(EVENT_LISTENER_BEAN_NAME, beanDefinition);
    }

    /**
     * Create the {@link org.springframework.beans.factory.config.BeanDefinition} for the {@link
     * AnnotationCommandHandlerBeanPostProcessor} and register it.
     *
     * @param commandBus            The bean name of the command bus to subscribe to
     * @param phase                 The lifecycle phase for the post processor
     * @param unsubscribeOnShutdown Whether to unsubscribe beans on shutdown
     * @param registry              The registry containing bean definitions
     */
    public void registerAnnotationCommandHandlerBeanPostProcessor(String commandBus, String phase,
                                                                  String unsubscribeOnShutdown,
                                                                  BeanDefinitionRegistry registry) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationCommandHandlerBeanPostProcessor.class);
        beanDefinition.getPropertyValues().add("parameterResolverFactory",
                                               getBeanReference(
                                                       registry));
        if (StringUtils.hasText(phase)) {
            beanDefinition.getPropertyValues().add("phase", phase);
        }
        if (StringUtils.hasText(unsubscribeOnShutdown)) {
            beanDefinition.getPropertyValues().add("unsubscribeOnShutdown",
                                                   unsubscribeOnShutdown);
        }

        if (StringUtils.hasText(commandBus)) {
            RuntimeBeanReference beanReference = new RuntimeBeanReference(commandBus);
            beanDefinition.getPropertyValues().addPropertyValue("commandBus", beanReference);
        }

        registry.registerBeanDefinition(COMMAND_HANDLER_BEAN_NAME, beanDefinition);
    }
}
