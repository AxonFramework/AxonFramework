/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.boot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.gateway.AbstractCommandGateway;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter;
import org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;


@ContextConfiguration(classes = AxonAutoConfigurationWithSpringCloudTest.TestContext.class)
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        AxonServerAutoConfiguration.class})
@TestPropertySource("classpath:test.springcloud.application.properties")
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationWithSpringCloudTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("localSegment")
    private CommandBus localSegment;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RoutingStrategy routingStrategy;

    @Autowired
    private CommandRouter commandRouter;
    @Autowired
    private CommandBusConnector commandBusConnector;

    @Test
    public void testContextInitialization() {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(RoutingStrategy.class));
        assertEquals(AnnotationRoutingStrategy.class, routingStrategy.getClass());

        assertNotNull(applicationContext.getBean(SpringCloudHttpBackupCommandRouter.class));
        assertEquals(SpringCloudHttpBackupCommandRouter.class, commandRouter.getClass());

        assertNotNull(applicationContext.getBean(SpringHttpCommandBusConnector.class));
        assertEquals(SpringHttpCommandBusConnector.class, commandBusConnector.getClass());

        assertNotNull(commandBus);
        assertEquals(DistributedCommandBus.class, commandBus.getClass());

        assertNotNull(localSegment);
        assertEquals(SimpleCommandBus.class, localSegment.getClass());

        assertNotSame(commandBus, localSegment);

        assertNotNull(applicationContext.getBean(EventBus.class));
        CommandGateway gateway = applicationContext.getBean(CommandGateway.class);
        assertTrue(gateway instanceof DefaultCommandGateway);
        assertSame(((AbstractCommandGateway) gateway).getCommandBus(), commandBus);
        assertNotNull(gateway);
        assertNotNull(applicationContext.getBean(Serializer.class));
    }

    @Configuration
    public static class TestContext {

        @Bean
        public RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        public DiscoveryClient discoveryClient() {
            return new SimpleDiscoveryClient(new SimpleDiscoveryProperties());
        }

        @Bean
        public Registration registration() {
            return new Registration() {
                @Override
                public String getServiceId() {
                    return "TestServiceId";
                }

                @Override
                public String getHost() {
                    return "localhost";
                }

                @Override
                public int getPort() {
                    return 12345;
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public URI getUri() {
                    return UriComponentsBuilder.fromUriString("localhost")
                                               .port(12345)
                                               .build()
                                               .toUri();
                }

                @Override
                public Map<String, String> getMetadata() {
                    return new HashMap<>();
                }
            };
        }
    }
}
