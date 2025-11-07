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

package org.axonframework.modelling.annotation;

import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class InjectEntityParameterResolverFactoryConfigurationEnhancerTest {

    @Test
    void addsInjectEntityParameterResolverFactory() {

        DefaultComponentRegistry componentRegistry = new DefaultComponentRegistry();
        componentRegistry.disableEnhancerScanning()
                         .registerEnhancer(new InjectEntityParameterResolverFactoryConfigurationEnhancer());

        Configuration build = componentRegistry.build(Mockito.mock(LifecycleRegistry.class));

        ParameterResolverFactory factory = build.getComponent(ParameterResolverFactory.class);
        assertInstanceOf(InjectEntityParameterResolverFactory.class, factory);
    }
}