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

import org.axonframework.common.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.eventstore.jpa.JpaEventStore;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBeanWithFallback;

/**
 * The JpaEventStoreBeanDefinitionParser is responsible for parsing the <code>eventStore</code> element form the Axon
 * namespace. It creates a {@link org.springframework.beans.factory.config.BeanDefinition} based either on a {@link
 * org.axonframework.eventstore.jpa.JpaEventStore} or on a {@link org.axonframework.eventstore.fs.FileSystemEventStore},
 * depending on the selected configuration.
 *
 * @author Ben Z. Tels
 * @author Allard Buijze
 * @author Rob van der Linden Vooren
 * @since 0.7
 */
public class JpaEventStoreBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    private UpcasterChainBeanDefinitionParser upcasterChainParser = new UpcasterChainBeanDefinitionParser();
    /**
     * the event serializer attribute.
     */
    private static final String EVENT_SERIALIZER_ATTRIBUTE = "event-serializer";
    private static final String DATA_SOURCE_ATTRIBUTE = "data-source";
    private static final String PERSISTENCE_EXCEPTION_RESOLVER_ATTRIBUTE = "persistence-exception-resolver";
    private static final String MAX_SNAPSHOTS_ARCHIVED_ATTRIBUTE = "max-snapshots-archived";
    private static final String BATCH_SIZE_ATTRIBUTE = "batch-size";
    private static final String ENTITY_MANAGER_PROVIDER = "entity-manager-provider";
    private static final String UPCASTERS_ELEMENT = "upcasters";

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
        if (element.hasAttribute(ENTITY_MANAGER_PROVIDER)) {
            builder.addConstructorArgReference(element.getAttribute(ENTITY_MANAGER_PROVIDER));
        } else {
            builder.addConstructorArgValue(
                    BeanDefinitionBuilder.genericBeanDefinition(ContainerManagedEntityManagerProvider.class)
                                         .getBeanDefinition());
        }
        Object serializer;
        if (element.hasAttribute(EVENT_SERIALIZER_ATTRIBUTE)) {
            serializer = new RuntimeBeanReference(element.getAttribute(EVENT_SERIALIZER_ATTRIBUTE));
        } else {
            serializer = createAutowiredBeanWithFallback(new XStreamSerializer(), Serializer.class);
        }
        builder.addConstructorArgValue(serializer);
        if (element.hasAttribute(DATA_SOURCE_ATTRIBUTE)) {
            builder.addPropertyReference("dataSource", element.getAttribute(DATA_SOURCE_ATTRIBUTE));
        }
        if (element.hasAttribute(PERSISTENCE_EXCEPTION_RESOLVER_ATTRIBUTE)) {
            builder.addPropertyReference("persistenceExceptionResolver", element.getAttribute(
                    PERSISTENCE_EXCEPTION_RESOLVER_ATTRIBUTE));
        }
        if (element.hasAttribute(MAX_SNAPSHOTS_ARCHIVED_ATTRIBUTE)) {
            builder.addPropertyValue("maxSnapshotsArchived", element.getAttribute(MAX_SNAPSHOTS_ARCHIVED_ATTRIBUTE));
        }
        if (element.hasAttribute(BATCH_SIZE_ATTRIBUTE)) {
            builder.addPropertyValue("batchSize", element.getAttribute(BATCH_SIZE_ATTRIBUTE));
        }
        Element upcasters = DomUtils.getChildElementByTagName(element, UPCASTERS_ELEMENT);
        if (upcasters != null) {
            builder.addPropertyValue("upcasterChain", upcasterChainParser.parse(upcasters, parserContext, serializer));
        }
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
