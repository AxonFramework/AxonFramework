/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.metrics.GlobalMetricRegistry;
import org.axonframework.metrics.MetricsModuleConfigurer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class, WebClientAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationWithMetricsTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MetricRegistry metricRegistry;
    @Autowired
    private GlobalMetricRegistry globalMetricRegistry;
    @Autowired
    private MetricsModuleConfigurer metricsModuleConfigurer;

    @Test
    public void testContextInitialization() {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(MetricRegistry.class));
        assertEquals(MetricRegistry.class, metricRegistry.getClass());

        assertNotNull(applicationContext.getBean(GlobalMetricRegistry.class));
        assertEquals(GlobalMetricRegistry.class, globalMetricRegistry.getClass());

        assertNotNull(applicationContext.getBean(MetricsModuleConfigurer.class));
        assertEquals(MetricsModuleConfigurer.class, metricsModuleConfigurer.getClass());
    }
}
