/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.metrics.dropwizard.springboot;

import com.codahale.metrics.MetricRegistry;
import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extension.metrics.dropwizard.MetricsConfigurationEnhancer;
import org.axonframework.extension.springboot.autoconfig.AxonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration to set up metrics for the infrastructure components.
 *
 * @author Steven van Beelen
 * @since 3.2.0
 */
@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.axonframework.extension.springboot.autoconfig.AxonAutoConfiguration",
        "com.codahale.metrics.MetricRegistry"
})
@EnableConfigurationProperties(MetricsProperties.class)
public class DropwizardMetricsAutoConfiguration {

    /**
     * Bean creation method constructing a Dropwizard {@link MetricRegistry} for the
     * {@link MetricsConfigurationEnhancer}.
     *
     * @return a Dropwizard {@link MetricRegistry} to be used by the {@link MetricsConfigurationEnhancer}
     */
    @Bean
    @ConditionalOnMissingBean(MetricRegistry.class)
    @ConditionalOnProperty(value = "axon.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    /**
     * Bean creation method constructing a {@link MetricsConfigurationEnhancer} with the given {@code registry}, which
     * will attach a default set of {@link org.axonframework.messaging.monitoring.MessageMonitor MessageMonitors} to
     * Axon's infrastructure components.
     *
     * @param registry the {@code MetricRegistry} to be used by the {@link MetricsConfigurationEnhancer} to register
     *                 metrics with
     * @return a {@link MetricsConfigurationEnhancer} that will attach a default set of
     * {@link org.axonframework.messaging.monitoring.MessageMonitor MessageMonitors} to Axon's infrastructure
     * components
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "axon.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MetricsConfigurationEnhancer metricsConfigurationEnhancer(MetricRegistry registry) {
        return new MetricsConfigurationEnhancer(registry);
    }

    /**
     * Bean creation method constructing a {@link ConfigurationEnhancer} that disables
     * {@link MetricsConfigurationEnhancer} that is only constructed when {@code axon.metrics.enabled} is set to
     * {@code false}.
     *
     * @return a {@link ConfigurationEnhancer} {@link MetricsConfigurationEnhancer} that is only constructed when
     * {@code axon.metrics.enabled} is set to {@code false}
     */
    @Bean
    @ConditionalOnProperty(name = "axon.metrics.enabled", havingValue = "false")
    public ConfigurationEnhancer disableMetricsConfigurationEnhancer() {
        return new ConfigurationEnhancer() {
            @Override
            public void enhance(@Nonnull ComponentRegistry registry) {
                registry.disableEnhancer(MetricsConfigurationEnhancer.class);
            }

            @Override
            public int order() {
                return Integer.MIN_VALUE;
            }
        };
    }
}

