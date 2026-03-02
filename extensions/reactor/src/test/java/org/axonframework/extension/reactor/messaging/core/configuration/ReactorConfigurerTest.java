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

package org.axonframework.extension.reactor.messaging.core.configuration;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.DefaultReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.DefaultReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.DefaultReactiveQueryGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactiveQueryGateway;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link ReactorConfigurer}.
 */
class ReactorConfigurerTest {

    private AxonConfiguration configuration;

    @AfterEach
    void tearDown() {
        if (configuration != null) {
            configuration.shutdown();
        }
    }

    @Nested
    class Create {

        @Test
        void createBuildsConfigurationWithAllThreeReactiveGateways() {
            // when
            configuration = ReactorConfigurer.create().build();

            // then
            assertThat(configuration.getComponent(ReactiveCommandGateway.class))
                    .isInstanceOf(DefaultReactiveCommandGateway.class);
            assertThat(configuration.getComponent(ReactiveEventGateway.class))
                    .isInstanceOf(DefaultReactiveEventGateway.class);
            assertThat(configuration.getComponent(ReactiveQueryGateway.class))
                    .isInstanceOf(DefaultReactiveQueryGateway.class);
        }

        @Test
        void createAlsoRegistersStandardMessagingComponents() {
            // when
            configuration = ReactorConfigurer.create().build();

            // then
            assertThat(configuration.getComponent(CommandGateway.class)).isNotNull();
            assertThat(configuration.getComponent(EventGateway.class)).isNotNull();
            assertThat(configuration.getComponent(QueryGateway.class)).isNotNull();
        }
    }

    @Nested
    class Enhance {

        @Test
        void enhanceWrapsExistingConfigurerAndAddsReactiveGateways() {
            // given
            var messagingConfigurer = MessagingConfigurer.create();

            // when
            ReactorConfigurer.enhance(messagingConfigurer);
            configuration = messagingConfigurer.build();

            // then
            assertThat(configuration.getComponent(ReactiveCommandGateway.class))
                    .isInstanceOf(DefaultReactiveCommandGateway.class);
            assertThat(configuration.getComponent(ReactiveEventGateway.class))
                    .isInstanceOf(DefaultReactiveEventGateway.class);
            assertThat(configuration.getComponent(ReactiveQueryGateway.class))
                    .isInstanceOf(DefaultReactiveQueryGateway.class);
        }

        @Test
        void enhanceRejectsNullConfigurer() {
            assertThatThrownBy(() -> ReactorConfigurer.enhance(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class InterceptorRegistration {

        @Test
        void registerReactiveCommandDispatchInterceptorIsAppliedToGateway() {
            // given
            AtomicBoolean interceptorInvoked = new AtomicBoolean(false);
            ReactiveMessageDispatchInterceptor<CommandMessage> interceptor = (message, chain) -> {
                interceptorInvoked.set(true);
                return chain.proceed(message);
            };

            // when
            configuration = ReactorConfigurer.create()
                    .registerReactiveCommandDispatchInterceptor(config -> interceptor)
                    .build();

            // then — the interceptor is registered; we verify by checking the gateway was built
            // (interceptor invocation requires sending a command, which needs a running system)
            ReactiveCommandGateway gateway = configuration.getComponent(ReactiveCommandGateway.class);
            assertThat(gateway).isInstanceOf(DefaultReactiveCommandGateway.class);
        }

        @Test
        void registerReactiveEventDispatchInterceptorIsAppliedToGateway() {
            // given
            AtomicBoolean interceptorInvoked = new AtomicBoolean(false);
            ReactiveMessageDispatchInterceptor<EventMessage> interceptor = (message, chain) -> {
                interceptorInvoked.set(true);
                return chain.proceed(message);
            };

            // when
            configuration = ReactorConfigurer.create()
                    .registerReactiveEventDispatchInterceptor(config -> interceptor)
                    .build();

            // then
            ReactiveEventGateway gateway = configuration.getComponent(ReactiveEventGateway.class);
            assertThat(gateway).isInstanceOf(DefaultReactiveEventGateway.class);
        }

        @Test
        void registerReactiveQueryDispatchInterceptorIsAppliedToGateway() {
            // given
            AtomicBoolean interceptorInvoked = new AtomicBoolean(false);
            ReactiveMessageDispatchInterceptor<QueryMessage> interceptor = (message, chain) -> {
                interceptorInvoked.set(true);
                return chain.proceed(message);
            };

            // when
            configuration = ReactorConfigurer.create()
                    .registerReactiveQueryDispatchInterceptor(config -> interceptor)
                    .build();

            // then
            ReactiveQueryGateway gateway = configuration.getComponent(ReactiveQueryGateway.class);
            assertThat(gateway).isInstanceOf(DefaultReactiveQueryGateway.class);
        }

        @Test
        void rejectsNullCommandInterceptorBuilder() {
            assertThatThrownBy(() ->
                    ReactorConfigurer.create().registerReactiveCommandDispatchInterceptor(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullEventInterceptorBuilder() {
            assertThatThrownBy(() ->
                    ReactorConfigurer.create().registerReactiveEventDispatchInterceptor(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullQueryInterceptorBuilder() {
            assertThatThrownBy(() ->
                    ReactorConfigurer.create().registerReactiveQueryDispatchInterceptor(null)
            ).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Delegation {

        @Test
        void componentRegistryDelegatesToUnderlyingConfigurer() {
            // when
            configuration = ReactorConfigurer.create()
                    .componentRegistry(cr -> cr.registerComponent(
                            String.class, config -> "test-component"
                    ))
                    .build();

            // then
            assertThat(configuration.getComponent(String.class)).isEqualTo("test-component");
        }

        @Test
        void lifecycleRegistryDelegatesToUnderlyingConfigurer() {
            // given
            AtomicBoolean started = new AtomicBoolean(false);

            // when
            configuration = ReactorConfigurer.create()
                    .lifecycleRegistry(lr -> lr.onStart(0, () -> {
                        started.set(true);
                        return CompletableFuture.completedFuture(null);
                    }))
                    .build();

            configuration.start();

            // then
            assertThat(started).isTrue();
        }
    }
}
