/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot.autoconfig;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration to set up Dropwizard Metrics for the infrastructure components
 *
 * @author Marijn van Zelst
 * @since 4.0
 */
@Configuration
@AutoConfigureBefore({
        AxonAutoConfiguration.class,
        MetricsAutoConfiguration.class
})
@ConditionalOnClass(name = {
        "com.codahale.metrics.MetricRegistry",
        "io.micrometer.core.instrument.MeterRegistry",
        "org.axonframework.metrics.GlobalMetricRegistry"
})
public class DropwizardMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetricRegistry metricRegistry(){
        return new MetricRegistry();
    }

    @Bean
    @ConditionalOnBean(MetricRegistry.class)
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry(MetricRegistry metricRegistry) {
        DropwizardConfig dropwizardConfig = new DropwizardConfig() {
            @Override
            public String prefix() {
                return "axon-metrics";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
        return new DropwizardMeterRegistry(dropwizardConfig, metricRegistry, HierarchicalNameMapper.DEFAULT,
                                           Clock.SYSTEM) {
            @Override
            protected Double nullGaugeValue() {
                return null;
            }
        };
    }

}

