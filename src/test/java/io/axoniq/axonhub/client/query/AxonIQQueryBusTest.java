/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.query;

import io.axoniq.axonhub.client.AxonIQPlatformConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Author: marc
 */
public class AxonIQQueryBusTest {
    private AxonIQQueryBus queryBus;
    private DummyMessagePlatformServer dummyMessagePlatformServer;


    @Before
    public void setup() throws Exception {
        AxonIQPlatformConfiguration conf = new AxonIQPlatformConfiguration();
        conf.setRoutingServers("localhost:4343");
        conf.setClientName("JUnit");
        conf.setComponentName("JUnit");
        conf.setInitialNrOfPermits(100);
        conf.setNewPermitsThreshold(10);
        conf.setNrOfNewPermits(1000);
        QueryBus localSegment = new SimpleQueryBus();
        Serializer ser = new XStreamSerializer();
        queryBus = new AxonIQQueryBus(new PlatformConnectionManager(conf), conf, localSegment, ser, new QueryPriorityCalculator() {});
        dummyMessagePlatformServer = new DummyMessagePlatformServer(4343);
        dummyMessagePlatformServer.start();
    }

    @After
    public void tearDown() throws Exception {
        dummyMessagePlatformServer.stop();
    }

    @Test
    public void subscribe() throws Exception {
        Registration response = queryBus.subscribe("testQuery", String.class, q -> "test");
        Thread.sleep(1000);
        assertEquals(1, dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()).size());

        response.cancel();
        Thread.sleep(100);
        assertEquals(0, dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()).size());
    }

    @Test
    public void query() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        assertEquals("test", queryBus.query(queryMessage).get());
    }

    @Test
    public void queryAll() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class)
                .andMetaData(MetaData.with("repeat", 10).and("interval", 10));

        assertEquals(10, queryBus.queryAll(queryMessage, 2, TimeUnit.SECONDS).count());
    }

    @Test
    public void queryAllTimeout() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class)
                .andMetaData(MetaData.with("repeat", 10).and("interval", 100));

        assertTrue(8 > queryBus.queryAll(queryMessage, 550, TimeUnit.MILLISECONDS).count());
    }


}