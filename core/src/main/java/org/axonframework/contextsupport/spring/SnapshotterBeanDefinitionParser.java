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

import org.axonframework.common.DirectExecutor;
import org.axonframework.eventsourcing.SpringAggregateSnapshotter;
import org.axonframework.eventstore.SnapshotEventStore;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBean;

/**
 * The SnapshotterBeanDefinitionParser is responsible for parsing the <code>snapshotter</code> element form the Axon
 * namespace. It creates a {@link org.springframework.beans.factory.config.BeanDefinition} based either on a {@link
 * SpringAggregateSnapshotter}.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class SnapshotterBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    private static final String EVENT_STORE_ATTRIBUTE = "event-store";
    private static final String EXECUTOR_ATTRIBUTE = "executor";
    private static final String TRANSACTION_MANAGER_ATTRIBUTE = "transaction-manager";

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> getBeanClass(Element element) {
        return SpringAggregateSnapshotter.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_STORE_ATTRIBUTE)) {
            builder.addPropertyReference("eventStore", element.getAttribute(EVENT_STORE_ATTRIBUTE));
        } else {
            builder.addPropertyValue("eventStore", createAutowiredBean(SnapshotEventStore.class));
        }

        if (element.hasAttribute(EXECUTOR_ATTRIBUTE)) {
            builder.addPropertyReference("executor", element.getAttribute(EXECUTOR_ATTRIBUTE));
        } else {
            builder.addPropertyValue("executor", DirectExecutor.INSTANCE);
        }

        if (element.hasAttribute(TRANSACTION_MANAGER_ATTRIBUTE)) {
            builder.addPropertyReference("transactionManager", element.getAttribute(TRANSACTION_MANAGER_ATTRIBUTE));
        }
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
