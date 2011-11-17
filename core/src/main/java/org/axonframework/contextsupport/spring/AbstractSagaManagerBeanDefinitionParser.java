package org.axonframework.contextsupport.spring;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.SagaManager;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.saga.spring.SpringResourceInjector;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Abstract SagaManager parser that parses common properties for all SagaManager implementations.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractSagaManagerBeanDefinitionParser {

    private Object resourceInjector;
    private static final String RESOURCE_INJECTOR_ATTRIBUTE = "resource-injector";
    private static final String SAGA_REPOSITORY_ATTRIBUTE = "saga-repository";
    private static final String EVENT_BUS_ATTRIBUTE = "event-bus";
    private static final String SAGA_FACTORY_ATTRIBUTE = "saga-factory";

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
        parseEventBusAttribute(element, sagaManagerDefinition);
        parseTypesElement(element, sagaManagerDefinition);

        sagaManagerDefinition.setInitMethodName("subscribe");
        sagaManagerDefinition.setDestroyMethodName("unsubscribe");

        registerSpecificProperties(element, parserContext, sagaManagerDefinition);

        return sagaManagerDefinition;
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
     * @param types                 The types of sagas found in the bean definition
     * @param sagaManagerDefinition The definition of the saga manager to register the types in.
     */
    protected abstract void registerTypes(String[] types, GenericBeanDefinition sagaManagerDefinition);

    /**
     * Registers the given <code>eventBusDefinition</code> in the given <code>sagaManagerDefinition</code>.
     *
     * @param eventBusDefinition    The bean definition of the event bus to register
     * @param sagaManagerDefinition The definition of the saga manager to register the event bus in.
     */
    protected abstract void registerEventBus(Object eventBusDefinition, GenericBeanDefinition sagaManagerDefinition);

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
            context.getRegistry().registerBeanDefinition("sagaRepository", bean);
            registerSagaRepository(new RuntimeBeanReference("sagaRepository"), sagaManagerDefinition);
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

    private void parseTypesElement(Element element, GenericBeanDefinition sagaManagerDefinition) {
        Element childNode = DomUtils.getChildElementByTagName(element, "types");
        registerTypes(childNode.getTextContent().split(","), sagaManagerDefinition);
    }

    private void parseEventBusAttribute(Element element, GenericBeanDefinition beanDefinition) {
        if (element.hasAttribute(EVENT_BUS_ATTRIBUTE)) {
            registerEventBus(new RuntimeBeanReference(element.getAttribute(EVENT_BUS_ATTRIBUTE)), beanDefinition);
        } else {
            registerEventBus(new AutowiredBean(EventBus.class), beanDefinition);
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
