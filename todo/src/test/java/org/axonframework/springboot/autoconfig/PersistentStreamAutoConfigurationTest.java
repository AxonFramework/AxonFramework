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

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamScheduledExecutorBuilder;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.springboot.util.GrpcServerStub;
import org.axonframework.springboot.util.TcpUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("TODO #3496")
class PersistentStreamAutoConfigurationTest {

    public static final Class<SequentialPerAggregatePolicy> DEFAULT_SEQUENCING_POLICY_CLASS =
            SequentialPerAggregatePolicy.class;

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
    void axonServerPersistentStreamBeansCreated() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments].name=My Payments",
                                       "axon.axonserver.persistent-streams[orders].name=My Orders")
                   .run(context -> {
                       assertThat(context).getBean("payments").isNotNull();
                       assertThat(context).getBean("orders").isNotNull();
                   });
    }

    @Test
    void defaultEventProcessorBuilderShouldBeConfiguredForPresistentStreams() {
        testContext.withPropertyValues("axon.axonserver.auto-persistent-streams-enable=true",
                                       "axon.axonserver.auto-persistent-streams-settings.initial-segment-count=10",
                                       "axon.axonserver.auto-persistent-streams-settings.batch-size=6")
                   .run(context -> {

//                       EventProcessingModule eventProcessingModule = context.getBean(EventProcessingModule.class);

//                       EventProcessingConfigurer.EventProcessorBuilder defaultEventProcessorBuilder = getField(
//                               "defaultEventProcessorBuilder",
//                               eventProcessingModule);

//                       LegacyConfiguration config = getField("configuration", eventProcessingModule);
//                       Object processor = defaultEventProcessorBuilder.build("processingGroupName",
//                                                                             config,
//                                                                             new MultiEventHandlerInvoker(
//                                                                                     Collections.emptyList()));

//                       Object messageSource = getField("messageSource", processor);
//                       Object connection = getField("persistentStreamConnection", messageSource);
//                       PersistentStreamProperties properties = getField("persistentStreamProperties", connection);
//                       Integer batchSize = getField("batchSize", connection);

//                       assertThat(messageSource).isInstanceOf(PersistentStreamMessageSource.class);
//                       assertThat(properties.segments()).isEqualTo(10);
//                       assertThat(properties.streamName()).isEqualTo("processingGroupName-stream");
//                       assertThat(batchSize).isEqualTo(6);
                   });
    }

    @Test
    void persistentStreamSettingsArePopulatedAsExpected() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.axonserver.persistent-streams[payments-stream].thread-count=4")
                   .run(context -> {
                       assertThat(context).hasSingleBean(AxonServerConfiguration.class);
//                       Map<String, AxonServerConfiguration.PersistentStreamSettings> persistentStreams =
//                               context.getBean(AxonServerConfiguration.class).getPersistentStreams();

//                       assertThat(persistentStreams).hasSize(1);
//                       AxonServerConfiguration.PersistentStreamSettings paymentsStreamSettings =
//                               persistentStreams.get("payments-stream");
//                       assertThat(paymentsStreamSettings).isNotNull();
//                       assertThat(paymentsStreamSettings.getThreadCount()).isEqualTo(4);
                   });
    }

    @Test
    void persistentStreamProcessorsConfigurerModuleAddsSequencingPolicy() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.eventhandling.processing.payments.source=payments-stream",
                                       "axon.eventhandling.processing.payments.dlq.enabled=true",
                                       "axon.eventhandling.processing.payments.mode=SUBSCRIBING")
                   .run(context -> {
//                       assertThat(context).getBean("persistentStreamProcessorsConfigurerModule").isNotNull();
//                       ConfigurerModule configurerModule =
//                               context.getBean("persistentStreamProcessorsConfigurerModule", ConfigurerModule.class);
//                       LegacyConfigurer defaultConfigurer = LegacyDefaultConfigurer.defaultConfiguration();
//                       configurerModule.configureModule(defaultConfigurer);
//                       LegacyConfiguration configuration = defaultConfigurer.buildConfiguration();
//                       SequencingPolicy sequencingPolicy =
//                               configuration.eventProcessingConfiguration().sequencingPolicy("payments");
//                       assertThat(sequencingPolicy).isNotNull();
//                       assertThat(sequencingPolicy).isNotInstanceOf(DEFAULT_SEQUENCING_POLICY_CLASS);
                   });
    }

    @Test
    void persistentStreamProcessorsConfigurerModuleAddsNoSequencingPolicyWithoutDlq() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.eventhandling.processing.payments.source=payments-stream",
                                       "axon.eventhandling.processing.payments.mode=SUBSCRIBING")
                   .run(context -> {
//                       assertThat(context).getBean("persistentStreamProcessorsConfigurerModule").isNotNull();
//                       ConfigurerModule configurerModule =
//                               context.getBean("persistentStreamProcessorsConfigurerModule", ConfigurerModule.class);
//                       LegacyConfigurer defaultConfigurer = LegacyDefaultConfigurer.defaultConfiguration();
//                       configurerModule.configureModule(defaultConfigurer);
//                       LegacyConfiguration configuration = defaultConfigurer.buildConfiguration();
//                       assertThat(
//                               configuration.eventProcessingConfiguration().sequencingPolicy("payments")
//                       ).isInstanceOf(DEFAULT_SEQUENCING_POLICY_CLASS);
                   });
    }

    @Test
    void axonServerPersistentStreamBeansNotCreatedWhenAxonServerDisabled() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments].name=My Payments",
                                       "axon.axonserver.enabled=false")
                   .run(context -> assertThat(context).getBean("payments").isNull());
    }

    @Test
    void persistentStreamScheduledExecutorBuilderConstructsUniqueScheduledExecutorServices() {
        testContext.withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.eventhandling.processing.payments.source=payments-stream")
                   .run(context -> {
                       assertThat(context).hasSingleBean(PersistentStreamScheduledExecutorBuilder.class);
                       PersistentStreamScheduledExecutorBuilder executorBuilder =
                               context.getBean(PersistentStreamScheduledExecutorBuilder.class);

                       ScheduledExecutorService fooExecutor = executorBuilder.build(1, "foo");
                       assertThat(fooExecutor).isNotEqualTo(executorBuilder.build(1, "foo"));
                   });
    }

    @Test
    void persistentStreamScheduledExecutorBuilderReusesScheduledExecutorService() {
        testContext.withUserConfiguration(SinglePersistentStreamScheduledExecutorServiceConfiguration.class)
                   .withPropertyValues("axon.axonserver.persistent-streams[payments-stream].name=My Payments",
                                       "axon.eventhandling.processing.payments.source=payments-stream")
                   .run(context -> {
                       assertThat(context).hasSingleBean(PersistentStreamScheduledExecutorBuilder.class);
                       PersistentStreamScheduledExecutorBuilder executorBuilder =
                               context.getBean(PersistentStreamScheduledExecutorBuilder.class);

                       ScheduledExecutorService fooExecutor = executorBuilder.build(1, "foo");
                       assertThat(fooExecutor).isEqualTo(executorBuilder.build(1, "foo"));
                   });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class TestContext {

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }

    private static class SinglePersistentStreamScheduledExecutorServiceConfiguration {

        @Bean
        @Qualifier("persistentStreamScheduler")
        public ScheduledExecutorService persistentStreamScheduler() {
            return Executors.newScheduledThreadPool(1, new AxonThreadFactory("persistent-streams"));
        }
    }

    private <O, R> R getField(String fieldName, O object) throws NoSuchFieldException, IllegalAccessException {
        return getField(object.getClass(), fieldName, object);
    }

    private <C, O, R> R getField(Class<C> clazz,
                                 String fieldName,
                                 O object) throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        //noinspection unchecked
        return (R) field.get(object);
    }
}
