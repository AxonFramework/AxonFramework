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

import org.axonframework.eventhandling.NoTransactionManager;
import org.axonframework.eventhandling.transactionmanagers.SpringTransactionManager;
import org.axonframework.saga.SagaManager;
import org.axonframework.saga.annotation.AsyncAnnotatedSagaManager;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * BeanDefinitionParser that parses saga-manager elements in the application context.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AsyncSagaManagerBeanDefinitionParser extends AbstractSagaManagerBeanDefinitionParser {

    private static final String EXECUTOR_ATTRIBUTE = "executor";
    private static final String TRANSACTION_MANAGER_ATTRIBUTE = "transaction-manager";
    private static final String PROCESSOR_COUNT_ATTRIBUTE = "processor-count";
    private static final String BUFFER_SIZE_ATTRIBUTE = "buffer-size";

    @Override
    protected void registerSpecificProperties(Element element, ParserContext parserContext,
                                              GenericBeanDefinition sagaManagerDefinition) {
        Element asyncElement = (Element) element.getElementsByTagName("axon:async").item(0);
        parseTransactionManagerAttribute(asyncElement, sagaManagerDefinition.getPropertyValues());
        parseExecutorAttribute(asyncElement, sagaManagerDefinition.getPropertyValues());
        parseProcessorCountAttribute(asyncElement, sagaManagerDefinition.getPropertyValues());
        parseBufferSizeAttribute(asyncElement, sagaManagerDefinition.getPropertyValues());
        sagaManagerDefinition.setInitMethodName("start");
        sagaManagerDefinition.setDestroyMethodName("stop");
    }

    @Override
    protected Class<? extends SagaManager> getBeanClass() {
        return AsyncAnnotatedSagaManager.class;
    }

    @Override
    protected void registerSagaRepository(Object sagaRepositoryDefinition,
                                          GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getPropertyValues().add("sagaRepository", sagaRepositoryDefinition);
    }

    @Override
    protected void registerSagaFactory(Object sagaFactoryDefinition, GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getPropertyValues().add("sagaFactory", sagaFactoryDefinition);
    }

    @Override
    protected void registerTypes(String[] types, GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(1, types);
    }

    @Override
    protected void registerEventBus(Object eventBusDefinition, GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, eventBusDefinition);
    }

    @Override
    protected void parseSuppressExceptionsAttribute(Element element, MutablePropertyValues beanDefinition) {
    }

    private void parseTransactionManagerAttribute(Element element, MutablePropertyValues propertyValues) {
        if (element.hasAttribute(TRANSACTION_MANAGER_ATTRIBUTE)) {
            BeanDefinition bd =
                    BeanDefinitionBuilder.genericBeanDefinition(SpringTransactionManager.class)
                                         .addPropertyReference("transactionManager",
                                                               element.getAttribute(TRANSACTION_MANAGER_ATTRIBUTE))
                                         .getBeanDefinition();
            propertyValues.addPropertyValue("transactionManager", bd);
        } else {
            propertyValues.addPropertyValue("transactionManager", new NoTransactionManager());
        }
    }

    private void parseExecutorAttribute(Element element, MutablePropertyValues propertyValues) {
        if (element.hasAttribute(EXECUTOR_ATTRIBUTE)) {
            propertyValues.addPropertyValue("executor",
                                            new RuntimeBeanReference(element.getAttribute(EXECUTOR_ATTRIBUTE)));
        }
    }

    private void parseProcessorCountAttribute(Element element, MutablePropertyValues propertyValues) {
        if (element.hasAttribute(PROCESSOR_COUNT_ATTRIBUTE)) {
            propertyValues.addPropertyValue("processorCount", element.getAttribute(PROCESSOR_COUNT_ATTRIBUTE));
        }
    }

    private void parseBufferSizeAttribute(Element element, MutablePropertyValues propertyValues) {
        if (element.hasAttribute(BUFFER_SIZE_ATTRIBUTE)) {
            propertyValues.addPropertyValue("bufferSize", element.getAttribute(BUFFER_SIZE_ATTRIBUTE));
        }
    }
}
