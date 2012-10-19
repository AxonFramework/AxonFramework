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

package org.axonframework.contextsupport.spring;

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.replay.ReplayingCluster;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context-cluster.xml"})
public class ClusterBeanDefinitionParserTest_DefaultSettings {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testBeansAreProperlyConfigured() {
        Cluster cluster1 = applicationContext.getBean("replayingCluster", Cluster.class);
        Cluster cluster2 = applicationContext.getBean("replayingClusterWithDefaults", Cluster.class);

        assertNotNull(cluster1);
        assertNotNull(cluster2);
        assertTrue(cluster1 instanceof ReplayingCluster);
        assertTrue(cluster2 instanceof ReplayingCluster);
    }


}
