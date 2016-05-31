/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.common.caching.NoCache;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.eventhandling.saga.repository.CachingSagaStore;
import org.axonframework.eventhandling.saga.repository.jdbc.GenericSagaSqlSchema;
import org.axonframework.eventhandling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.spring.config.AutowiredBean;
import org.axonframework.spring.jdbc.SpringDataSourceConnectionProvider;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import javax.sql.DataSource;

/**
 * BeanDefinitionParser that provides Spring namespace support for defining JDBC Saga Repositories.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class JdbcSagaRepositoryBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String RESOURCE_INJECTOR_ATTRIBUTE = "resource-injector";
    private static final String SAGA_SERIALIZER_ATTRIBUTE = "saga-serializer";
    private static final String SAGA_SERIALIZER_PROPERTY = "serializer";
    private static final String CONNECTION_PROVIDER = "connection-provider";
    private static final String DATA_SOURCE = "data-source";
    private static final String ATTRIBUTE_SAGA_CACHE = "saga-cache";
    private static final String SQL_SCHEMA = "sql-schema";
    private static final String ATTRIBUTE_ASSOCIATIONS_CACHE = "associations-cache";
    private static final String ELEMENT_CACHE_CONFIG = "cache-config";

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(JdbcSagaStore.class);
        parseResourceInjectorAttribute(element, builder);
        parseSagaSerializerAttribute(element, builder);
        parseConnectionProvider(element, builder);
        parseSqlSchema(element, builder);
        return parseCacheConfig(element, builder.getBeanDefinition());
    }

    private AbstractBeanDefinition parseCacheConfig(Element element, AbstractBeanDefinition beanDefinition) {
        final Element cacheConfigElement = DomUtils.getChildElementByTagName(element, ELEMENT_CACHE_CONFIG);
        if (cacheConfigElement != null) {
            GenericBeanDefinition cachedRepoDef = new GenericBeanDefinition();
            cachedRepoDef.setBeanClass(CachingSagaStore.class);
            final Object sagaCacheReference = cacheConfigElement.hasAttribute(ATTRIBUTE_SAGA_CACHE)
                    ? new RuntimeBeanReference(cacheConfigElement.getAttribute(ATTRIBUTE_SAGA_CACHE))
                    : NoCache.INSTANCE;
            final Object associationsCacheReference = cacheConfigElement.hasAttribute(ATTRIBUTE_ASSOCIATIONS_CACHE)
                    ? new RuntimeBeanReference(cacheConfigElement.getAttribute(ATTRIBUTE_ASSOCIATIONS_CACHE))
                    : NoCache.INSTANCE;
            cachedRepoDef.getConstructorArgumentValues().addIndexedArgumentValue(0, beanDefinition);
            cachedRepoDef.getConstructorArgumentValues().addIndexedArgumentValue(1, associationsCacheReference);
            cachedRepoDef.getConstructorArgumentValues().addIndexedArgumentValue(2, sagaCacheReference);
            return cachedRepoDef;
        }
        return beanDefinition;
    }

    private void parseSqlSchema(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(SQL_SCHEMA)) {
            builder.addConstructorArgReference(element.getAttribute(SQL_SCHEMA));
        } else {
            builder.addConstructorArgValue(BeanDefinitionBuilder.genericBeanDefinition(GenericSagaSqlSchema.class)
                                                                .getBeanDefinition());
        }
    }

    private void parseConnectionProvider(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(CONNECTION_PROVIDER)) {
            builder.addConstructorArgReference(element.getAttribute(CONNECTION_PROVIDER));
        } else {

            final BeanDefinitionBuilder dataSourceProviderBean = BeanDefinitionBuilder.genericBeanDefinition(
                    SpringDataSourceConnectionProvider.class);
            if (element.hasAttribute(DATA_SOURCE)) {
                dataSourceProviderBean.addConstructorArgReference(element.getAttribute(DATA_SOURCE));
            } else {
                dataSourceProviderBean.addConstructorArgValue(AutowiredBean.createAutowiredBean(DataSource.class));
            }

            builder.addConstructorArgValue(
                    BeanDefinitionBuilder.genericBeanDefinition(UnitOfWorkAwareConnectionProviderWrapper.class)
                                         .addConstructorArgValue(dataSourceProviderBean.getBeanDefinition())
                                         .getBeanDefinition()
            );
        }
    }

    private void parseSagaSerializerAttribute(Element element, BeanDefinitionBuilder beanDefinition) {
        if (element.hasAttribute(SAGA_SERIALIZER_ATTRIBUTE)) {
            beanDefinition.addPropertyReference(SAGA_SERIALIZER_PROPERTY,
                                                element.getAttribute(SAGA_SERIALIZER_ATTRIBUTE));
        }
    }

    private void parseResourceInjectorAttribute(Element element, BeanDefinitionBuilder beanDefinition) {
        if (element.hasAttribute(RESOURCE_INJECTOR_ATTRIBUTE)) {
            beanDefinition.addPropertyReference("resourceInjector", element.getAttribute(RESOURCE_INJECTOR_ATTRIBUTE));
        } else {
            GenericBeanDefinition defaultResourceInjector = new GenericBeanDefinition();
            defaultResourceInjector.setBeanClass(SpringResourceInjector.class);
            beanDefinition.addPropertyValue("resourceInjector", defaultResourceInjector);
        }
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
