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

package org.axonframework.messaging.annotation;

import org.axonframework.configuration.DefaultComponentRegistry;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.annotations.ConcludesBatchParameterResolverFactory;
import org.axonframework.messaging.annotations.MultiParameterResolverFactory;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.configuration.reflection.ConfigurationParameterResolverFactory;
import org.axonframework.messaging.configuration.reflection.ConfigurationParameterResolverConfigurationEnhancer;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationParameterResolverConfigurationEnhancerTest {

    @Test
    void addsConfigurationParameterResolverFactory() {

        DefaultComponentRegistry componentRegistry = new DefaultComponentRegistry();
        componentRegistry.disableEnhancerScanning()
                         .registerEnhancer(new ConfigurationParameterResolverConfigurationEnhancer())
                         .registerComponent(ParameterResolverFactory.class,
                                            (c) -> new ConcludesBatchParameterResolverFactory());


        Configuration build = componentRegistry.build(Mockito.mock(LifecycleRegistry.class));

        ParameterResolverFactory factory = build.getComponent(ParameterResolverFactory.class);
        assertInstanceOf(MultiParameterResolverFactory.class, factory);
        assertEquals(2, ((MultiParameterResolverFactory) factory).getDelegates().size());
        assertInstanceOf(ConcludesBatchParameterResolverFactory.class,
                         ((MultiParameterResolverFactory) factory).getDelegates().get(0));
        assertInstanceOf(ConfigurationParameterResolverFactory.class,
                         ((MultiParameterResolverFactory) factory).getDelegates().get(1));
    }
}