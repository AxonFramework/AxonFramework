/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.commandhandling.SimpleCommandBus;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

/**
 * The SimpleCommandBusBeanDefinitionParser does the actual work of parsing the <code>commandBus</code> element from the
 * Axon namespace. This DefinitionParser creates a {@link org.axonframework.commandhandling.SimpleCommandBus} {@link
 * org.springframework.beans.factory.config.BeanDefinition}, with optional interceptors and subscribers.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class SimpleCommandBusBeanDefinitionParser extends AbstractBeanDefinitionParser implements BeanDefinitionParser {

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition commandBusDefinition = new GenericBeanDefinition();
        commandBusDefinition.setBeanClass(SimpleCommandBus.class);

        parseInterceptorConfiguration(element, parserContext, commandBusDefinition);

        return commandBusDefinition;
    }

    /**
     * Handles any optional interceptor-related configuration.
     *
     * @param element              The {@link Element} being parsed.
     * @param parserContext        The running {@link ParserContext}.
     * @param commandBusDefinition The {@link BeanDefinition} being built.
     */
    private void parseInterceptorConfiguration(Element element, ParserContext parserContext,
                                               GenericBeanDefinition commandBusDefinition) {
        Element interceptorsElement = DomUtils.getChildElementByTagName(element, "interceptors");
        if (interceptorsElement != null) {
            List<?> interceptorsList = parserContext.getDelegate().parseListElement(interceptorsElement,
                                                                                    commandBusDefinition);
            commandBusDefinition.getPropertyValues().add("interceptors", interceptorsList);
        }
    }

}
