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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.extension.reactor.messaging.commandhandling.gateway.DefaultReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.DefaultReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.DefaultReactiveQueryGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactiveQueryGateway;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Test class validating the {@link ReactorAutoConfiguration}.
 */
class ReactorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactorAutoConfiguration.class))
            .withUserConfiguration(RequiredBeansConfiguration.class);

    @Nested
    class BeanRegistration {

        @Test
        void registersAllThreeReactiveGateways() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ReactiveCommandGateway.class);
                assertThat(context).hasSingleBean(ReactiveEventGateway.class);
                assertThat(context).hasSingleBean(ReactiveQueryGateway.class);
            });
        }

        @Test
        void registersDefaultImplementations() {
            contextRunner.run(context -> {
                assertThat(context.getBean(ReactiveCommandGateway.class))
                        .isInstanceOf(DefaultReactiveCommandGateway.class);
                assertThat(context.getBean(ReactiveEventGateway.class))
                        .isInstanceOf(DefaultReactiveEventGateway.class);
                assertThat(context.getBean(ReactiveQueryGateway.class))
                        .isInstanceOf(DefaultReactiveQueryGateway.class);
            });
        }
    }

    @Nested
    class ConditionalOnMissingBean {

        @Test
        void doesNotOverrideUserDefinedReactiveCommandGateway() {
            contextRunner
                    .withUserConfiguration(CustomCommandGatewayConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveCommandGateway.class);
                        assertThat(context.getBean(ReactiveCommandGateway.class))
                                .isInstanceOf(CustomReactiveCommandGateway.class);
                    });
        }

        @Test
        void doesNotOverrideUserDefinedReactiveEventGateway() {
            contextRunner
                    .withUserConfiguration(CustomEventGatewayConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveEventGateway.class);
                        assertThat(context.getBean(ReactiveEventGateway.class))
                                .isInstanceOf(CustomReactiveEventGateway.class);
                    });
        }

        @Test
        void doesNotOverrideUserDefinedReactiveQueryGateway() {
            contextRunner
                    .withUserConfiguration(CustomQueryGatewayConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveQueryGateway.class);
                        assertThat(context.getBean(ReactiveQueryGateway.class))
                                .isInstanceOf(CustomReactiveQueryGateway.class);
                    });
        }
    }

    @Nested
    class InterceptorWiring {

        @Test
        void commandInterceptorBeansAreWiredIntoGateway() {
            contextRunner
                    .withUserConfiguration(CommandInterceptorConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveCommandGateway.class);
                        // The gateway is created — if interceptor wiring failed, context would not start
                        assertThat(context.getBean(ReactiveCommandGateway.class))
                                .isInstanceOf(DefaultReactiveCommandGateway.class);
                    });
        }

        @Test
        void eventInterceptorBeansAreWiredIntoGateway() {
            contextRunner
                    .withUserConfiguration(EventInterceptorConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveEventGateway.class);
                        assertThat(context.getBean(ReactiveEventGateway.class))
                                .isInstanceOf(DefaultReactiveEventGateway.class);
                    });
        }

        @Test
        void queryInterceptorBeansAreWiredIntoGateway() {
            contextRunner
                    .withUserConfiguration(QueryInterceptorConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveQueryGateway.class);
                        assertThat(context.getBean(ReactiveQueryGateway.class))
                                .isInstanceOf(DefaultReactiveQueryGateway.class);
                    });
        }
    }

    // -- Test configurations --

    @Configuration
    static class RequiredBeansConfiguration {

        @Bean
        CommandGateway commandGateway() {
            return mock(CommandGateway.class);
        }

        @Bean
        EventGateway eventGateway() {
            return mock(EventGateway.class);
        }

        @Bean
        QueryGateway queryGateway() {
            return mock(QueryGateway.class);
        }

        @Bean
        MessageTypeResolver messageTypeResolver() {
            return new ClassBasedMessageTypeResolver();
        }
    }

    @Configuration
    static class CustomCommandGatewayConfiguration {

        @Bean
        ReactiveCommandGateway reactiveCommandGateway() {
            return new CustomReactiveCommandGateway();
        }
    }

    @Configuration
    static class CustomEventGatewayConfiguration {

        @Bean
        ReactiveEventGateway reactiveEventGateway() {
            return new CustomReactiveEventGateway();
        }
    }

    @Configuration
    static class CustomQueryGatewayConfiguration {

        @Bean
        ReactiveQueryGateway reactiveQueryGateway() {
            return new CustomReactiveQueryGateway();
        }
    }

    @Configuration
    static class CommandInterceptorConfiguration {

        @Bean
        ReactiveMessageDispatchInterceptor<CommandMessage> testCommandInterceptor() {
            return (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("intercepted", "true"));
                return chain.proceed(enriched);
            };
        }
    }

    @Configuration
    static class EventInterceptorConfiguration {

        @Bean
        ReactiveMessageDispatchInterceptor<EventMessage> testEventInterceptor() {
            return (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("intercepted", "true"));
                return chain.proceed(enriched);
            };
        }
    }

    @Configuration
    static class QueryInterceptorConfiguration {

        @Bean
        ReactiveMessageDispatchInterceptor<QueryMessage> testQueryInterceptor() {
            return (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("intercepted", "true"));
                return chain.proceed(enriched);
            };
        }
    }

    // -- Custom gateway stubs for ConditionalOnMissingBean tests --

    static class CustomReactiveCommandGateway implements ReactiveCommandGateway {

        @Override
        public <R> reactor.core.publisher.Mono<R> send(Object command, Class<R> resultType) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<Void> send(Object command) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public void registerDispatchInterceptor(ReactiveMessageDispatchInterceptor<CommandMessage> interceptor) {
        }
    }

    static class CustomReactiveEventGateway implements ReactiveEventGateway {

        @Override
        public reactor.core.publisher.Mono<Void> publish(Object... events) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<Void> publish(java.util.List<?> events) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public void registerDispatchInterceptor(ReactiveMessageDispatchInterceptor<EventMessage> interceptor) {
        }
    }

    static class CustomReactiveQueryGateway implements ReactiveQueryGateway {

        @Override
        public <R> reactor.core.publisher.Mono<R> query(Object query, Class<R> responseType) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public <R> reactor.core.publisher.Mono<java.util.List<R>> queryMany(Object query, Class<R> responseType) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public <R> reactor.core.publisher.Flux<R> streamingQuery(Object query, Class<R> responseType) {
            return reactor.core.publisher.Flux.empty();
        }

        @Override
        public <R> reactor.core.publisher.Flux<R> subscriptionQuery(Object query, Class<R> responseType) {
            return reactor.core.publisher.Flux.empty();
        }

        @Override
        public void registerDispatchInterceptor(ReactiveMessageDispatchInterceptor<QueryMessage> interceptor) {
        }
    }
}
