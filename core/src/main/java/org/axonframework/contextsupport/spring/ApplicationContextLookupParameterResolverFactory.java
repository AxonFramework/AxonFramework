package org.axonframework.contextsupport.spring;

import org.axonframework.common.annotation.MultiParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.common.annotation.PriorityAnnotationComparator;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FactoryBean implementation that create a ParameterResolverFactory, which autodetects beans implementing
 * ParameterResolverFactory beans in the application context.
 *
 * @author Allard Buijze
 * @since 2.1.1
 */
public class ApplicationContextLookupParameterResolverFactory implements FactoryBean<ParameterResolverFactory>,
        ApplicationContextAware, InitializingBean {

    private final List<ParameterResolverFactory> factories;
    private volatile ParameterResolverFactory parameterResolverFactory;
    private ApplicationContext applicationContext;

    /**
     * Creates an instance, using the given <code>defaultFactories</code>. These are added, regardless of the beans
     * discovered in the application context.
     *
     * @param defaultFactories The ParameterResolverFactory instances to add by default
     */
    public ApplicationContextLookupParameterResolverFactory(List<ParameterResolverFactory> defaultFactories) {
        this.factories = new ArrayList<ParameterResolverFactory>(defaultFactories);
    }

    @Override
    public ParameterResolverFactory getObject() throws Exception {
        return parameterResolverFactory;
    }

    @Override
    public Class<?> getObjectType() {
        return MultiParameterResolverFactory.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, ParameterResolverFactory> factoriesFromContext = applicationContext.getBeansOfType(
                ParameterResolverFactory.class);
        factories.addAll(factoriesFromContext.values());

        Collections.sort(factories, PriorityAnnotationComparator.getInstance());

        parameterResolverFactory = new MultiParameterResolverFactory(factories);
    }
}
