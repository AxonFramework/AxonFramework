/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.common.annotation.SpringBeanParameterResolverFactory;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerBeanPostProcessor;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

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

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        registerAnnotationCommandHandlerBeanPostProcessor(element, parserContext);
        registerAnnotationEventListenerBeanPostProcessor(element, parserContext);
        registerAnnotationHandlerResourceInjectingPostProcessor(parserContext);
        return null;
    }

    private void registerAnnotationHandlerResourceInjectingPostProcessor(ParserContext parserContext) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(SpringBeanParameterResolverFactory.class);
        parserContext.getRegistry().registerBeanDefinition("beanParameterResolverFactory", beanDefinition);
    }

    /**
     * Create the {@link org.springframework.beans.factory.config.BeanDefinition} for the {@link
     * AnnotationEventListenerBeanPostProcessor} and register it.
     *
     * @param element       The {@link Element} being parsed.
     * @param parserContext The running {@link ParserContext}.
     */
    private void registerAnnotationEventListenerBeanPostProcessor(Element element, ParserContext parserContext) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationEventListenerBeanPostProcessor.class);
        if (element.hasAttribute(EVENT_BUS_ATTRIBUTE)) {
            String eventBusReference = element.getAttribute(EVENT_BUS_ATTRIBUTE);
            RuntimeBeanReference beanReference = new RuntimeBeanReference(eventBusReference);
            beanDefinition.getPropertyValues().addPropertyValue("eventBus", beanReference);
        }
        parserContext.getRegistry().registerBeanDefinition(EVENT_LISTENER_BEAN_NAME, beanDefinition);
    }

    /**
     * Create the {@link org.springframework.beans.factory.config.BeanDefinition} for the {@link
     * AnnotationCommandHandlerBeanPostProcessor} and register it.
     *
     * @param element       The {@link Element} being parsed.
     * @param parserContext The running {@link ParserContext}.
     */
    private void registerAnnotationCommandHandlerBeanPostProcessor(Element element, ParserContext parserContext) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AnnotationCommandHandlerBeanPostProcessor.class);
        if (element.hasAttribute(COMMAND_BUS_ATTRIBUTE)) {
            String commandBusReference = element.getAttribute(COMMAND_BUS_ATTRIBUTE);
            RuntimeBeanReference beanReference = new RuntimeBeanReference(commandBusReference);
            beanDefinition.getPropertyValues().addPropertyValue("commandBus", beanReference);
        }

        parserContext.getRegistry().registerBeanDefinition(COMMAND_HANDLER_BEAN_NAME, beanDefinition);
    }
}
