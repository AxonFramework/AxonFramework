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
 * The EventStoreBeanDefinitionParser is responsible for parsing the <code>eventStore</code> element form the Axon
 * namespace. It creates a {@link BeanDefinition} based either on a {@link JpaEventStore} or on a {@link
 * FileSystemEventStore}, depending on the selected configuration.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class EventStoreBeanDefinitionParser extends AbstractSingleBeanDefinitionParser implements BeanDefinitionParser {

    /**
     * The entity manager attribute.
     */
    private static final String ENTITY_MANAGER_ATTRIBUTE = "entityManager";
    /**
     * The base directory attribute.
     */
    private static final String BASE_DIR_ATTRIBUTE = "baseDir";
    /**
     * the event serializer attribute.
     */
    private static final String EVENT_SERIALIZER_ATTRIBUTE = "eventSerializer";
    /**
     * String that identifies a choice for a JPA event store.
     */
    private static final String JPA_EVENTSTORE_TYPE = "JPA";
    /**
     * String that identifies a choice for a file event store.
     */
    private static final String FILE_EVENTSTORE_TYPE = "FILE";
    /**
     * The type attribute.
     */
    private static final String TYPE_ATTRIBUTE = "type";

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> getBeanClass(Element element) {
        if (!element.hasAttribute(TYPE_ATTRIBUTE)) {
            throw new IllegalStateException(
                    "The eventStore element must declare a type attribute with the type of the event store.");
        }

        if (isFileEventStore(element)) {
            return FileSystemEventStore.class;
        }

        if (isJPAEventStore(element)) {
            return JpaEventStore.class;
        }

        throw new IllegalArgumentException(
                "The eventStore element's type attribute must have a value of either FILE or JPA");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_SERIALIZER_ATTRIBUTE)) {
            builder.addConstructorArgReference(element.getAttribute(EVENT_SERIALIZER_ATTRIBUTE));
        }

        if (isFileEventStore(element)) {
            // This attribute is required in the XSD.
            builder.addPropertyValue(BASE_DIR_ATTRIBUTE, element.getAttribute(BASE_DIR_ATTRIBUTE));
        }

        if (isJPAEventStore(element)) {
            if (element.hasAttribute(ENTITY_MANAGER_ATTRIBUTE)) {
                builder.addPropertyReference(ENTITY_MANAGER_ATTRIBUTE, element.getAttribute(ENTITY_MANAGER_ATTRIBUTE));
            }
        }
    }

    /**
     * Indicates whether or not the element is requesting a JPA event store.
     *
     * @param element The {@link Element} being parsed.
     * @return Whether or not the element is requesting a JPA event store.
     */
    private boolean isJPAEventStore(Element element) {
        return JPA_EVENTSTORE_TYPE.equals(element.getAttribute(TYPE_ATTRIBUTE));
    }

    /**
     * Indicates whether or not the element is requesting a FILE event store.
     *
     * @param element The {@link Element} being parsed.
     * @return Whether or not the element is requesting a FILE event store.
     */
    private boolean isFileEventStore(Element element) {
        return FILE_EVENTSTORE_TYPE.equals(element.getAttribute(TYPE_ATTRIBUTE));
    }

}
