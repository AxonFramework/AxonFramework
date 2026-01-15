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

package org.axonframework.modelling;

import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.configuration.Configuration;
import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

class HierarchicalStateManagerConfigurationEnhancerTest {
    @Test
    void createsHierarchicalParameterResolverFromParentAndChild() {
        // Set up a parent with a unique StateManager
        DefaultComponentRegistry parent = createCleanComponentRegistry();
        StateManager parentStateManager = mock(StateManager.class);
        parent.registerComponent(StateManager.class, c -> parentStateManager);

        // And a child
        DefaultComponentRegistry child = createCleanComponentRegistry();
        StateManager childStateManager = mock(StateManager.class);
        child.registerComponent(StateManager.class, c -> childStateManager);

        // Build both in a nested way
        Configuration parentConfiguration = parent.build(mock(LifecycleRegistry.class));
        Configuration childConfiguration = child.buildNested(parentConfiguration, mock(LifecycleRegistry.class));

        // And assert that the child configuration has a HierarchicalStateManager
        StateManager parentManager = parentConfiguration.getComponent(StateManager.class);
        StateManager childManager = childConfiguration.getComponent(StateManager.class);

        Assertions.assertNotSame(parentManager, childManager);
        Assertions.assertInstanceOf(HierarchicalStateManager.class, childManager);
        Assertions.assertEquals(parentManager, ((HierarchicalStateManager) childManager).getParent());
        Assertions.assertEquals(childStateManager,
                                ((HierarchicalStateManager) childManager).getChild());
    }

    private DefaultComponentRegistry createCleanComponentRegistry() {
        DefaultComponentRegistry defaultComponentRegistry = new DefaultComponentRegistry();
        defaultComponentRegistry.disableEnhancerScanning()
                                .registerEnhancer(new HierarchicalStateManagerConfigurationEnhancer());
        return defaultComponentRegistry;
    }
}