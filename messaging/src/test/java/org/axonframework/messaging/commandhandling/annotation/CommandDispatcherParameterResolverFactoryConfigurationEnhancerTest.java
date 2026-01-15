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

package org.axonframework.messaging.commandhandling.annotation;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.common.configuration.StubLifecycleRegistry;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CommandDispatcherParameterResolverFactoryConfigurationEnhancer}.
 *
 * @author Steven van Beelen
 */
class CommandDispatcherParameterResolverFactoryConfigurationEnhancerTest {

    @Test
    void registersParameterResolverToComponentRegistry() {
        DefaultComponentRegistry registry = new DefaultComponentRegistry();
        registry.disableEnhancerScanning();
        registry.registerEnhancer(new CommandDispatcherParameterResolverFactoryConfigurationEnhancer());

        Configuration configuration = registry.build(new StubLifecycleRegistry());

        ParameterResolverFactory factory = configuration.getComponent(ParameterResolverFactory.class);
        assertInstanceOf(CommandDispatcherParameterResolverFactory.class, factory);
    }
}