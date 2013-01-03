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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.HybridJpaRepository;
import org.axonframework.repository.GenericJpaRepository;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.w3c.dom.Element;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBean;

/**
 * @author Allard Buijze
 */
public class JpaRepositoryBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    private static final String ENTITY_MANAGER_PROVIDER = "entity-manager-provider";
    private static final String EVENT_BUS = "event-bus";
    private static final String EVENT_STORE = "event-store";
    private static final String LOCK_MANAGER = "lock-manager";
    private static final String LOCKING_STRATEGY = "locking-strategy";
    private static final String AGGREGATE_TYPE = "aggregate-type";

    @Override
    protected Class<?> getBeanClass(Element element) {
        if (element.hasAttribute(EVENT_STORE)) {
            return HybridJpaRepository.class;
        }
        return GenericJpaRepository.class;
    }

    @Override
    protected void doParse(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(ENTITY_MANAGER_PROVIDER)) {
            builder.addConstructorArgReference(element.getAttribute(ENTITY_MANAGER_PROVIDER));
        } else {
            builder.addConstructorArgValue(
                    BeanDefinitionBuilder.genericBeanDefinition(ContainerManagedEntityManagerProvider.class)
                                         .getBeanDefinition());
        }
        builder.addConstructorArgValue(element.getAttribute(AGGREGATE_TYPE));
        parseLockManager(element, builder);
        parseEventStore(element, builder);
        parseEventBus(element, builder);
    }

    private void parseLockManager(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(LOCK_MANAGER)) {
            builder.addConstructorArgReference(element.getAttribute(LOCK_MANAGER));
        } else if (element.hasAttribute(LOCKING_STRATEGY)) {
            LockingStrategy strategy = LockingStrategy.valueOf(element.getAttribute(LOCKING_STRATEGY));
            GenericBeanDefinition lockManager = new GenericBeanDefinition();
            lockManager.setBeanClass(strategy.getLockManagerType());
            builder.addConstructorArgValue(lockManager);
        }
    }

    private void parseEventBus(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_BUS)) {
            builder.addPropertyReference("eventBus", element.getAttribute(EVENT_BUS));
        } else {
            builder.addPropertyValue("eventBus", createAutowiredBean(EventBus.class));
        }
    }

    private void parseEventStore(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(EVENT_STORE)) {
            builder.addPropertyReference("eventStore", element.getAttribute(EVENT_STORE));
        }
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
