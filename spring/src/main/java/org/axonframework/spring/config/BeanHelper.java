package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.modelling.command.Repository;

/**
 * Helper class to simplify creation of bean definitions for components configured in Axon Configuration
 */
public abstract class BeanHelper {

    private BeanHelper() {
    }

    /**
     * Retrieves the repository for given {@code aggregateType} from given {@code configuration}
     *
     * @param aggregateType The type to find the repository for
     * @param configuration The configuration from which to retrieve the Repository
     * @param <T>           The type of aggregate
     *
     * @return the Repository instance for the aggregate
     * @throws IllegalArgumentException if the given aggregateType has not been configured
     */
    public static <T> Repository<T> repository(Class<T> aggregateType, Configuration configuration) {
        return configuration.repository(aggregateType);
    }
}
