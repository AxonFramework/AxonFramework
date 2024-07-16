package org.axonframework.springboot.autoconfig;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource;
import org.axonframework.config.Configuration;

/**
 * Functional interface for creating instances of {@link PersistentStreamMessageSource}.
 * This factory is used to construct message sources for persistent streams with specific configurations.
 */
@FunctionalInterface
public interface PersistentStreamMessageSourceFactory {

    /**
     * Builds a {@link PersistentStreamMessageSource} with the specified parameters.
     *
     * @param name           The name of the persistent stream message source.
     * @param settings       The settings for the persistent stream processor.
     * @param configuration  The Axon Framework configuration.
     * @return A new instance of {@link PersistentStreamMessageSource} configured with the provided parameters.
     */
    PersistentStreamMessageSource build(
            String name,
            AxonServerConfiguration.PersistentStreamProcessorSettings settings,
            Configuration configuration);
}