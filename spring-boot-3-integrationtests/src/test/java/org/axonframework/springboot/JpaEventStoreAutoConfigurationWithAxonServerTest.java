/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.springboot;

import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.springboot.utils.GrpcServerStub;
import org.axonframework.springboot.utils.TcpUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JPA EventStore auto-configuration
 *
 * @author Sara Pellegrini
 */
class JpaEventStoreAutoConfigurationWithAxonServerTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class);
    }

    @BeforeAll
    static void beforeAll() {
        System.setProperty("axon.axonserver.servers", GrpcServerStub.DEFAULT_HOST + ":" + TcpUtils.findFreePort());
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("axon.axonserver.servers");
    }

    @Test
    void eventStoreIsOfTypeAxonServerEventStore() {
        testContext.run(context -> {
            Map<String, EventStore> eventStores = context.getBeansOfType(EventStore.class);
            assertTrue(eventStores.containsKey("eventStore"));
            assertEquals(AxonServerEventStore.class, eventStores.get("eventStore").getClass());
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class TestContext {

        @Bean(initMethod = "start", destroyMethod = "shutdown")
        public GrpcServerStub grpcServerStub(@Value("${axon.axonserver.servers}") String servers) {
            return new GrpcServerStub(Integer.parseInt(servers.split(":")[1]));
        }
    }
}
