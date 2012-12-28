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

import org.axonframework.saga.SagaManager;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * BeanDefinitionParser that parses saga-manager elements in the application context.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SyncSagaManagerBeanDefinitionParser extends AbstractSagaManagerBeanDefinitionParser {

    private static final String SUPPRESS_EXCEPTIONS_ATTRIBUTE = "suppress-exceptions";

    @Override
    protected Class<? extends SagaManager> getBeanClass() {
        return AnnotatedSagaManager.class;
    }

    @Override
    protected void registerSagaRepository(Object sagaRepositoryDefinition,
                                          GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, sagaRepositoryDefinition);
    }

    @Override
    protected void registerSagaFactory(Object sagaFactoryDefinition, GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getConstructorArgumentValues().addGenericArgumentValue(sagaFactoryDefinition);
    }

    @Override
    protected void registerTypes(String[] types, GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(3, types);
    }

    @Override
    protected void registerEventBus(Object eventBusDefinition, GenericBeanDefinition sagaManagerDefinition) {
        sagaManagerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(2, eventBusDefinition);
    }

    @Override
    protected void registerSpecificProperties(Element element, ParserContext parserContext,
                                              GenericBeanDefinition sagaManagerDefinition) {
        // no specific properties here...
    }

    @Override
    protected void parseSuppressExceptionsAttribute(Element element, MutablePropertyValues beanDefinition)  {
        if (element.hasAttribute(SUPPRESS_EXCEPTIONS_ATTRIBUTE)) {
            beanDefinition.addPropertyValue("suppressExceptions", element.getAttribute(SUPPRESS_EXCEPTIONS_ATTRIBUTE));
        }
    }
}
