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

import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactorCommandGateway;
import org.axonframework.extension.reactor.messaging.core.interception.ReactorDispatchInterceptorRegistry;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactorEventGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactorQueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link ReactorConfigurationDefaults}.
 *
 * @author Theo Emanuelsson
 */
class ReactorConfigurationDefaultsTest {

    private ReactorConfigurationDefaults testSubject;
    private ComponentRegistry registry;

    @BeforeEach
    void setUp() {
        testSubject = new ReactorConfigurationDefaults();
        registry = mock(ComponentRegistry.class);
        when(registry.registerIfNotPresent(any(Class.class), any(ComponentBuilder.class)))
                .thenReturn(registry);
    }

    @Nested
    class Enhance {

        @Test
        void registersRegistryAndAllThreeReactorGateways() {
            // when
            testSubject.enhance(registry);

            // then
            verify(registry).registerIfNotPresent(
                    eq(ReactorDispatchInterceptorRegistry.class), any(ComponentBuilder.class)
            );
            verify(registry).registerIfNotPresent(eq(ReactorCommandGateway.class), any(ComponentBuilder.class));
            verify(registry).registerIfNotPresent(eq(ReactorEventGateway.class), any(ComponentBuilder.class));
            verify(registry).registerIfNotPresent(eq(ReactorQueryGateway.class), any(ComponentBuilder.class));
        }
    }

    @Nested
    class Order {

        @Test
        void orderIsMaxValueSameAsMessagingDefaults() {
            assertThat(testSubject.order()).isEqualTo(Integer.MAX_VALUE);
        }
    }
}
