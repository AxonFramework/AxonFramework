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

package org.axonframework.contextsupport.spring;

import org.axonframework.common.jdbc.SpringDataSourceConnectionProvider;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.eventstore.jdbc.DefaultEventEntryStore;
import org.axonframework.eventstore.jdbc.GenericEventSqlSchema;
import org.axonframework.eventstore.jdbc.JdbcEventStore;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import javax.sql.DataSource;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBeanWithFallback;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

/**
 * BeanDefinitionParser that provides Spring namespace support for defining JDBC Event Stores.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class JdbcEventStoreBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    private UpcasterChainBeanDefinitionParser upcasterChainParser = new UpcasterChainBeanDefinitionParser();

    private static final String EVENT_SERIALIZER_ATTRIBUTE = "event-serializer";
    private static final String DATA_SOURCE_ATTRIBUTE = "data-source";
    private static final String PERSISTENCE_EXCEPTION_RESOLVER_ATTRIBUTE = "persistence-exception-resolver";
    private static final String MAX_SNAPSHOTS_ARCHIVED_ATTRIBUTE = "max-snapshots-archived";
    private static final String BATCH_SIZE_ATTRIBUTE = "batch-size";
    private static final String CONNECTION_PROVIDER = "connection-provider";
    private static final String SQL_SCHEMA = "sql-schema";
    private static final String UPCASTERS_ELEMENT = "upcasters";
    private static final String EVENT_ENTRY_STORE_ATTRIBUTE = "event-entry-store-ref";

    @Override
    protected Class<?> getBeanClass(Element element) {
        return JdbcEventStore.class;
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_ENTRY_STORE_ATTRIBUTE)) {
            Object eventEntryStore = new RuntimeBeanReference(element.getAttribute(EVENT_ENTRY_STORE_ATTRIBUTE));
            builder.addConstructorArgValue(eventEntryStore);
        } else {
            BeanDefinitionBuilder eventEntryStore = genericBeanDefinition(DefaultEventEntryStore.class);
            if (element.hasAttribute(CONNECTION_PROVIDER)) {
                eventEntryStore.addConstructorArgReference(element.getAttribute(CONNECTION_PROVIDER));
            } else {
                final BeanDefinitionBuilder dataSourceConnectionProvider = genericBeanDefinition(
                        SpringDataSourceConnectionProvider.class);
                if (element.hasAttribute(DATA_SOURCE_ATTRIBUTE)) {
                    dataSourceConnectionProvider
                            .addConstructorArgReference(element.getAttribute(DATA_SOURCE_ATTRIBUTE));
                } else {
                    dataSourceConnectionProvider.addConstructorArgValue(AutowiredBean
                                                                                .createAutowiredBean(DataSource.class));
                }

                eventEntryStore.addConstructorArgValue(
                        genericBeanDefinition(UnitOfWorkAwareConnectionProviderWrapper.class)
                                .addConstructorArgValue(dataSourceConnectionProvider.getBeanDefinition())
                                .getBeanDefinition()
                );
            }

            if (element.hasAttribute(SQL_SCHEMA)) {
                eventEntryStore.addConstructorArgReference(element.getAttribute(SQL_SCHEMA));
            } else {
                eventEntryStore.addConstructorArgValue(
                        genericBeanDefinition(GenericEventSqlSchema.class).getBeanDefinition()
                );
            }
            builder.addConstructorArgValue(eventEntryStore.getBeanDefinition());
        }

        Object serializer;
        if (element.hasAttribute(EVENT_SERIALIZER_ATTRIBUTE)) {
            serializer = new RuntimeBeanReference(element.getAttribute(EVENT_SERIALIZER_ATTRIBUTE));
        } else {
            serializer = createAutowiredBeanWithFallback(new XStreamSerializer(), Serializer.class);
        }
        builder.addConstructorArgValue(serializer);

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
