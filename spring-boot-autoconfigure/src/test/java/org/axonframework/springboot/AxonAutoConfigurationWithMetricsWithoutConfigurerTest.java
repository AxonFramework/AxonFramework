/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.config.Configurer;
import org.axonframework.config.MessageMonitorFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.metrics.GlobalMetricRegistry;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.springboot.autoconfig.MicrometerMetricsAutoConfiguration;
import org.axonframework.springboot.utils.GrpcServerStub;
import org.axonframework.springboot.utils.TcpUtils;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class, WebClientAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class, MicrometerMetricsAutoConfiguration.class
})
@TestPropertySource("classpath:test.metrics.application.properties")
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AxonAutoConfigurationWithMetricsWithoutConfigurerTest {

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

    @Test
    void axonServerEventStoreRequestedMonitor() {
        assertNotNull(applicationContext.getBean(AxonServerEventStore.class));

        MessageMonitorFactory monitor = applicationContext.getBean("mockMessageMonitorFactory", MessageMonitorFactory.class);

        verify(monitor, description("expected MessageMonitorFactory to be retrieved for AxonServerEventStore"))
                .create(any(), eq(AxonServerEventStore.class), anyString());
    }

    @Configuration
    public static class Context {

        @Autowired
        public void configure(Configurer configurer) {
            configurer.configureMessageMonitor(EventBus.class, mockMessageMonitorFactory());
        }

        @Bean
        public MessageMonitorFactory mockMessageMonitorFactory() {
            MessageMonitorFactory mock = mock(MessageMonitorFactory.class);
            when(mock.create(any(), any(), any())).thenReturn(NoOpMessageMonitor.instance());
            return mock;
        }

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }
}
