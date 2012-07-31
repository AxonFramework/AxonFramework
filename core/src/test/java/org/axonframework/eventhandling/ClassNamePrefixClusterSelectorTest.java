/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class ClassNamePrefixClusterSelectorTest {

    @Test
    public void testLongestPrefixEvaluatedFirst() {
        Cluster defaultCluster = new SimpleCluster();
        Cluster cluster1 = new SimpleCluster();
        Cluster cluster2 = new SimpleCluster();

        Map<String, Cluster> mappings = new HashMap<String, Cluster>();
        mappings.put("org.axonframework", cluster1);
        mappings.put("org", cluster2);
        mappings.put("$Proxy", cluster2);
        ClassNamePrefixClusterSelector selector = new ClassNamePrefixClusterSelector(mappings, defaultCluster);

        Cluster actual = selector.selectCluster(new EventListener() {
            @Override
            public void handle(EventMessage event) {
            }
        });
        assertSame(cluster1, actual);
    }

    @Test
    public void testInitializeWithSingleMapping() {
        Cluster cluster1 = new SimpleCluster();

        ClassNamePrefixClusterSelector selector = new ClassNamePrefixClusterSelector("org.axonframework", cluster1);

        Cluster actual = selector.selectCluster(new EventListener() {
            @Override
            public void handle(EventMessage event) {
            }
        });
        assertSame(cluster1, actual);
    }

    @Test
    public void testRevertsToDefaultWhenNoMappingFound() {
        Cluster defaultCluster = new SimpleCluster();
        Cluster cluster1 = new SimpleCluster();

        Map<String, Cluster> mappings = new HashMap<String, Cluster>();
        mappings.put("javax.", cluster1);
        ClassNamePrefixClusterSelector selector = new ClassNamePrefixClusterSelector(mappings, defaultCluster);

        Cluster actual = selector.selectCluster(new EventListener() {
            @Override
            public void handle(EventMessage event) {
            }
        });
        assertSame(defaultCluster, actual);
    }

    @Test
    public void testReturnsNullWhenNoMappingFound() {
        Cluster cluster1 = new SimpleCluster();

        Map<String, Cluster> mappings = new HashMap<String, Cluster>();
        mappings.put("javax.", cluster1);
        ClassNamePrefixClusterSelector selector = new ClassNamePrefixClusterSelector(mappings);

        Cluster actual = selector.selectCluster(new EventListener() {
            @Override
            public void handle(EventMessage event) {
            }
        });
        assertSame(null, actual);
    }
}
