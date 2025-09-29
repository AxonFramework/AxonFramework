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

import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.inmemory.InMemoryTokenStore;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating whether a Spring {@link Component} with an {@link EventHandler} results in a working
 * {@link org.axonframework.eventhandling.processors.streaming.StreamingEventProcessor} that is invoked when an event is
 * published.
 *
 * @author Mateusz Nowak
 * @author Simon Zambrovski
 * @author Steven van Beelen
 */
class EventProcessorAutoConfigurationTest {

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
            EventGateway eventGateway = context.getBean(EventGateway.class);
            eventGateway.publish(null, "some-event-to-echo");

            AtomicBoolean invoked = context.getBean(AtomicBoolean.class);
            assertThat(invoked).isTrue();
        });
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @Bean
        public AtomicBoolean invoked() {
            return new AtomicBoolean(false);
        }

        @Bean
        public TokenStore tokenStore() {
            return new InMemoryTokenStore();
        }

        @SuppressWarnings("unused")
        @Component
        public static class EventHandlingComponent {

            private final AtomicBoolean invoked;

            public EventHandlingComponent(AtomicBoolean invoked) {
                this.invoked = invoked;
            }

            @SuppressWarnings("unused")
            @EventHandler
            public void on(String event) {
                invoked.set(true);
            }
        }
    }
}
