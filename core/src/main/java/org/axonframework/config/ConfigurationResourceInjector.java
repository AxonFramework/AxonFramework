package org.axonframework.config;

import org.axonframework.eventhandling.saga.AbstractResourceInjector;

import java.util.Optional;

/**
 * ResourceInjector implementation that injects resources defined in the Axon Configuration.
 */
public class ConfigurationResourceInjector extends AbstractResourceInjector {

    private final Configuration configuration;

    /**
     * Initializes the ResourceInjector to inject the resources found in the given {@code configuration}.
     *
     * @param configuration the Configuration to find injectable resources in
     */
    public ConfigurationResourceInjector(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected <R> Optional<R> findResource(Class<R> requiredType) {
        return Optional.ofNullable(configuration.getComponent(requiredType));
    }
}
