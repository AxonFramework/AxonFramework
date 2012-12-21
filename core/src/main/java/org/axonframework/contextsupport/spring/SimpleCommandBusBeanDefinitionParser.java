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

import org.axonframework.commandhandling.SimpleCommandBus;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

/**
 * The SimpleCommandBusBeanDefinitionParser does the actual work of parsing the <code>commandBus</code> element from
 * the
 * Axon namespace. This DefinitionParser creates a {@link org.axonframework.commandhandling.SimpleCommandBus} {@link
 * org.springframework.beans.factory.config.BeanDefinition}, with optional interceptors and subscribers.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class SimpleCommandBusBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String ATTRIBUTE_TRANSACTION_MANAGER = "transaction-manager";

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition commandBusDefinition = new GenericBeanDefinition();
        commandBusDefinition.setBeanClass(SimpleCommandBus.class);

        String attribute = element.getAttribute("register-mbeans");
        if (!"".equals(attribute)) {
            commandBusDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, attribute);
        }

        parseDispatchInterceptorConfiguration(element, parserContext, commandBusDefinition);
        parseHandlerInterceptorConfiguration(element, parserContext, commandBusDefinition);
        if (element.hasAttribute(ATTRIBUTE_TRANSACTION_MANAGER)) {
            final BeanDefinition txManager =
                    BeanDefinitionBuilder.genericBeanDefinition(TransactionManagerFactoryBean.class)
                                         .addPropertyReference("transactionManager",
                                                               element.getAttribute(ATTRIBUTE_TRANSACTION_MANAGER))
                                         .getBeanDefinition();
            commandBusDefinition.getPropertyValues().add("transactionManager", txManager);
        }

        return commandBusDefinition;
    }

    /**
     * Handles any optional interceptor-related configuration.
     *
     * @param element              The {@link Element} being parsed.
     * @param parserContext        The running {@link ParserContext}.
     * @param commandBusDefinition The {@link org.springframework.beans.factory.config.BeanDefinition} being built.
     */
    private void parseHandlerInterceptorConfiguration(Element element, ParserContext parserContext,
                                                      GenericBeanDefinition commandBusDefinition) {
        Element interceptorsElement = DomUtils.getChildElementByTagName(element, "handlerInterceptors");
        if (interceptorsElement != null) {
            List<?> interceptorsList = parserContext.getDelegate().parseListElement(interceptorsElement,
                                                                                    commandBusDefinition);
            commandBusDefinition.getPropertyValues().add("handlerInterceptors", interceptorsList);
        }
    }

    /**
     * Handles any optional interceptor-related configuration.
     *
     * @param element              The {@link Element} being parsed.
     * @param parserContext        The running {@link ParserContext}.
     * @param commandBusDefinition The {@link org.springframework.beans.factory.config.BeanDefinition} being built.
     */
    private void parseDispatchInterceptorConfiguration(Element element, ParserContext parserContext,
                                                       GenericBeanDefinition commandBusDefinition) {
        Element interceptorsElement = DomUtils.getChildElementByTagName(element, "dispatchInterceptors");
        if (interceptorsElement != null) {
            List<?> interceptorsList = parserContext.getDelegate().parseListElement(interceptorsElement,
                                                                                    commandBusDefinition);
            commandBusDefinition.getPropertyValues().add("dispatchInterceptors", interceptorsList);
        }
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
