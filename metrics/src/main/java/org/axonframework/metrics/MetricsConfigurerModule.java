package org.axonframework.metrics;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;

/**
 * Implementation of the {@link ConfigurerModule} which uses the
 * {@link org.axonframework.metrics.GlobalMetricRegistry} to register several Metrics Modules to the given
 * {@link org.axonframework.config.Configurer}.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
public class MetricsConfigurerModule implements ConfigurerModule {

    private final GlobalMetricRegistry globalMetricRegistry;

    public MetricsConfigurerModule(GlobalMetricRegistry globalMetricRegistry) {
        this.globalMetricRegistry = globalMetricRegistry;
    }

    @Override
    public void configureModule(Configurer configurer) {
        globalMetricRegistry.registerWithConfigurer(configurer);
    }
}
