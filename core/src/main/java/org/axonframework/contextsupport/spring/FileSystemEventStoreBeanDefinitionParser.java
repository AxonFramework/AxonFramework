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
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * The FileSystemEventStoreBeanDefinitionParser is responsible for parsing the <code>eventStore</code> element form the
 * Axon namespace. It creates a {@link org.springframework.beans.factory.config.BeanDefinition} based either on a {@link
 * org.axonframework.eventstore.jpa.JpaEventStore} or on a {@link org.axonframework.eventstore.fs.FileSystemEventStore},
 * depending on the selected configuration.
 *
 * @author Ben Z. Tels
 * @author Allard Buijze
 * @since 0.7
 */
public class FileSystemEventStoreBeanDefinitionParser extends AbstractSingleBeanDefinitionParser
        implements BeanDefinitionParser {

    /**
     * The base directory attribute.
     */
    private static final String BASE_DIR_ATTRIBUTE = "base-dir";
    /**
     * the event serializer attribute.
     */
    private static final String EVENT_SERIALIZER_ATTRIBUTE = "event-serializer";

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> getBeanClass(Element element) {
        return FileSystemEventStore.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_SERIALIZER_ATTRIBUTE)) {
            builder.addConstructorArgReference(element.getAttribute(EVENT_SERIALIZER_ATTRIBUTE));
        }
        String baseDirValue = element.getAttribute(BASE_DIR_ATTRIBUTE);
        if (!baseDirValue.endsWith("/")) {
            baseDirValue = baseDirValue + "/";
        }
        builder.addPropertyValue("baseDir", baseDirValue);
    }
}
