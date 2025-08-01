/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.springboot;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.axonframework.micrometer.GlobalMetricRegistry;
import org.axonframework.micrometer.MetricsConfigurerModule;
import org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        AxonServerAutoConfiguration.class,
        AxonServerBusAutoConfiguration.class,
        AxonServerActuatorAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@Disabled("TODO #3496")
class AxonAutoConfigurationWithMicrometerMetricsTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private GlobalMetricRegistry globalMetricRegistry;

    @Autowired
    private MetricsConfigurerModule metricsConfigurerModule;

    @Autowired(required = false)
    private MetricRegistry dropwizardMetricRegistry;

    @Autowired(required = false)
    private org.axonframework.metrics.GlobalMetricRegistry metricsModuleGlobalMetricRegistry;

    @Autowired(required = false)
    private org.axonframework.metrics.MetricsConfigurerModule metricsModuleMetricsConfigurerModule;

    @Test
    void contextInitialization() {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(MeterRegistry.class));
        assertNotNull(meterRegistry);

        assertTrue(applicationContext.containsBean("globalMetricRegistry"));
        assertNotNull(applicationContext.getBean(GlobalMetricRegistry.class));
        assertEquals(GlobalMetricRegistry.class, globalMetricRegistry.getClass());

        assertTrue(applicationContext.containsBean("metricsConfigurerModule"));
        assertNotNull(applicationContext.getBean(MetricsConfigurerModule.class));
        assertEquals(MetricsConfigurerModule.class, metricsConfigurerModule.getClass());

        assertNull(dropwizardMetricRegistry);
        assertNull(metricsModuleGlobalMetricRegistry);
        assertNull(metricsModuleMetricsConfigurerModule);
    }
}
