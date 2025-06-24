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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.spring.config.SpringAxonApplication;
import org.axonframework.spring.config.SpringComponentRegistry;
import org.axonframework.spring.config.SpringLifecycleRegistry;
import org.axonframework.spring.config.SpringLifecycleShutdownHandler;
import org.axonframework.spring.config.SpringLifecycleStartHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AxonAutoConfiguration}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public class AxonAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class)
                .withPropertyValues("axon.axonserver.enabled=false");
    }

    @Test
    void expectedAxonConfigurationBeansAreAutomaticallyConfigured() {
        testContext.run(context -> {
            assertThat(context).hasBean("springComponentRegistry");
            assertThat(context).hasBean("springLifecycleRegistry");
            assertThat(context).hasBean("axonApplication");
            assertThat(context).hasBean("axonApplicationConfiguration");
        });
    }

    @Test
    void expectedAxonConfigurationBeansCanAllBeCustomized() {
        testContext.withUserConfiguration(CustomContext.class).run(context -> {
            assertThat(context).hasBean("customComponentRegistry");
            assertThat(context).hasBean("customLifecycleRegistry");
            assertThat(context).hasBean("customAxonApplication");
            assertThat(context).hasBean("customAxonApplicationConfiguration");

            assertThat(context).doesNotHaveBean("springComponentRegistry");
            assertThat(context).doesNotHaveBean("springLifecycleRegistry");
            assertThat(context).doesNotHaveBean("axonApplication");
            assertThat(context).doesNotHaveBean("axonApplicationConfiguration");
            assertThat(context).doesNotHaveBean("axonConfiguration");
        });
    }

    @Test
    void lifecycleRegistryIntegratesWithDedicatedSpringLifecycleBeans() {
        testContext.run(context -> {
            // We expect beans to be registered for lifecycle handlers.
            Map<String, SpringLifecycleStartHandler> startHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleStartHandler.class
            );
            Map<String, SpringLifecycleShutdownHandler> shutdownHandlers = BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    context, SpringLifecycleShutdownHandler.class
            );

            // The TestContext registers a start handler on phase 10 for the SimpleCommandBus
            assertTrue(startHandlers.values().stream().anyMatch(h -> h.getPhase() == 10));
            // The TestContext registers a shutdown handler on phase 12 for the SimpleCommandBus
            assertTrue(shutdownHandlers.values().stream().anyMatch(h -> h.getPhase() == 12));

            for (SpringLifecycleStartHandler startHandler : startHandlers.values()) {
                assertTrue(startHandler.isRunning());
            }
            for (SpringLifecycleShutdownHandler shutdownHandler : shutdownHandlers.values()) {
                assertTrue(shutdownHandler.isRunning());
            }

            AtomicBoolean startHandlerInvoked = context.getBean("startHandlerInvoked", AtomicBoolean.class);
            assertTrue(startHandlerInvoked.get());
            AtomicBoolean shutdownHandlerInvoked = context.getBean("shutdownHandlerInvoked", AtomicBoolean.class);
            assertFalse(shutdownHandlerInvoked.get());

            // TODO await response if we can even test this
            // context.stop();
            // await().atMost(Duration.ofSeconds(5))
            //        .pollDelay(Duration.ofMillis(25))
            //        .until(shutdownHandlerInvoked::get);
        });
    }

    /**
     * All validated beans originate from the {@code org.axonframework.configuration.MessagingConfigurationDefaults}
     * that's set on the {@link org.axonframework.configuration.MessagingConfigurer}.
     */
    @Test
    void defaultAxonMessagingComponentsArePresent() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(MessageTypeResolver.class);
            assertThat(context).hasSingleBean(CommandGateway.class);
            assertThat(context).hasSingleBean(CommandBus.class);
            assertThat(context).hasSingleBean(EventGateway.class);
            assertThat(context).hasSingleBean(EventSink.class);
            assertThat(context).hasSingleBean(EventBus.class);
            assertThat(context).hasSingleBean(QueryGateway.class);
            assertThat(context).hasSingleBean(QueryBus.class);
            assertThat(context).hasSingleBean(QueryUpdateEmitter.class);
        });
    }

    /**
     * All validated beans originate from the
     * {@code org.axonframework.eventsourcing.configuration.EventSourcingConfigurationDefaults} that's set on the
     * {@link org.axonframework.eventsourcing.configuration.EventSourcingConfigurer}.
     */
    @Test
    void defaultAxonEventSourcingComponentsArePresent() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(TagResolver.class);
            assertThat(context).hasSingleBean(EventStorageEngine.class);
            assertThat(context).hasSingleBean(EventStore.class);
            assertThat(context).hasSingleBean(EventSink.class);
            assertThat(context).hasSingleBean(Snapshotter.class);
        });
    }

    @Configuration
    @EnableAutoConfiguration
    public static class TestContext {

        @Bean
        AtomicBoolean startHandlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        AtomicBoolean shutdownHandlerInvoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        CommandBus commandBus(LifecycleRegistry lifecycleRegistry,
                              AtomicBoolean startHandlerInvoked,
                              AtomicBoolean shutdownHandlerInvoked) {
            SimpleCommandBus simpleCommandBus = new SimpleCommandBus();
            lifecycleRegistry.onStart(10, () -> startHandlerInvoked.set(true));
            lifecycleRegistry.onShutdown(12, () -> shutdownHandlerInvoked.set(true));
            return simpleCommandBus;
        }

        @Bean
        ConfigurationEnhancer configurationEnhancer() {
            return registry -> registry.registerDecorator(
                    CommandBus.class, 0,
                    (ComponentDecorator<CommandBus, CommandBus>) (config, name, delegate) ->
                            new InterceptingCommandBus(delegate, List.of(), List.of())
            );
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class CustomContext {

        @Bean
        SpringComponentRegistry customComponentRegistry(ApplicationContext applicationContext) {
            return new SpringComponentRegistry(applicationContext);
        }

        @Bean
        SpringLifecycleRegistry customLifecycleRegistry() {
            return new SpringLifecycleRegistry();
        }

        @Bean
        SpringAxonApplication customAxonApplication(SpringComponentRegistry customComponentRegistry,
                                                    SpringLifecycleRegistry customLifecycleRegistry) {
            return new SpringAxonApplication(customComponentRegistry, customLifecycleRegistry);
        }

        @Bean
        AxonConfiguration customAxonApplicationConfiguration(SpringAxonApplication customAxonApplication) {
            return customAxonApplication.build();
        }
    }
}
