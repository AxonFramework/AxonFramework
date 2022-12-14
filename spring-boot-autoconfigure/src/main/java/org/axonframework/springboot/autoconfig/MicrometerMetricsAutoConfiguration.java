/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.springboot.autoconfig;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.micrometer.GlobalMetricRegistry;
import org.axonframework.micrometer.MetricsConfigurerModule;
import org.axonframework.springboot.MetricsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto configuration to set up Micrometer Metrics for the infrastructure components.
 *
 * @author Steven van Beelen
 * @author Marijn van Zelst
 * @since 4.1
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration"
})
@AutoConfigureBefore({AxonAutoConfiguration.class, MetricsAutoConfiguration.class})
@ConditionalOnClass(name = {
        "io.micrometer.core.instrument.MeterRegistry",
        "org.axonframework.micrometer.GlobalMetricRegistry"
})
@EnableConfigurationProperties(MetricsProperties.class)
public class MicrometerMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public static MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(GlobalMetricRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    public static GlobalMetricRegistry globalMetricRegistry(MeterRegistry meterRegistry) {
        return new GlobalMetricRegistry(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(MetricsConfigurerModule.class)
    @ConditionalOnBean(GlobalMetricRegistry.class)
    @ConditionalOnProperty(value = "axon.metrics.auto-configuration.enabled", matchIfMissing = true)
    public static MetricsConfigurerModule metricsConfigurerModule(GlobalMetricRegistry globalMetricRegistry,
                                                                  MetricsProperties metricsProperties) {
        return new MetricsConfigurerModule(globalMetricRegistry, metricsProperties.getMicrometer().isDimensional());
    }
}

