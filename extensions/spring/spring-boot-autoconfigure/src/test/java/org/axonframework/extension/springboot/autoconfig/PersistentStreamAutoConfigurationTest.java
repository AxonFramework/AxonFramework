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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamScheduledExecutorBuilder;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.extension.springboot.util.GrpcServerStub;
import org.axonframework.extension.springboot.util.TcpUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link PersistentStreamAutoConfiguration}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class PersistentStreamAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class);
    }

    @BeforeAll
    static void beforeAll() {
        System.setProperty("axon.axonserver.servers", GrpcServerStub.DEFAULT_HOST + ":" + TcpUtils.findFreePort());
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("axon.axonserver.servers");
    }

    @Nested
    class DefaultBeans {

        @Test
        void persistentStreamScheduledExecutorBuilderBeanIsCreated() {
            testContext.run(context -> {
                assertThat(context).hasSingleBean(PersistentStreamScheduledExecutorBuilder.class);
            });
        }

        @Test
        void persistentStreamMessageSourceFactoryBeanIsCreated() {
            testContext.run(context -> {
                assertThat(context).hasSingleBean(PersistentStreamMessageSourceFactory.class);
            });
        }

        @Test
        void persistentStreamScheduledExecutorBuilderConstructsUniqueScheduledExecutorServices() {
            testContext.run(context -> {
                assertThat(context).hasSingleBean(PersistentStreamScheduledExecutorBuilder.class);
                PersistentStreamScheduledExecutorBuilder executorBuilder =
                        context.getBean(PersistentStreamScheduledExecutorBuilder.class);

                ScheduledExecutorService firstExecutor = executorBuilder.build(1, "stream-1");
                ScheduledExecutorService secondExecutor = executorBuilder.build(1, "stream-1");

                // Each call should create a new executor
                assertThat(firstExecutor).isNotSameAs(secondExecutor);

                firstExecutor.shutdown();
                secondExecutor.shutdown();
            });
        }
    }

    @Nested
    class DisabledWhenEventStoreDisabled {

        @Test
        void persistentStreamBeansNotCreatedWhenEventStoreDisabled() {
            testContext.withPropertyValues("axon.axonserver.event-store.enabled=false")
                       .run(context -> {
                           assertThat(context).doesNotHaveBean(PersistentStreamScheduledExecutorBuilder.class);
                           assertThat(context).doesNotHaveBean(PersistentStreamMessageSourceFactory.class);
                       });
        }
    }

    @Nested
    class BackwardsCompatibility {

        @Test
        void persistentStreamScheduledExecutorBuilderReusesQualifiedSchedulerBean() {
            testContext.withUserConfiguration(SingleSchedulerConfiguration.class)
                       .run(context -> {
                           assertThat(context).hasSingleBean(PersistentStreamScheduledExecutorBuilder.class);
                           PersistentStreamScheduledExecutorBuilder executorBuilder =
                                   context.getBean(PersistentStreamScheduledExecutorBuilder.class);

                           ScheduledExecutorService qualifiedScheduler =
                                   context.getBean("persistentStreamScheduler", ScheduledExecutorService.class);

                           // When using the qualified bean, the builder should always return the same scheduler
                           ScheduledExecutorService firstExecutor = executorBuilder.build(1, "stream-1");
                           ScheduledExecutorService secondExecutor = executorBuilder.build(1, "stream-2");

                           assertThat(firstExecutor).isSameAs(qualifiedScheduler);
                           assertThat(secondExecutor).isSameAs(qualifiedScheduler);
                       });
        }
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }

    @Configuration
    static class SingleSchedulerConfiguration {

        @Bean
        @Qualifier("persistentStreamScheduler")
        public ScheduledExecutorService persistentStreamScheduler() {
            return Executors.newScheduledThreadPool(1, new AxonThreadFactory("persistent-streams"));
        }
    }
}
