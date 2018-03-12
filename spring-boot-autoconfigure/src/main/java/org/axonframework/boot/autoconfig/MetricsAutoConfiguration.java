package org.axonframework.boot.autoconfig;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.metrics.GlobalMetricRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
@ConditionalOnClass(name = {
        "com.codahale.metrics.MetricRegistry",
        "org.axonframework.metrics.GlobalMetricRegistry"
})
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MetricRegistry.class)
    public GlobalMetricRegistry globalMetricRegistry(MetricRegistry metricRegistry) {
        return new GlobalMetricRegistry(metricRegistry);
    }
}

