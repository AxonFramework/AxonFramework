/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.MongoClientOptions;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author Jettro Coenradie
 */
public class MongoOptionsFactoryTest {

    private MongoOptionsFactory factory;

    @Before
    public void setUp() throws Exception {
        factory = new MongoOptionsFactory();
    }

    @Test
    public void testCreateMongoOptions_defaults() throws Exception {
        MongoClientOptions options = factory.createMongoOptions();
        MongoClientOptions defaults = MongoClientOptions.builder().build();

        assertEquals(defaults.getMaxWaitTime(), options.getMaxWaitTime());
        assertEquals(defaults.getSocketTimeout(), options.getSocketTimeout());
        assertEquals(defaults.getConnectionsPerHost(), options.getConnectionsPerHost());
        assertEquals(defaults.getConnectTimeout(), options.getConnectTimeout());
        assertEquals(defaults.getThreadsAllowedToBlockForConnectionMultiplier(),
                options.getThreadsAllowedToBlockForConnectionMultiplier());
    }

    @Test
    public void testCreateMongoOptions_customSet() throws Exception {
        factory.setConnectionsPerHost(9);
        factory.setConnectionTimeout(11);
        factory.setMaxWaitTime(3);
        factory.setSocketTimeOut(23);
        factory.setThreadsAllowedToBlockForConnectionMultiplier(31);

        MongoClientOptions options = factory.createMongoOptions();
        assertEquals(3, options.getMaxWaitTime());
        assertEquals(23, options.getSocketTimeout());
        assertEquals(9, options.getConnectionsPerHost());
        assertEquals(11, options.getConnectTimeout());
        assertEquals(31, options.getThreadsAllowedToBlockForConnectionMultiplier());
    }
}
