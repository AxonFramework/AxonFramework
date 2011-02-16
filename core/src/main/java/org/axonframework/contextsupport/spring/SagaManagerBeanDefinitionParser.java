/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.contextsupport.spring;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.saga.spring.SpringResourceInjector;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * BeanDefinitionParser that parses saga-manager elements in the application context.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SagaManagerBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String EVENT_BUS_ATTRIBUTE = "event-bus";
    private static final String SAGA_FACTORY_ATTRIBUTE = "saga-factory";

    private Object resourceInjector;
    private static final String RESOURCE_INJECTOR_ATTRIBUTE = "resource-injector";
    private static final String SAGA_REPOSITORY_ATTRIBUTE = "saga-repository";

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition sagaManagerDefinition = new GenericBeanDefinition();
        sagaManagerDefinition.setBeanClass(AnnotatedSagaManager.class);
        parseResourceInjectorAttribute(element);

        parseSagaRepositoryAttribute(element, parserContext, sagaManagerDefinition.getConstructorArgumentValues());
        parseSagaFactoryAttribute(element, sagaManagerDefinition.getConstructorArgumentValues());
        parseEventBusAttribute(element, sagaManagerDefinition.getConstructorArgumentValues());
        parseTypesElement(element, sagaManagerDefinition);

        return sagaManagerDefinition;
    }

    private void parseTypesElement(Element element, GenericBeanDefinition sagaManagerDefinition) {
        Element childNode = DomUtils.getChildElementByTagName(element, "types");
        sagaManagerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(3, childNode.getTextContent());
    }

    private void parseResourceInjectorAttribute(Element element) {
        if (element.hasAttribute(RESOURCE_INJECTOR_ATTRIBUTE)) {
            resourceInjector = new RuntimeBeanReference(element.getAttribute(RESOURCE_INJECTOR_ATTRIBUTE));
        }
    }

    private void parseEventBusAttribute(Element element, ConstructorArgumentValues properties) {
        if (element.hasAttribute(EVENT_BUS_ATTRIBUTE)) {
            properties.addIndexedArgumentValue(2, new RuntimeBeanReference(element.getAttribute(EVENT_BUS_ATTRIBUTE)));
        } else {
            properties.addIndexedArgumentValue(2, new AutowireCandidateQualifier(EventBus.class));
        }
    }

    private void parseSagaFactoryAttribute(Element element, ConstructorArgumentValues properties) {
        if (element.hasAttribute(SAGA_FACTORY_ATTRIBUTE)) {
            properties.addGenericArgumentValue(new RuntimeBeanReference(element.getAttribute(SAGA_FACTORY_ATTRIBUTE)));
        } else {
            GenericBeanDefinition defaultFactoryDefinition = new GenericBeanDefinition();
            defaultFactoryDefinition.setBeanClass(GenericSagaFactory.class);
            defaultFactoryDefinition.getPropertyValues().add("resourceInjector", getResourceInjector());
            properties.addIndexedArgumentValue(1, defaultFactoryDefinition);
        }
    }

    private void parseSagaRepositoryAttribute(Element element, ParserContext context,
                                              ConstructorArgumentValues arguments) {
        if (element.hasAttribute(SAGA_REPOSITORY_ATTRIBUTE)) {
            arguments.addIndexedArgumentValue(0,
                                              new RuntimeBeanReference(element.getAttribute(SAGA_REPOSITORY_ATTRIBUTE)));
        } else {
            GenericBeanDefinition bean = new GenericBeanDefinition();
            bean.setBeanClass(InMemorySagaRepository.class);
            context.getRegistry().registerBeanDefinition("sagaRepository", bean);
            arguments.addIndexedArgumentValue(0, new RuntimeBeanReference("sagaRepository"));
        }
    }

    private Object getResourceInjector() {
        if (resourceInjector == null) {
            GenericBeanDefinition bean = new GenericBeanDefinition();
            bean.setBeanClass(SpringResourceInjector.class);
            resourceInjector = bean;
        }
        return resourceInjector;
    }
}
