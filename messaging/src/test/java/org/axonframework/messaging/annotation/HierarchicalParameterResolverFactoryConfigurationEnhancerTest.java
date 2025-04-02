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
import org.axonframework.configuration.NewConfiguration;
import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

class HierarchicalParameterResolverFactoryConfigurationEnhancerTest {

    @Test
    void createsHierarchicalParameterResolverFromParentAndChild() {
        // Set up a parent with a unique ParameterResolverFactory
        DefaultComponentRegistry parent = createCleanComponentRegistry();
        ParameterResolverFactory parentParameterResolverFactory = mock(ParameterResolverFactory.class);
        parent.registerComponent(ParameterResolverFactory.class, c -> parentParameterResolverFactory);

        // And a child
        DefaultComponentRegistry child = createCleanComponentRegistry();
        ParameterResolverFactory childParameterResolverFactory = mock(ParameterResolverFactory.class);
        child.registerComponent(ParameterResolverFactory.class, c -> childParameterResolverFactory);

        // Build both in a nested way
        NewConfiguration parentConfiguration = parent.build(mock(LifecycleRegistry.class));
        NewConfiguration childConfiguration = child.buildNested(parentConfiguration, mock(LifecycleRegistry.class));

        // And assert that the child configuration has a HierarchicalParameterResolverFactory
        ParameterResolverFactory parentFactory = parentConfiguration.getComponent(ParameterResolverFactory.class);
        ParameterResolverFactory childFactory = childConfiguration.getComponent(ParameterResolverFactory.class);

        Assertions.assertNotSame(parentFactory, childFactory);
        Assertions.assertInstanceOf(HierarchicalParameterResolverFactory.class, childFactory);
        Assertions.assertEquals(parentFactory, ((HierarchicalParameterResolverFactory) childFactory).getParent());
        Assertions.assertEquals(childParameterResolverFactory,
                                ((HierarchicalParameterResolverFactory) childFactory).getChild());
    }

    private DefaultComponentRegistry createCleanComponentRegistry() {
        DefaultComponentRegistry defaultComponentRegistry = new DefaultComponentRegistry();
        defaultComponentRegistry.disableEnhancerScanning()
                                .registerEnhancer(new HierarchicalParameterResolverFactoryConfigurationEnhancer());
        return defaultComponentRegistry;
    }
}