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

import org.axonframework.eventhandling.AutowiringClusterSelector;
import org.axonframework.eventhandling.ClusteringEventBus;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * The EventBusBeanDefinitionParser is responsible for parsing the <code>eventBus</code> element from the Axon
 * namespace. The parser will create a {@link org.springframework.beans.factory.config.BeanDefinition} based on a
 * {@link
 * org.axonframework.eventhandling.SimpleEventBus}.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class EventBusBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String TERMINAL_ATTRIBUTE = "terminal";
    private static final String CLUSTER_SELECTOR_REF = "cluster-selector";

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition eventBusDefinition = new GenericBeanDefinition();
        eventBusDefinition.setBeanClass(ClusteringEventBus.class);

        String clusterSelectorRef = element.getAttribute(CLUSTER_SELECTOR_REF);
        if (StringUtils.hasText(clusterSelectorRef)) {
            eventBusDefinition.getConstructorArgumentValues()
                              .addIndexedArgumentValue(0,
                                                       new RuntimeBeanReference(clusterSelectorRef));
        } else {
            eventBusDefinition.getConstructorArgumentValues()
                              .addIndexedArgumentValue(0, BeanDefinitionBuilder
                                      .genericBeanDefinition(AutowiringClusterSelector.class).getBeanDefinition());
        }

        String terminalRef = element.getAttribute(TERMINAL_ATTRIBUTE);
        if (StringUtils.hasText(terminalRef)) {
            eventBusDefinition.getConstructorArgumentValues()
                              .addIndexedArgumentValue(1,
                                                       new RuntimeBeanReference(terminalRef));
        }

        return eventBusDefinition;
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
