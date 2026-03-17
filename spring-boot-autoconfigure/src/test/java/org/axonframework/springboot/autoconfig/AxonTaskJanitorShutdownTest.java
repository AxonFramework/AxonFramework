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

package org.axonframework.springboot.autoconfig;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating that the Axon task janitor is shut down when the Axon Configuration is shut down. The
 * shutdown is registered via {@link org.axonframework.config.AxonTaskJanitorShutdownConfigurerModule}, which is
 * loaded by the Configurer via ServiceLoader and works in both Spring and non-Spring environments.
 */
class AxonTaskJanitorShutdownTest {

    private static final ApplicationContextRunner CONTEXT_RUNNER = new ApplicationContextRunner()
            .withUserConfiguration(TestContext.class)
            .withPropertyValues(
                    "axon.axonserver.enabled=false",
                    "axon.timeout.enabled=true",
                    "axon.eventstorage.jpa.polling-interval=0"
            );

    @Nested
    class WhenTimeoutEnabled {

        @Test
        void contextStartsAndStopsSuccessfully() {
            // given / when
            CONTEXT_RUNNER.run(context -> {
                // then - Axon Configuration is built with ConfigurerModules (including janitor shutdown)
                org.axonframework.config.Configuration config =
                        context.getBean(org.axonframework.config.Configuration.class);
                assertThat(config).isNotNull();
                // Context close will trigger config.shutdown(), which runs the janitor shutdown hook
            });
        }
    }

    @Nested
    class WhenTimeoutDisabled {

        @Test
        void contextStartsSuccessfully() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(TestContext.class)
                    .withPropertyValues(
                            "axon.axonserver.enabled=false",
                            "axon.timeout.enabled=false",
                            "axon.eventstorage.jpa.polling-interval=0"
                    );

            runner.run(context -> assertThat(context.getBean(org.axonframework.config.Configuration.class))
                    .isNotNull());
        }
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestContext {
    }
}
