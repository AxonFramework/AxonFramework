package org.axonframework.config;

/**
 * Interface describing a configurer for a module in the Axon Configuration API. Allows the registration of modules on
 * the {@link org.axonframework.config.Configurer} like the {@link org.axonframework.monitoring.MessageMonitor}.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
public interface ConfigurerModule {

    /**
     * Configure this module to the given global {@link org.axonframework.config.Configurer}.
     *
     * @param configurer a {@link org.axonframework.config.Configurer} instance to configure this module with
     */
    void configureModule(Configurer configurer);
}
