package org.axonframework.boot.autoconfig;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.config.Configurer;
import org.axonframework.metrics.GlobalMetricRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration to set up Metrics for the infrastructure components.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MetricRegistry.class)
    public GlobalMetricRegistry globalMetricRegistry(MetricRegistry metricRegistry) {
        return new GlobalMetricRegistry(metricRegistry);
    }

    @Autowired
    public void configureMetrics(GlobalMetricRegistry globalMetricRegistry, Configurer configurer) {
        globalMetricRegistry.registerWithConfigurer(configurer);
    }
}
