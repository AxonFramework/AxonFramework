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

package org.axonframework.config;

import org.axonframework.lifecycle.Phase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test class validating that {@link AxonTaskJanitorShutdownConfigurerModule} registers a shutdown hook with the
 * configurer.
 */
class AxonTaskJanitorShutdownConfigurerModuleTest {

    @Nested
    class ConfigureModule {

        @Test
        void registersShutdownHookWithConfigurer() {
            // given
            Configurer configurer = mock(Configurer.class);
            AxonTaskJanitorShutdownConfigurerModule module = new AxonTaskJanitorShutdownConfigurerModule();

            // when
            module.configureModule(configurer);

            // then
            verify(configurer).onShutdown(eq(Phase.EXTERNAL_CONNECTIONS - 100), any(Runnable.class));
        }
    }

    @Nested
    class Order {

        @Test
        void returnsDefaultOrder() {
            AxonTaskJanitorShutdownConfigurerModule module = new AxonTaskJanitorShutdownConfigurerModule();
            assertEquals(0, module.order());
        }
    }
}
