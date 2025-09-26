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

package org.axonframework.eventhandling.annotations;

import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.DefaultComponentRegistry;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.utils.StubLifecycleRegistry;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventAppenderParameterResolverFactoryConfigurationEnhancer}.
 *
 * @author Mitchell Herrijgers.
 */
class EventAppenderParameterResolverFactoryConfigurationEnhancerTest {

    @Test
    void registersParameterResolverToComponentRegistry() {
        DefaultComponentRegistry registry = new DefaultComponentRegistry();
        registry.disableEnhancerScanning();
        registry.registerEnhancer(new EventAppenderParameterResolverFactoryConfigurationEnhancer());

        Configuration configuration = registry.build(new StubLifecycleRegistry());

        ParameterResolverFactory factory = configuration.getComponent(ParameterResolverFactory.class);
        assertInstanceOf(EventAppenderParameterResolverFactory.class, factory);
    }
}