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

package org.axonframework.springboot.autoconfig;

import io.axoniq.axonserver.connector.AxonServerConnection;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ManagedChannelCustomizer;
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngine;
import org.axonframework.springboot.utils.GrpcServerStub;
import org.axonframework.springboot.utils.TcpUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating that the {@link AxonServerConfigurationEnhancer}
 * are registered and customizable when using Spring Boot.
 *
 * @author Steven van Beelen
 */
class AxonServerAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class)
                .withPropertyValues("axon.axonserver.enabled=true");
    }

    @BeforeAll
    static void beforeAll() {
        System.setProperty("axon.axonserver.servers", GrpcServerStub.DEFAULT_HOST + ":" + TcpUtils.findFreePort());
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("axon.axonserver.servers");
    }

    @Test
    void defaultAxonServerComponentsArePresent() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(AxonServerConfiguration.class);
            assertThat(context).hasBean(AxonServerConfiguration.class.getName());
            assertThat(context).hasSingleBean(AxonServerConnectionManager.class);
            assertThat(context).hasBean(AxonServerConnectionManager.class.getName());
            assertThat(context).hasSingleBean(ManagedChannelCustomizer.class);
            assertThat(context).hasBean(ManagedChannelCustomizer.class.getName());
            assertThat(context).hasSingleBean(AxonServerEventStorageEngine.class);
            assertThat(context).hasBean(AxonServerEventStorageEngine.class.getName());
        });
    }

    @Test
    void overrideDefaultAxonServerComponents() {
        testContext.withUserConfiguration(CustomContext.class).run(context -> {
            assertThat(context).hasSingleBean(AxonServerConfiguration.class);
            assertThat(context).hasBean("customAxonServerConfiguration");
            assertThat(context).hasSingleBean(AxonServerConnectionManager.class);
            assertThat(context).hasBean("customAxonServerConnectionManager");
            assertThat(context).hasSingleBean(ManagedChannelCustomizer.class);
            assertThat(context).hasBean("customManagedChannelCustomizer");
            assertThat(context).hasSingleBean(AxonServerEventStorageEngine.class);
            assertThat(context).hasBean("customAxonServerEventStorageEngine");
        });
    }

    @Configuration
    @EnableAutoConfiguration
    public static class TestContext {

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class CustomContext {

        @Bean
        public AxonServerConfiguration customAxonServerConfiguration() {
            return mock(AxonServerConfiguration.class);
        }

        @Bean
        public AxonServerConnectionManager customAxonServerConnectionManager() {
            AxonServerConnectionManager mock = mock(AxonServerConnectionManager.class);
            AxonServerConnection connectionMock = mock();
            when(mock.getConnection()).thenReturn(connectionMock);
            return mock;
        }

        @Bean
        public ManagedChannelCustomizer customManagedChannelCustomizer() {
            return mock(ManagedChannelCustomizer.class);
        }

        @Bean
        public AxonServerEventStorageEngine customAxonServerEventStorageEngine() {
            return mock(AxonServerEventStorageEngine.class);
        }
    }
}
