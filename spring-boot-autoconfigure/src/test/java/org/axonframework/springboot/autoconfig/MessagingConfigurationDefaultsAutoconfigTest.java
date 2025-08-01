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
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurationDefaults;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating that the {@link org.axonframework.configuration.MessagingConfigurationDefaults} are registered
 * and customizable when using Spring Boot.
 *
 * @author Steven van Beelen
 */
class MessagingConfigurationDefaultsAutoconfigTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class)
                .withPropertyValues("axon.axonserver.enabled=false");
    }

    @Test
    void defaultAxonMessagingComponentsArePresent() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(MessageTypeResolver.class);
            assertThat(context).hasBean(MessageTypeResolver.class.getName());
            assertThat(context).hasSingleBean(CommandGateway.class);
            assertThat(context).hasBean(CommandGateway.class.getName());
            assertThat(context).hasSingleBean(CommandBus.class);
            assertThat(context).hasBean(CommandBus.class.getName());
            assertThat(context).hasSingleBean(EventGateway.class);
            assertThat(context).hasBean(EventGateway.class.getName());
            assertThat(context).hasSingleBean(EventSink.class);
            assertThat(context).hasBean(EventSink.class.getName());
            assertThat(context).hasSingleBean(EventBus.class);
            assertThat(context).hasBean(EventBus.class.getName());
            assertThat(context).hasSingleBean(QueryGateway.class);
            assertThat(context).hasBean(QueryGateway.class.getName());
            assertThat(context).hasSingleBean(QueryBus.class);
            assertThat(context).hasBean(QueryBus.class.getName());
            assertThat(context).hasSingleBean(QueryUpdateEmitter.class);
            assertThat(context).hasBean(QueryUpdateEmitter.class.getName());
        });
    }

    @Test
    void overrideDefaultAxonMessagingComponents() {
        testContext.withUserConfiguration(CustomContext.class).run(context -> {
            assertThat(context).hasSingleBean(MessageTypeResolver.class);
            assertThat(context).hasBean("customMessageTypeResolver");
            assertThat(context).hasSingleBean(CommandGateway.class);
            assertThat(context).hasBean("customCommandGateway");
            assertThat(context).hasSingleBean(CommandBus.class);
            assertThat(context).hasBean("customCommandBus");
            assertThat(context).hasSingleBean(EventGateway.class);
            assertThat(context).hasBean("customEventGateway");
            assertThat(context).hasSingleBean(EventSink.class);
            assertThat(context).hasBean("customEventSink");
            assertThat(context).hasSingleBean(EventBus.class);
            assertThat(context).hasBean("customEventBus");
            assertThat(context).hasSingleBean(QueryGateway.class);
            assertThat(context).hasBean("customQueryGateway");
            assertThat(context).hasSingleBean(QueryBus.class);
            assertThat(context).hasBean("customQueryBus");
            assertThat(context).hasSingleBean(QueryUpdateEmitter.class);
            assertThat(context).hasBean("customQueryUpdateEmitter");
        });
    }

    @Configuration
    @EnableAutoConfiguration
    public static class TestContext {

        @Bean
        public ConfigurationEnhancer disableEventSourcingConfigurationDefaults() {
            return new ConfigurationEnhancer() {

                @Override
                public void enhance(@NotNull ComponentRegistry registry) {
                    registry.disableEnhancer(EventSourcingConfigurationDefaults.class);
                }

                @Override
                public int order() {
                    return Integer.MIN_VALUE;
                }
            };
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class CustomContext {

        @Bean
        public MessageTypeResolver customMessageTypeResolver() {
            return mock(MessageTypeResolver.class);
        }

        @Bean
        public CommandGateway customCommandGateway() {
            return mock(CommandGateway.class);
        }

        @Bean
        public CommandBus customCommandBus() {
            return mock(CommandBus.class);
        }

        @Bean
        public EventGateway customEventGateway() {
            return mock(EventGateway.class);
        }

        @Bean
        public EventSink customEventSink() {
            return mock(EventSink.class);
        }

        @Bean
        public EventBus customEventBus() {
            return mock(EventBus.class);
        }

        @Bean
        public QueryGateway customQueryGateway() {
            return mock(QueryGateway.class);
        }

        @Bean
        public QueryBus customQueryBus() {
            return mock(QueryBus.class);
        }

        @Bean
        public QueryUpdateEmitter customQueryUpdateEmitter() {
            return mock(QueryUpdateEmitter.class);
        }
    }
}
