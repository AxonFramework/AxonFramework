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

package org.axonframework.integrationtests.queryhandling;

import io.grpc.ManagedChannelBuilder;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class QueryThreadingIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(QueryThreadingIntegrationTest.class);

    private static final String HOSTNAME = "localhost";
    private static final AtomicBoolean secondaryQueryBlock = new AtomicBoolean(true);
    private static final AtomicInteger waitingQueries = new AtomicInteger(0);

    @Container
    private static final AxonServerContainer axonServer =
            new AxonServerContainer()
                    .withAxonServerName("axonserver")
                    .withAxonServerHostname(HOSTNAME)
                    .withDevMode(true)
                    .withEnv("AXONIQ_AXONSERVER_INSTRUCTION-CACHE-TIMEOUT", "1000")
                    .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(1)))
                    .withNetworkAliases("axonserver");

    private AxonServerConnectionManager connectionManager;
    private AxonServerQueryBus queryBus;
    private AxonServerQueryBus queryBus2;

    @BeforeEach
    void setUp() {
        Serializer serializer = JacksonSerializer.defaultSerializer();

        String server = axonServer.getHost() + ":" + axonServer.getGrpcPort();
        AxonServerConfiguration configuration = AxonServerConfiguration.builder()
                .componentName("threadingTest")
                .servers(server)
                .build();
        configuration.setCommandThreads(5);
        configuration.setQueryThreads(5);
        configuration.setQueryResponseThreads(5);
        connectionManager = AxonServerConnectionManager.builder()
                .axonServerConfiguration(configuration)
                .channelCustomizer(ManagedChannelBuilder::directExecutor)
                .build();
        connectionManager.start();


        // The application having a query that depends on another one
        QueryBus localQueryBus = SimpleQueryBus.builder().build();
        queryBus = AxonServerQueryBus.builder()
                .axonServerConnectionManager(connectionManager)
                .configuration(configuration)
                .localSegment(localQueryBus)
                .updateEmitter(localQueryBus.queryUpdateEmitter())
                .messageSerializer(serializer)
                .genericSerializer(serializer)
                .build();
        queryBus.start();

        // The secondary application
        QueryBus localQueryBus2 = SimpleQueryBus.builder().build();
        queryBus2 = AxonServerQueryBus.builder()
                .axonServerConnectionManager(connectionManager)
                .configuration(configuration)
                .localSegment(localQueryBus2)
                .updateEmitter(localQueryBus.queryUpdateEmitter())
                .messageSerializer(serializer)
                .genericSerializer(serializer)
                .build();
        queryBus2.start();
        waitingQueries.set(0);
    }

    @AfterEach
    void tearDown() {
        queryBus.shutdownDispatching();
        queryBus.disconnect();
        queryBus2.shutdownDispatching();
        queryBus2.disconnect();

        connectionManager.shutdown();
    }

    @SuppressWarnings("resource")
    @Test
    void canStillHandleQueryResponsesWhileManyQueriesHandling() throws InterruptedException {
        queryBus2.subscribe("query-b", String.class, query -> {
            while (secondaryQueryBlock.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "b";
        });

        queryBus.subscribe("query-a", String.class, query -> {
            waitingQueries.incrementAndGet();
            QueryResponseMessage<String> b = queryBus.query(new GenericQueryMessage<>("start", "query-b", ResponseTypes.instanceOf(String.class))).get();
            waitingQueries.decrementAndGet();
            return "a" + b.getPayload();
        });


        CompletableFuture<QueryResponseMessage<String>> query1 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query2 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query3 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query4 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query5 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query6 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));

        // Wait until all queries are waiting on the secondary query. Note that query 6 cannot be processed
        await().pollDelay(500, TimeUnit.MILLISECONDS)
               .atMost(10, TimeUnit.SECONDS)
               .until(() -> {
                   log.info("Waiting queries: {}", waitingQueries.get());
                   return waitingQueries.get() == 5;
               });

        // We should still have the queries not done, it's waiting on the secondary one.
        assertFalse(query1.isDone());
        assertFalse(query2.isDone());
        assertFalse(query3.isDone());
        assertFalse(query4.isDone());
        assertFalse(query5.isDone());
        assertFalse(query6.isDone());

        // unblock the query, it should now process all queries
        secondaryQueryBlock.set(false);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Assertions.assertEquals(0, waitingQueries.get());
                    assertTrue(query1.isDone());
                    assertTrue(query2.isDone());
                    assertTrue(query3.isDone());
                    assertTrue(query4.isDone());
                    assertTrue(query5.isDone());
                    assertTrue(query6.isDone());
                });
    }

}
