package org.axonframework.boot.autoconfig;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.metrics.GlobalMetricRegistry;
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
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MetricRegistry.class)
    public GlobalMetricRegistry globalMetricRegistry(MetricRegistry metricRegistry) {
        return new GlobalMetricRegistry(metricRegistry);
    }
}
