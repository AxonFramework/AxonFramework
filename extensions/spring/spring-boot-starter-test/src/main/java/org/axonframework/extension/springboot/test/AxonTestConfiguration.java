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

package org.axonframework.extension.springboot.test;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.fixture.MessagesRecordingConfigurationEnhancer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration that registers the infrastructure required for {@link AxonTestFixture} in a Spring Boot test
 * context.
 * <p>
 * Registers:
 * <ul>
 *     <li>{@link MessagesRecordingConfigurationEnhancer} — decorates the command bus and event sink with recording
 *     wrappers so that the fixture can capture dispatched commands and published events.</li>
 *     <li>{@link AxonTestFixture} — the main test fixture, wired with the Spring-managed
 *     {@link AxonConfiguration}.</li>
 * </ul>
 * <p>
 * The fixture's {@link AxonTestFixture.Customization} can be overridden by declaring a bean of that type in the
 * test class (e.g. in an inner {@code @TestConfiguration} class). When no such bean is present, a default
 * {@code Customization} is used.
 *
 * @author Mateusz Nowak
 * @since 5.0.3
 */
@TestConfiguration
class AxonTestConfiguration {

    /**
     * Registers a {@link MessagesRecordingConfigurationEnhancer} that decorates the {@code CommandBus} and
     * {@code EventSink} with recording wrappers. This is required for {@link AxonTestFixture} to capture dispatched
     * commands and published events during test execution.
     *
     * @return a new {@link MessagesRecordingConfigurationEnhancer}
     */
    @Bean
    MessagesRecordingConfigurationEnhancer recordingEnhancer() {
        return new MessagesRecordingConfigurationEnhancer();
    }

    /**
     * Registers an {@link AxonTestFixture} wired with the Spring-managed {@link AxonConfiguration}.
     * <p>
     * If the test context contains a {@link AxonTestFixture.Customization} bean (e.g. declared in an inner
     * {@code @TestConfiguration} class), it is used to configure the fixture. Otherwise the default
     * {@code new Customization()} is applied.
     *
     * @param configuration  the Axon application configuration provided by the Spring context
     * @param customization  optional provider for a {@link AxonTestFixture.Customization} bean
     * @return a ready-to-use {@link AxonTestFixture}
     */
    @Bean
    AxonTestFixture axonTestFixture(
            AxonConfiguration configuration,
            ObjectProvider<AxonTestFixture.Customization> customization
    ) {
        return new AxonTestFixture(
                configuration,
                customization.getIfAvailable(AxonTestFixture.Customization::new)
        );
    }
}
