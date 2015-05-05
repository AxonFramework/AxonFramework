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

package org.axonframework.spring.config.xml;

import org.axonframework.spring.config.CommandHandlerSubscriber;
import org.axonframework.spring.config.EventListenerSubscriber;
import org.axonframework.spring.config.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.axonframework.spring.config.annotation.AnnotationDrivenRegistrar;
import org.axonframework.spring.config.annotation.AnnotationEventListenerBeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * The AnnotationConfigurationBeanDefinitionParser is responsible for parsing the annotation-config element from the
 * Axon namespace. The parser registers {@link org.springframework.beans.factory.config.BeanDefinition}s for an {@link
 * AnnotationCommandHandlerBeanPostProcessor} and an
 * {@link AnnotationEventListenerBeanPostProcessor}, with optional
 * configuration for an explicit {@link org.axonframework.commandhandling.CommandBus}, {@link
 * org.axonframework.eventhandling.EventBus} and {@link java.util.concurrent.Executor} instance.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class AnnotationConfigurationBeanDefinitionParser extends AbstractBeanDefinitionParser {

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        AnnotationDrivenRegistrar registrar = new AnnotationDrivenRegistrar();
        registrar.registerAnnotationCommandHandlerBeanPostProcessor(parserContext.getRegistry());
        registrar.registerAnnotationEventListenerBeanPostProcessor(parserContext.getRegistry());

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(
                CommandHandlerSubscriber.class);
        if (element.hasAttribute("command-bus")) {
            builder.addPropertyReference("commandBus", element.getAttribute("command-bus"));
        }
        parserContext.getRegistry().registerBeanDefinition("CommandHandlerSubscriber",
                                                           builder.getBeanDefinition());
        parserContext.getRegistry().registerBeanDefinition("EventListenerSubscriber",
                                                           BeanDefinitionBuilder.genericBeanDefinition(
                                                                   EventListenerSubscriber.class).getBeanDefinition());
        return null;
    }

}
