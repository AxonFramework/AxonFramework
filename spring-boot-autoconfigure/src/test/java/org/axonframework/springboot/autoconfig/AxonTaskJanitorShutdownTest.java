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

import org.axonframework.messaging.core.timeout.AxonTaskJanitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating that the {@link AxonTaskJanitorShutdownHandler} properly shuts down the janitor executor
 * service when the Spring context closes.
 *
 * @author Axon Framework Contributors
 */
class AxonTaskJanitorShutdownTest {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class)
                .withPropertyValues(
                        "axon.axonserver.enabled=false",
                        "axon.timeout.enabled=true",
                        "axon.eventstorage.jpa.polling-interval=0"
                );
    }

    @Test
    void janitorShutdownHandlerBeanIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("axonTaskJanitorShutdownHandler");
            assertThat(context).getBean("axonTaskJanitorShutdownHandler")
                    .isInstanceOf(AxonTaskJanitorShutdownHandler.class);
        });
    }

    @Test
    void janitorShutdownHandlerIsSmartLifecycle() {
        contextRunner.run(context -> {
            AxonTaskJanitorShutdownHandler handler = context.getBean(AxonTaskJanitorShutdownHandler.class);
            assertThat(handler).isInstanceOf(org.springframework.context.SmartLifecycle.class);
            assertThat(handler.isAutoStartup()).isTrue();
        });
    }

    @Test
    void janitorThreadIsShutDownOnContextClose() {
        contextRunner.run(context -> {
            AxonTaskJanitorShutdownHandler handler = context.getBean(AxonTaskJanitorShutdownHandler.class);
            handler.start();
            assertThat(handler.isRunning()).isTrue();
            
            handler.stop();
            assertThat(handler.isRunning()).isFalse();
        });
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestContext {
    }
}
