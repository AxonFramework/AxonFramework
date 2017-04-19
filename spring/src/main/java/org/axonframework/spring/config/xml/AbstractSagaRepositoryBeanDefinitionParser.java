package org.axonframework.spring.config.xml;

import org.axonframework.common.caching.NoCache;
import org.axonframework.eventhandling.saga.repository.CachingSagaStore;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Abstract bean definition parser that provides Spring namespace support for defining Saga Repositories.
 *
 * @author George Kankava
 * @since 2.4.3
 */
public abstract class AbstractSagaRepositoryBeanDefinitionParser extends AbstractBeanDefinitionParser {

    protected static final String RESOURCE_INJECTOR_ATTRIBUTE = "resource-injector";
    protected static final String ATTRIBUTE_SAGA_CACHE = "saga-cache";
    protected static final String ATTRIBUTE_ASSOCIATIONS_CACHE = "associations-cache";
    protected static final String ELEMENT_CACHE_CONFIG = "cache-config";

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
