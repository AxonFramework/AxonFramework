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

package org.axonframework.eventhandling.amqp;

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.SimpleCluster;
import org.junit.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class MetaDataPropertyQueueNameResolverTest {

    @Test
    public void testQueueNameIsProvided_DefaultMetaDataProperties() {
        Cluster cluster1 = new SimpleCluster();
        cluster1.getMetaData().setProperty("AMQP.QueueName", "queue1");
        cluster1.getMetaData().setProperty("ClusterName", "clusterName");

        String actual1 = new MetaDataPropertyQueueNameResolver("default").resolveQueueName(cluster1);
        assertEquals("queue1", actual1);
    }

    @Test
    public void testClusterNameIsProvided_DefaultMetaDataProperties() {
        Cluster cluster1 = new SimpleCluster();
        cluster1.getMetaData().setProperty("ClusterName", "clusterName");

        String actual1 = new MetaDataPropertyQueueNameResolver("default").resolveQueueName(cluster1);
        assertEquals("clusterName", actual1);
    }

    @Test
    public void testNoMetaDataIsProvided_DefaultMetaDataProperties() {
        Cluster cluster1 = new SimpleCluster();

        String actual1 = new MetaDataPropertyQueueNameResolver("default").resolveQueueName(cluster1);
        assertEquals("default", actual1);
    }

    @Test
    public void testMatchOnFirstCustomMetaDataProperty() {
        Cluster cluster1 = new SimpleCluster();
        cluster1.getMetaData().setProperty("first", "one");
        cluster1.getMetaData().setProperty("second", "two");

        String actual1 = new MetaDataPropertyQueueNameResolver("default", "first", "second").resolveQueueName(cluster1);
        assertEquals("one", actual1);
    }

    @Test
    public void testMatchOnSecondCustomMetaDataProperty() {
        Cluster cluster1 = new SimpleCluster();
        cluster1.getMetaData().setProperty("second", "two");

        String actual1 = new MetaDataPropertyQueueNameResolver("default", "first", "second").resolveQueueName(cluster1);
        assertEquals("two", actual1);
    }
}
