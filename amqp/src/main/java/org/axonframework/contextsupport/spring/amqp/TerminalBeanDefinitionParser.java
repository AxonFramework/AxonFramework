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

package org.axonframework.contextsupport.spring.amqp;

import org.axonframework.eventhandling.amqp.spring.ListenerContainerLifecycleManager;
import org.axonframework.eventhandling.amqp.spring.SpringAMQPTerminal;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * BeanDefinitionParser that parses &lt;amqp-terminal&gt; elements into a SpringAMQPTerminal instance.
 * <p/>
 * This parser ensures that the ListenerContainerLifecycleManager instance that the terminal uses is registered as a
 * top level bean. This is necessary for Spring to invoke its lifecycle methods.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class TerminalBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String CONTAINER_MANAGER_SUFFIX = "$containerManager";
    private static final String DEFAULT_CONFIG_ELEMENT = "default-configuration";
    private static final String PROPERTY_CONTAINER_LIFECYCLE_MANAGER = "listenerContainerLifecycleManager";
    private static final String PROPERTY_DEFAULT_CONFIGURATION = "defaultConfiguration";
    private final BeanDefinitionParser configurationParser = new AMQPConfigurationBeanDefinitionParser();

    private static final Map<String, String> BEAN_REFERENCE_PROPERTIES = new HashMap<String, String>();
    private static final Map<String, String> BEAN_VALUE_PROPERTIES = new HashMap<String, String>();

    static {
        BEAN_REFERENCE_PROPERTIES.put("connection-factory", "connectionFactory");
        BEAN_REFERENCE_PROPERTIES.put("message-converter", "messageConverter");
        BEAN_REFERENCE_PROPERTIES.put("routing-key-resolver", "routingKeyResolver");
        BEAN_REFERENCE_PROPERTIES.put("serializer", "serializer");
        BEAN_VALUE_PROPERTIES.put("durable", "durable");
        BEAN_VALUE_PROPERTIES.put("transactional", "transactional");
        BEAN_VALUE_PROPERTIES.put("exchange-name", "exchangeName");
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition terminalDefinition = new GenericBeanDefinition();
        terminalDefinition.setBeanClass(SpringAMQPTerminal.class);
        GenericBeanDefinition listenerContainerDefinition = createContainerManager(element, parserContext);
        final String containerBeanName = resolveId(element, terminalDefinition, parserContext)
                + CONTAINER_MANAGER_SUFFIX;

        terminalDefinition.getPropertyValues().add(PROPERTY_CONTAINER_LIFECYCLE_MANAGER,
                                                   new RuntimeBeanReference(containerBeanName));

        MutablePropertyValues props = terminalDefinition.getPropertyValues();
        for (Map.Entry<String, String> entry : BEAN_REFERENCE_PROPERTIES.entrySet()) {
            if (element.hasAttribute(entry.getKey())) {
                props.add(entry.getValue(), new RuntimeBeanReference(element.getAttribute(entry.getKey())));
            }
        }
        for (Map.Entry<String, String> entry : BEAN_VALUE_PROPERTIES.entrySet()) {
            if (element.hasAttribute(entry.getKey())) {
                props.add(entry.getValue(), element.getAttribute(entry.getKey()));
            }
        }

        parserContext.getRegistry().registerBeanDefinition(containerBeanName, listenerContainerDefinition);

        return terminalDefinition;
    }

    private GenericBeanDefinition createContainerManager(Element element, ParserContext parserContext) {
        GenericBeanDefinition listenerContainerDefinition = new GenericBeanDefinition();
        listenerContainerDefinition.setBeanClass(ListenerContainerLifecycleManager.class);

        // If a connection factory is defined on the terminal, it should also be used by the container manager
        if (element.hasAttribute("connection-factory")) {
            listenerContainerDefinition.getPropertyValues().add("connectionFactory",
                                                                new RuntimeBeanReference(element.getAttribute(
                                                                        "connection-factory")));
        }

        final Element defaultConfig = DomUtils.getChildElementByTagName(element, DEFAULT_CONFIG_ELEMENT);
        if (defaultConfig != null) {
            listenerContainerDefinition.getPropertyValues()
                                       .add(PROPERTY_DEFAULT_CONFIGURATION,
                                            configurationParser.parse(defaultConfig, ctx(parserContext,
                                                                                         listenerContainerDefinition)));
        }
        return listenerContainerDefinition;
    }

    private ParserContext ctx(ParserContext parserContext, GenericBeanDefinition listenerContainerDefinition) {
        return new ParserContext(parserContext.getReaderContext(),
                                 parserContext.getDelegate(),
                                 listenerContainerDefinition);
    }
}
