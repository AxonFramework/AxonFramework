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

import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.eventstore.jpa.JpaEventStore;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * The JpaEventStoreBeanDefinitionParser is responsible for parsing the <code>eventStore</code> element form the Axon
 * namespace. It creates a {@link BeanDefinition} based either on a {@link JpaEventStore} or on a {@link
 * FileSystemEventStore}, depending on the selected configuration.
 *
 * @author Ben Z. Tels
 * @author Allard Buijze
 * @since 0.7
 */
public class JpaEventStoreBeanDefinitionParser extends AbstractSingleBeanDefinitionParser
        implements BeanDefinitionParser {

    /**
     * The entity manager attribute.
     */
    private static final String ENTITY_MANAGER_ATTRIBUTE = "entity-manager";
    /**
     * the event serializer attribute.
     */
    private static final String EVENT_SERIALIZER_ATTRIBUTE = "event-serializer";

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> getBeanClass(Element element) {
        return JpaEventStore.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_SERIALIZER_ATTRIBUTE)) {
            builder.addConstructorArgReference(element.getAttribute(EVENT_SERIALIZER_ATTRIBUTE));
        }
        if (element.hasAttribute(ENTITY_MANAGER_ATTRIBUTE)) {
            builder.addPropertyReference("entityManager", element.getAttribute(ENTITY_MANAGER_ATTRIBUTE));
        }
    }
}
