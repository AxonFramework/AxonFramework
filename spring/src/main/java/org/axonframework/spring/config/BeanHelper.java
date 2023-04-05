package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.modelling.command.Repository;

/**
 * Helper class to simplify creation of bean definitions for components configured in Axon Configuration
 */
public abstract class BeanHelper {

    private BeanHelper() {
    }

    public static <T> Repository<T> repository(Class<T> aggregateType, Configuration configuration) {
        return configuration.repository(aggregateType);
    }
}
