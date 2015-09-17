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

import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.SagaManager;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.saga.spring.SpringResourceInjector;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract SagaManager parser that parses common properties for all SagaManager implementations.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractSagaManagerBeanDefinitionParser {

    private static final String DEFAULT_SAGA_REPOSITORY_ID = "sagaRepository$$DefaultInMemory";
    private static final String RESOURCE_INJECTOR_ATTRIBUTE = "resource-injector";
    private static final String SAGA_REPOSITORY_ATTRIBUTE = "saga-repository";
    private static final String CORRELATION_DATA_PROVIDER_ATTRIBUTE = "correlation-data-provider";
    private static final String ALLOW_REPLAY_ATTRIBUTE = "allow-replay";
    private static final String SAGA_FACTORY_ATTRIBUTE = "saga-factory";

    private Object resourceInjector;

    /**
     * Parses elements for shared SagaManager logic.
     *
     * @param element       The xml element containing the Bean Definition
     * @param parserContext The context for the parser
     * @return a BeanDefinition for the bean defined in the element
     */
    protected final AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition sagaManagerDefinition = new GenericBeanDefinition();
        sagaManagerDefinition.setBeanClass(getBeanClass());

        parseResourceInjectorAttribute(element);
        parseSagaRepositoryAttribute(element, parserContext, sagaManagerDefinition);
        parseSagaFactoryAttribute(element, sagaManagerDefinition);
        parseTypesElement(element, sagaManagerDefinition, parserContext.getRegistry());
        parseSuppressExceptionsAttribute(element, sagaManagerDefinition.getPropertyValues());
        parseCorrelationDataProvderAttribute(element, sagaManagerDefinition.getPropertyValues());
        parseAllowReplayAttribute(element, sagaManagerDefinition.getPropertyValues());

        registerSpecificProperties(element, parserContext, sagaManagerDefinition);
        return sagaManagerDefinition;
    }

    private void parseCorrelationDataProvderAttribute(Element element, MutablePropertyValues properties) {
        if (element.hasAttribute(CORRELATION_DATA_PROVIDER_ATTRIBUTE)) {
            properties.add("correlationDataProvider",
                           new RuntimeBeanReference(element.getAttribute(CORRELATION_DATA_PROVIDER_ATTRIBUTE)));
        }
    }

    private void parseAllowReplayAttribute(Element element, MutablePropertyValues properties) {
        if (element.hasAttribute(ALLOW_REPLAY_ATTRIBUTE)) {
            properties.add("allowReplay", element.getAttribute(ALLOW_REPLAY_ATTRIBUTE));
        }
    }

    /**
     * Returns the type of bean to be created by this BeanDefinitionParser.
     *
     * @return the type of bean to be created by this BeanDefinitionParser.
     */
    protected abstract Class<? extends SagaManager> getBeanClass();

    /**
     * Registers the given <code>sagaRepositoryDefinition</code> in the given <code>sagaManagerDefinition</code>.
     *
     * @param sagaRepositoryDefinition The bean definition of the repository to register
     * @param sagaManagerDefinition    The definition of the saga manager to register the repository in.
     */
    protected abstract void registerSagaRepository(Object sagaRepositoryDefinition,
                                                   GenericBeanDefinition sagaManagerDefinition);

    /**
     * Registers the given <code>sagaFactoryDefinition</code> in the given <code>sagaManagerDefinition</code>.
     *
     * @param sagaFactoryDefinition The bean definition of the factory to register
     * @param sagaManagerDefinition The definition of the saga manager to register the factory in.
     */
    protected abstract void registerSagaFactory(Object sagaFactoryDefinition,
                                                GenericBeanDefinition sagaManagerDefinition);

    /**
     * Registers the given Saga <code>types</code> in the given <code>sagaManagerDefinition</code>.
     *
     * @param types                             The types of sagas found in the bean definition
     * @param sagaManagerDefinition             The definition of the saga manager to register the types in.
     * @param parameterResolverFactoryReference the reference to the ParameterResolverFactory bean
     */
    protected abstract void registerTypes(String[] types, GenericBeanDefinition sagaManagerDefinition,
                                          RuntimeBeanReference parameterResolverFactoryReference);

    /**
     * Registers any implementation specific properties found in the given <code>element</code> in the given
     * <code>sagaManagerDefinition</code>. The purpose of this method is to allow different elements to contain
     * properties specific to that type of implementation.
     *
     * @param element               The custom namespace element to parse
     * @param parserContext         The object encapsulating the current state of the parsing process; provides access
     *                              to a BeanDefinitionRegistry
     * @param sagaManagerDefinition The definition of the saga manager to register the custom properties in.
     */
    protected abstract void registerSpecificProperties(Element element, ParserContext parserContext,
                                                       GenericBeanDefinition sagaManagerDefinition);

    private void parseSagaRepositoryAttribute(Element element, ParserContext context,
                                              GenericBeanDefinition sagaManagerDefinition) {
        if (element.hasAttribute(SAGA_REPOSITORY_ATTRIBUTE)) {
            registerSagaRepository(new RuntimeBeanReference(element.getAttribute(SAGA_REPOSITORY_ATTRIBUTE)),
                                   sagaManagerDefinition);
        } else {
            GenericBeanDefinition bean = new GenericBeanDefinition();
            bean.setBeanClass(InMemorySagaRepository.class);
            context.getRegistry().registerBeanDefinition(DEFAULT_SAGA_REPOSITORY_ID, bean);
            registerSagaRepository(new RuntimeBeanReference(DEFAULT_SAGA_REPOSITORY_ID), sagaManagerDefinition);
        }
    }

    private void parseSagaFactoryAttribute(Element element, GenericBeanDefinition sagaManagerDefinition) {
        if (element.hasAttribute(SAGA_FACTORY_ATTRIBUTE)) {
            registerSagaFactory(new RuntimeBeanReference(element.getAttribute(SAGA_FACTORY_ATTRIBUTE)),
                                sagaManagerDefinition);
        } else {
            GenericBeanDefinition defaultFactoryDefinition = new GenericBeanDefinition();
            defaultFactoryDefinition.setBeanClass(GenericSagaFactory.class);
            defaultFactoryDefinition.getPropertyValues().add("resourceInjector", getResourceInjector());
            registerSagaFactory(defaultFactoryDefinition, sagaManagerDefinition);
        }
    }

    private void parseResourceInjectorAttribute(Element element) {
        if (element.hasAttribute(RESOURCE_INJECTOR_ATTRIBUTE)) {
            resourceInjector = new RuntimeBeanReference(element.getAttribute(RESOURCE_INJECTOR_ATTRIBUTE));
        }
    }

    private void parseTypesElement(Element element, GenericBeanDefinition sagaManagerDefinition,
                                   BeanDefinitionRegistry registry) {
        Set<String> filteredTypes = new HashSet<String>();

        // find explicitly names types
        Element typeNode = DomUtils.getChildElementByTagName(element, "types");
        if (typeNode != null) {
            final String[] types = typeNode.getTextContent().split("[,\\n]");
            for (String type : types) {
                if (StringUtils.hasText(type)) {
                    filteredTypes.add(type.trim());
                }
            }
        }

        if (element.hasAttribute("base-package")) {
            // find using classpath scanning
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(AbstractAnnotatedSaga.class));
            for (String basePackage : element.getAttribute("base-package").split(",")) {
                if (StringUtils.hasText(basePackage)) {
                    Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage.trim());
                    for (BeanDefinition bd : candidates) {
                        filteredTypes.add(bd.getBeanClassName());
                    }
                }
            }
        }
        registerTypes(filteredTypes.toArray(new String[filteredTypes.size()]), sagaManagerDefinition,
                      SpringContextParameterResolverFactoryBuilder.getBeanReference(
                              registry)
        );
    }

    /**
     * Process the "suppress-exceptions" setting on the given <code>element</code>.
     *
     * @param element        The element representing the saga manager's bean definition
     * @param beanDefinition The bean definition of the Saga Manager
     */
    protected abstract void parseSuppressExceptionsAttribute(Element element, MutablePropertyValues beanDefinition);

    private Object getResourceInjector() {
        if (resourceInjector == null) {
            GenericBeanDefinition bean = new GenericBeanDefinition();
            bean.setBeanClass(SpringResourceInjector.class);
            resourceInjector = bean;
        }
        return resourceInjector;
    }
}
