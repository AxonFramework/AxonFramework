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

package org.axonframework.springboot;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.extension.metrics.dropwizard.GlobalMetricRegistry;
import org.axonframework.springboot.autoconfig.MicrometerMetricsAutoConfiguration;
import org.axonframework.springboot.util.GrpcServerStub;
import org.axonframework.springboot.util.TcpUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class, WebClientAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class, MicrometerMetricsAutoConfiguration.class
})
@TestPropertySource("classpath:test.metrics.application.properties")
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@Disabled("TODO #3496")
class AxonAutoConfigurationWithMetricsWithoutConfigurerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MetricRegistry metricRegistry;
    @Autowired
    private GlobalMetricRegistry globalMetricRegistry;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("axon.axonserver.servers", GrpcServerStub.DEFAULT_HOST + ":" + TcpUtils.findFreePort());
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("axon.axonserver.servers");
    }

    @Test
    void contextInitialization() {
        assertNotNull(applicationContext);

        assertTrue(applicationContext.containsBean("metricRegistry"));
        assertNotNull(applicationContext.getBean(MetricRegistry.class));
        assertEquals(MetricRegistry.class, metricRegistry.getClass());

        assertTrue(applicationContext.containsBean("globalMetricRegistry"));
        assertNotNull(applicationContext.getBean(GlobalMetricRegistry.class));
        assertEquals(GlobalMetricRegistry.class, globalMetricRegistry.getClass());

        assertFalse(applicationContext.containsBean("metricsConfigurerModule"));
    }

    @Configuration
    public static class Context {

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }
}
