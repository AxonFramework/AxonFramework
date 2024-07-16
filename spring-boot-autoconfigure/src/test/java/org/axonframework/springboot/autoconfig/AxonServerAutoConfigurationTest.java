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

package org.axonframework.springboot.autoconfig;

import io.grpc.ManagedChannelBuilder;
import org.axonframework.axonserver.connector.ManagedChannelCustomizer;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventScheduler;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStoreFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamSequencingPolicyProvider;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.disruptor.commandhandling.DisruptorCommandBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.axonframework.springboot.utils.GrpcServerStub;
import org.axonframework.springboot.utils.TcpUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AxonServerAutoConfigurationTest {

    private static final TargetContextResolver<Message<?>> CUSTOM_TARGET_CONTEXT_RESOLVER =
            m -> "some-custom-context-resolution";
    private static final ManagedChannelCustomizer CUSTOM_MANAGED_CHANNEL_CUSTOMIZER =
            ManagedChannelBuilder::directExecutor;
    public static final Class<SequentialPerAggregatePolicy> DEFAULT_SEQUENCING_POLICY_CLASS = SequentialPerAggregatePolicy.class;

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class);
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
    void axonServerQueryBusConfiguration() {
        testContext.run(context -> {
            QueryBus queryBus = context.getBean(QueryBus.class);
            assertThat(queryBus).isNotNull();
            assertThat(queryBus).isInstanceOf(AxonServerQueryBus.class);

            QueryUpdateEmitter queryUpdateEmitter = context.getBean(QueryUpdateEmitter.class);
            assertThat(queryUpdateEmitter).isNotNull();
            assertThat(queryUpdateEmitter).isEqualTo(queryBus.queryUpdateEmitter());
        });
    }

    @Test
    void axonServerCommandBusBeanTypesConfiguration() {
        testContext.run(context -> {
            Map<String, CommandBus> commandBusses = context.getBeansOfType(CommandBus.class);

            assertThat(commandBusses).containsKey("axonServerCommandBus");
            assertThat(commandBusses.get("axonServerCommandBus")).isInstanceOf(AxonServerCommandBus.class);

            assertThat(commandBusses).containsKey("commandBus");
            assertThat(commandBusses.get("commandBus")).isInstanceOf(SimpleCommandBus.class);
        });
    }

    @Test
    void springResourceInjectorConfigured() {
        testContext.run(context -> {
            ResourceInjector resourceInjector = context.getBean(ResourceInjector.class);
            assertThat(resourceInjector).isNotNull();
            assertThat(resourceInjector).isInstanceOf(SpringResourceInjector.class);
        });
    }

    @Test
    void axonServerDefaultCommandBusConfiguration() {
        testContext.withConfiguration(AutoConfigurations.of(AxonServerBusAutoConfiguration.class))
                   .withConfiguration(AutoConfigurations.of(AxonServerAutoConfiguration.class))
                   .run(context -> {
                       assertThat(context).getBeanNames(CommandBus.class)
                                          .hasSize(2);
                       assertThat(context).getBean("axonServerCommandBus")
                                          .isExactlyInstanceOf(AxonServerCommandBus.class);
                       assertThat(context).getBean("commandBus")
                                          .isExactlyInstanceOf(SimpleCommandBus.class);
                   });
    }

    @Test
    void axonServerDefaultConfiguration_AxonServerDisabled() {
        testContext.withPropertyValues("axon.axonserver.enabled=false")
                   .run(context -> {
                       assertThat(context).getBeanNames(CommandBus.class)
                                          .hasSize(1);
                       assertThat(context).doesNotHaveBean("axonServerCommandBus");
                       assertThat(context).getBean("commandBus")
                                          .isExactlyInstanceOf(SimpleCommandBus.class);
                       assertThat(context).hasSingleBean(EventBus.class);
                   });
    }

    @Test
    void axonServerDefaultConfiguration_AxonServerEventStoreDisabled() {
        testContext.withPropertyValues("axon.axonserver.event-store.enabled=false")
                   .run(context -> {
                       QueryBus queryBus = context.getBean(QueryBus.class);
                       assertThat(queryBus).isNotNull();
                       assertThat(queryBus).isInstanceOf(AxonServerQueryBus.class);
                       assertThat(context).getBean("axonServerCommandBus")
                                          .isExactlyInstanceOf(AxonServerCommandBus.class);
                       assertThat(context).doesNotHaveBean("eventScheduler");
                       assertThat(context).doesNotHaveBean("eventStore");
                   });
    }

    @Test
    void axonServerUserDefinedCommandBusConfiguration() {
        testContext.withUserConfiguration(ExplicitUserCommandBusConfiguration.class)
                   .run(context -> {
                       assertThat(context).getBeanNames(CommandBus.class)
                                          .hasSize(1);
                       assertThat(context).getBean(CommandBus.class)
                                          .isExactlyInstanceOf(DisruptorCommandBus.class);
                   });
    }

    @Test
    void axonServerUserDefinedLocalSegmentConfiguration() {
        testContext.withUserConfiguration(ExplicitUserLocalSegmentConfiguration.class)
                   .run(context -> {
                       assertThat(context).getBeanNames(CommandBus.class)
                                          .hasSize(2);
                       assertThat(context).getBean("axonServerCommandBus")
                                          .isExactlyInstanceOf(AxonServerCommandBus.class);
                       assertThat(context).getBean("commandBus")
                                          .isExactlyInstanceOf(DisruptorCommandBus.class);
                   });
    }

    @Test
    void axonServerWrongUserDefinedLocalSegmentConfiguration() {
        testContext.withUserConfiguration(
                           ExplicitWrongUserLocalSegmentConfiguration.class
                   )
                   .run(context -> {
                       assertThat(context).getBeanNames(CommandBus.class)
                                          .hasSize(1);
                       assertThat(context).getBean(CommandBus.class)
                                          .isExactlyInstanceOf(DisruptorCommandBus.class);
                   });
    }

    @Test
    void nonAxonServerCommandBusConfiguration() {
        testContext.withPropertyValues("axon.axonserver.enabled=false")
                   .run(context -> {
                       assertThat(context).getBeanNames(CommandBus.class)
                                          .hasSize(1);
                       assertThat(context).getBean(CommandBus.class)
                                          .isExactlyInstanceOf(SimpleCommandBus.class);
                   });
    }

    @Test
    void defaultTargetContextResolverIsNoOp() {
        testContext.run(context -> {
            assertThat(context).getBeanNames(TargetContextResolver.class)
                               .hasSize(1);
            assertThat(context).getBean(TargetContextResolver.class)
                               .isEqualTo(TargetContextResolver.noOp());
        });
    }

    @Test
    void customTargetContextResolverIsConfigured() {
        testContext.withUserConfiguration(TargetContextResolverConfiguration.class)
                   .run(context -> {
                       assertThat(context).getBeanNames(TargetContextResolver.class)
                                          .hasSize(1);
                       assertThat(context).getBean(TargetContextResolver.class)
                                          .isEqualTo(CUSTOM_TARGET_CONTEXT_RESOLVER);
                   });
    }

    @Test
    void axonServerEventSchedulerIsConfigured() {
        testContext.run(context -> {
            assertThat(context).getBeanNames(EventScheduler.class)
                               .hasSize(1);
            assertThat(context).getBean(EventScheduler.class)
                               .isExactlyInstanceOf(AxonServerEventScheduler.class);
        });
    }

    @Test
    void customManagedChannelCustomizerIsConfigured() {
        testContext.withUserConfiguration(ManagedChannelCustomizerConfiguration.class)
                   .run(context -> {
                       assertThat(context).getBeanNames(ManagedChannelCustomizer.class)
                                          .hasSize(1);
                       assertThat(context).getBean(ManagedChannelCustomizer.class)
                                          .isEqualTo(CUSTOM_MANAGED_CHANNEL_CUSTOMIZER);
                   });
    }

    @Test
    void axonServerEventStoreFactoryBeanIsConfigured() {
        testContext.withUserConfiguration(TestContext.class)
                   .run(context -> assertThat(context).getBeanNames(AxonServerEventStoreFactory.class).hasSize(1));
    }

    @Test
    void axonServerEventStoreFactoryBeanIsNotConfiguredWhenEventStoreIsDisabled() {
        testContext.withUserConfiguration(TestContext.class)
                .withPropertyValues("axon.axonserver.event-store.enabled=false")
                   .run(context -> assertThat(context).getBean(AxonServerEventStoreFactory.class).isNull());
    }

    @Test
    void axonServerPersistentStreamBeansCreated() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments].name=My Payments",
                                       "axon.axonserver.persistent-streams[orders].name=My Orders")
                .run(context -> {
                    assertThat(context).getBean("payments").isNotNull();
                    assertThat(context).getBean("orders").isNotNull();
                });
    }

    @Test
    void persistentStreamProcessorsConfigurerModuleAddsSequencingPolicy() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.eventhandling.processors.payments.source=payments-stream",
                                       "axon.eventhandling.processors.payments.dlq.enabled=true",
                                       "axon.eventhandling.processors.payments.mode=SUBSCRIBING")
                   .run(context -> {
                       assertThat(context).getBean("persistentStreamProcessorsConfigurerModule").isNotNull();
                       ConfigurerModule configurerModule = context.getBean("persistentStreamProcessorsConfigurerModule", ConfigurerModule.class);
                       Configurer defaultConfigurer = DefaultConfigurer.defaultConfiguration();
                       configurerModule.configureModule(defaultConfigurer);
                       Configuration configuration = defaultConfigurer.buildConfiguration();
                       SequencingPolicy<? super EventMessage<?>> sequencingPolicy = configuration.eventProcessingConfiguration()
                                                                                         .sequencingPolicy("payments");
                       assertThat(sequencingPolicy).isNotNull();
                       assertThat(sequencingPolicy).isNotInstanceOf(DEFAULT_SEQUENCING_POLICY_CLASS);
                   });
    }
    @Test
    void persistentStreamProcessorsConfigurerModuleAddsNoSequencingPolicyWithoutDlq() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.eventhandling.processors.payments.source=payments-stream",
                                       "axon.eventhandling.processors.payments.mode=SUBSCRIBING")
                   .run(context -> {
                       assertThat(context).getBean("persistentStreamProcessorsConfigurerModule").isNotNull();
                       ConfigurerModule configurerModule = context.getBean("persistentStreamProcessorsConfigurerModule", ConfigurerModule.class);
                       Configurer defaultConfigurer = DefaultConfigurer.defaultConfiguration();
                       configurerModule.configureModule(defaultConfigurer);
                       Configuration configuration = defaultConfigurer.buildConfiguration();
                       assertThat(configuration.eventProcessingConfiguration().sequencingPolicy("payments")).isInstanceOf(
                               DEFAULT_SEQUENCING_POLICY_CLASS);
                   });
    }

    @Test
    void axonServerPersistentStreamBeansNotCreatedWhenAxonServerDisabled() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments].name=My Payments",
                                       "axon.axonserver.enabled=false")
                   .run(context -> assertThat(context).getBean("payments").isNull());
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class TestContext {

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }

    private static class ExplicitUserCommandBusConfiguration {

        @Bean
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }
    }

    private static class ExplicitUserLocalSegmentConfiguration {

        @Bean
        @Qualifier("localSegment")
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }
    }

    private static class ExplicitWrongUserLocalSegmentConfiguration {

        @Bean
        @Qualifier("wrongSegment")
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }
    }

    private static class TargetContextResolverConfiguration {

        @Bean
        public TargetContextResolver<Message<?>> customTargetContextResolver() {
            return CUSTOM_TARGET_CONTEXT_RESOLVER;
        }
    }

    private static class ManagedChannelCustomizerConfiguration {

        @Bean
        public ManagedChannelCustomizer customManagedChannelCustomizer() {
            return CUSTOM_MANAGED_CHANNEL_CUSTOMIZER;
        }
    }
}
