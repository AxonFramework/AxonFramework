package org.axonframework.spring.config.annotation;

import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;

import java.util.List;

/**
 * Spring Factory Bean that creates a {@link HandlerDefinition} using configured {@link HandlerDefinition} and
 * {@link HandlerEnhancerDefinition) beans (e.g. those configured in a Spring Application Context) and complements
 * those found using a service loader on the Bean Class Loader.
 * <p>
 * This bean is to be use from a Configuration file with autowires the other {@link HandlerDefinition} and
 * {@link HandlerEnhancerDefinition} beans from the application context.
 */
public class HandlerDefinitionFactoryBean implements FactoryBean<HandlerDefinition>, BeanClassLoaderAware {

    private final List<HandlerDefinition> definitions;
    private final List<HandlerEnhancerDefinition> enhancerDefinitions;
    private ClassLoader beanClassLoader;

    public HandlerDefinitionFactoryBean(List<HandlerDefinition> definitions,
                                        List<HandlerEnhancerDefinition> enhancerDefinitions) {
        this.definitions = definitions;
        this.enhancerDefinitions = enhancerDefinitions;
    }

    @Override
    public HandlerDefinition getObject() {
        return MultiHandlerDefinition.ordered(resolveEnhancers(), resolveDefinitions());
    }

    private MultiHandlerDefinition resolveDefinitions() {
        return MultiHandlerDefinition.ordered(ClasspathHandlerDefinition.forClassLoader(beanClassLoader),
                                              MultiHandlerDefinition.ordered(definitions));
    }

    private MultiHandlerEnhancerDefinition resolveEnhancers() {
        return MultiHandlerEnhancerDefinition.ordered(ClasspathHandlerEnhancerDefinition.forClassLoader(beanClassLoader),
                                                      MultiHandlerEnhancerDefinition.ordered(enhancerDefinitions));
    }

    @Override
    public Class<?> getObjectType() {
        return HandlerDefinition.class;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }
}
