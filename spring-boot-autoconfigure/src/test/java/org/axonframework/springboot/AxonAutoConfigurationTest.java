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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.LegacyConfiguration;
import org.axonframework.config.LegacyConfigurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.axonframework.modelling.command.LegacyRepository;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.axonframework.tracing.SpanFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AxonAutoConfigurationTest {

    @Test
    void ambiguousComponentsThrowExceptionWhenRequestedFromConfiguration() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne", CommandGateway.class,
                          () -> new DefaultCommandGateway(new SimpleCommandBus(), new ClassBasedMessageTypeResolver()))
                .withBean("gatewayTwo", CommandGateway.class,
                          () -> new DefaultCommandGateway(new SimpleCommandBus(), new ClassBasedMessageTypeResolver()))
                .withPropertyValues("axon.axonserver.enabled=false");

        AxonConfigurationException actual = assertThrows(AxonConfigurationException.class, () -> {
            applicationContextRunner
                    .run(ctx -> ctx.getBean(LegacyConfiguration.class).commandGateway());
        });
        assertTrue(actual.getMessage().contains("CommandGateway"));
        assertTrue(actual.getMessage().contains("gatewayOne"));
        assertTrue(actual.getMessage().contains("gatewayTwo"));
    }

    @Test
    void ambiguousPrimaryComponentsThrowExceptionWhenRequestedFromConfiguration() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne",
                          CommandGateway.class,
                          () -> new DefaultCommandGateway(new SimpleCommandBus(), new ClassBasedMessageTypeResolver()),
                          beanDefinition -> beanDefinition.setPrimary(true))
                .withBean("gatewayTwo",
                          CommandGateway.class,
                          () -> new DefaultCommandGateway(new SimpleCommandBus(), new ClassBasedMessageTypeResolver()),
                          beanDefinition -> beanDefinition.setPrimary(true))
                .withPropertyValues("axon.axonserver.enabled=false");

        AxonConfigurationException actual = assertThrows(AxonConfigurationException.class, () -> {
            applicationContextRunner
                    .run(ctx -> ctx.getBean(LegacyConfiguration.class).commandGateway());
        });
        assertTrue(actual.getMessage().contains("CommandGateway"));
        assertTrue(actual.getMessage().contains("gatewayOne"));
        assertTrue(actual.getMessage().contains("gatewayTwo"));
    }

    @Test
    void ambiguousBeanDependenciesThrowException() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne", CommandBus.class, SimpleCommandBus::new)
                .withBean("gatewayTwo", CommandBus.class, SimpleCommandBus::new)
                .withPropertyValues("axon.axonserver.enabled=false")
                .run(ctx -> {
                    Throwable startupFailure = ctx.getStartupFailure();
                    assertTrue(startupFailure instanceof UnsatisfiedDependencyException);
                    assertTrue(startupFailure.getMessage().contains("CommandBus"),
                               "Expected mention of 'CommandBus' in: " + startupFailure.getMessage());
                    assertTrue(startupFailure.getMessage().contains("gatewayOne"),
                               "Expected mention of 'gatewayOne' in: " + startupFailure.getMessage());
                    assertTrue(startupFailure.getMessage().contains("gatewayTwo"),
                               "Expected mention of 'gatewayTwo' in: " + startupFailure.getMessage());
                });
    }

    private static EventMessage<Object> asEventMessage(Object payload) {
        return new GenericEventMessage<>(new MessageType("event"), payload);
    }

    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    @Configuration
    public static class Context {

        @Autowired
        public void configure(EventProcessingConfigurer eventProcessingConfigurer) {
            eventProcessingConfigurer.usingSubscribingEventProcessors();
        }

        @Bean
        public SnapshotTriggerDefinition snapshotTriggerDefinition(Snapshotter snapshotter) {
            return new EventCountSnapshotTriggerDefinition(snapshotter, 2);
        }

        @Bean
        public ParameterResolverFactory customerParameterResolverFactory() {
            return new SimpleResourceParameterResolverFactory(singleton(new CustomResource()));
        }

        @Bean
        public LegacyEventStore eventStore() {
            return spy(LegacyEmbeddedEventStore.builder().storageEngine(storageEngine()).build());
        }

        @Bean
        public LegacyEventStorageEngine storageEngine() {
            return new LegacyInMemoryEventStorageEngine();
        }

        @Bean
        public CorrelationDataProvider correlationData1() {
            return new SimpleCorrelationDataProvider("key1");
        }

        @Bean
        public CorrelationDataProvider correlationData2() {
            return new SimpleCorrelationDataProvider("key2");
        }

        @Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition")
        public static class MyAggregate {

            @CommandHandler
            public void handle(String type, SomeComponent test, CustomResource resource) {

            }

            @EventHandler
            public void on(String type, SomeComponent test) {

            }
        }

        @Saga
        public static class MySaga {

            @SagaEventHandler(associationProperty = "toString")
            public void handle(String type, SomeComponent test) {

            }
        }

        @Saga
        public static class MyDefaultConfigSaga {

            @SagaEventHandler(associationProperty = "toString")
            public void handle(String type, SomeComponent test) {

            }
        }

        @Component
        public static class CustomParameterResolverFactory implements ParameterResolverFactory {

            private final EventBus eventBus;

            public CustomParameterResolverFactory(EventBus eventBus) {
                this.eventBus = eventBus;
            }

            @Nullable
            @Override
            public ParameterResolver createInstance(@Nonnull Executable executable, @Nonnull Parameter[] parameters, int parameterIndex) {
                if (Integer.class.isAssignableFrom(parameters[parameterIndex].getType())) {
                    return new FixedValueParameterResolver<>(1);
                }
                return null;
            }
        }

        @Component
        public static class SomeComponent {

            private final List<String> invocations = new ArrayList<>();

            public SomeComponent(LegacyConfiguration axonConfig) {
                // just to see if wiring the configuration causes circular dependencies.
            }

            @EventHandler
            public void handle(String event, SomeOtherComponent test, Integer testing) {
                invocations.add(event);
            }
        }

        @Component
        public static class SomeOtherComponent {

        }
    }

    public static class CustomResource {

    }
}
