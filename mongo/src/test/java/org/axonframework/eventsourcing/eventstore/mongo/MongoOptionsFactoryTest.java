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

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.MongoOptions;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

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
        MongoOptions options = factory.createMongoOptions();
        MongoOptions defaults = new MongoOptions();

        assertEquals(defaults.autoConnectRetry, options.autoConnectRetry);
        assertEquals(defaults.maxWaitTime, options.maxWaitTime);
        assertEquals(defaults.socketTimeout, options.socketTimeout);
        assertEquals(defaults.connectionsPerHost, options.connectionsPerHost);
        assertEquals(defaults.connectTimeout, options.connectTimeout);
        assertEquals(defaults.threadsAllowedToBlockForConnectionMultiplier,
                     options.threadsAllowedToBlockForConnectionMultiplier);
    }

    @Test
    public void testCreateMongoOptions_customSet() throws Exception {
        factory.setAutoConnectRetry(true);
        factory.setConnectionsPerHost(9);
        factory.setConnectionTimeout(11);
        factory.setMaxWaitTime(3);
        factory.setSocketTimeOut(23);
        factory.setThreadsAllowedToBlockForConnectionMultiplier(31);

        MongoOptions options = factory.createMongoOptions();
        assertTrue(options.autoConnectRetry);
        assertEquals(3, options.maxWaitTime);
        assertEquals(23, options.socketTimeout);
        assertEquals(9, options.connectionsPerHost);
        assertEquals(11, options.connectTimeout);
        assertEquals(31, options.threadsAllowedToBlockForConnectionMultiplier);
    }
}
