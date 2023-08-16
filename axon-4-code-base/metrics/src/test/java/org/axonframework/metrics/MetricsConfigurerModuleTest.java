/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.metrics;

import org.axonframework.config.Configurer;
import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

class MetricsConfigurerModuleTest {

    private GlobalMetricRegistry globalMetricRegistry;
    private MetricsConfigurerModule metricsConfigurerModule;

    @BeforeEach
    void setUp() {
        globalMetricRegistry = mock(GlobalMetricRegistry.class);
        metricsConfigurerModule = new MetricsConfigurerModule(globalMetricRegistry);
    }

    @Test
    void configureModuleCallsGlobalMetricRegistry() {
        Configurer configurerMock = mock(Configurer.class);

        metricsConfigurerModule.configureModule(configurerMock);

        verify(globalMetricRegistry).registerWithConfigurer(configurerMock);
    }
}
