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

package org.axonframework.integrationtests.queryhandling;

import io.grpc.ManagedChannelBuilder;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.query.AxonServerQueryBusConnector;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QueueMessageStream;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryBusTestUtils;
import org.axonframework.messaging.queryhandling.QueryExecutionException;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBus;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBusConfiguration;
import org.axonframework.messaging.queryhandling.distributed.PayloadConvertingQueryBusConnector;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.common.util.MockException;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class QueryThreadingIntegrationTest {

    private static final MessageType QUERY_TYPE_A = new MessageType("query-a");
    private static final MessageType QUERY_TYPE_B = new MessageType("query-b");

    private static final Logger log = LoggerFactory.getLogger(QueryThreadingIntegrationTest.class);

    private static final String HOSTNAME = "localhost";
    private static final CountDownLatch secondaryQueryBlock = new CountDownLatch(1);
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
    public static final MessageType MESSAGE_TYPE_STRING = new MessageType(String.class);

    private AxonServerConnectionManager connectionManager;
    private AxonServerQueryBusConnector connector;
    private DistributedQueryBus queryBus1;
    private AxonServerQueryBusConnector connector2;
    private DistributedQueryBus queryBus2;
    private JacksonConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JacksonConverter();
        var messageConverter = new DelegatingMessageConverter(converter);

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
        QueryBus localQueryBus = QueryBusTestUtils.aQueryBus();
        connector = new AxonServerQueryBusConnector(connectionManager.getConnection(), configuration);
        DistributedQueryBusConfiguration queryBusConfig = new DistributedQueryBusConfiguration().queryThreads(5);
        queryBus1 = new DistributedQueryBus(localQueryBus,
                                            new PayloadConvertingQueryBusConnector(connector,
                                                                                   messageConverter,
                                                                                   byte[].class),
                                            queryBusConfig);
        connector.start();

        // The secondary application
        QueryBus localQueryBus2 = QueryBusTestUtils.aQueryBus();
        connector2 = new AxonServerQueryBusConnector(connectionManager.getConnection(), configuration);
        queryBus2 = new DistributedQueryBus(localQueryBus2,
                                            new PayloadConvertingQueryBusConnector(connector2,
                                                                                   messageConverter,
                                                                                   byte[].class),
                                            queryBusConfig);
        connector2.start();
        waitingQueries.set(0);
    }

    @AfterEach
    void tearDown() {
        connector.shutdownDispatching();
        connector.disconnect();
        connector2.shutdownDispatching();
        connector2.disconnect();

        connectionManager.shutdown();
    }

    @Test
    void canSendQueryAndReceiveSingleResponse() {
        queryBus1.subscribe(QUERY_TYPE_A.qualifiedName(),
                            (query, ctx) -> MessageStream.just(new GenericQueryResponseMessage(
                                    MESSAGE_TYPE_STRING,
                                    "a")));

        var result = queryBus2.query(new GenericQueryMessage(QUERY_TYPE_A, "start"),
                                     null);
        await().until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("a");
        await().until(result::isCompleted);
    }

    @Test
    void canSendSubscriptionQuery() {
        queryBus1.subscribe(QUERY_TYPE_A.qualifiedName(),
                            (query, ctx) -> MessageStream.just(new GenericQueryResponseMessage(
                                    MESSAGE_TYPE_STRING,
                                    "a")));

        var result = queryBus2.subscriptionQuery(new GenericQueryMessage(QUERY_TYPE_A,
                                                                         "start"),
                                                 null, 16);
        await().until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("a");

        // this means we have the initial result. Let's send some update
        queryBus1.emitUpdate(m -> true,
                             () -> new GenericSubscriptionQueryUpdateMessage(MESSAGE_TYPE_STRING, "u1"),
                             null);
        queryBus1.completeSubscriptions(m -> true, null);

        // and check for these update to arrive
        await().atMost(Duration.ofSeconds(1)).until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("u1");
        await().atMost(Duration.ofSeconds(1)).until(result::isCompleted);
    }

    @Test
    void canSendSubscriptionQueryWithFailingInitialResponses() {
        queryBus1.subscribe(QUERY_TYPE_A.qualifiedName(),
                            (query, ctx) -> MessageStream.failed(new MockException("Simulating failure")));

        var result = queryBus2.subscriptionQuery(new GenericQueryMessage(QUERY_TYPE_A,
                                                                         "start"),
                                                 null, 16);
        await().atMost(3, TimeUnit.SECONDS).until(result::isCompleted);
        assertThat(result.error()).isPresent().get().isInstanceOf(QueryExecutionException.class).matches(
                e -> e.getMessage().contains("Simulating failure")
        );
    }

    @Test
    void canSendSubscriptionQueryWithMultipleInitialResponses() {
        queryBus1.subscribe(QUERY_TYPE_A.qualifiedName(),
                            (query, ctx) -> MessageStream.fromItems(new GenericQueryResponseMessage(
                                    MESSAGE_TYPE_STRING,
                                    "a1"), new GenericQueryResponseMessage(
                                    MESSAGE_TYPE_STRING,
                                    "a2")));

        var result = queryBus2.subscriptionQuery(new GenericQueryMessage(QUERY_TYPE_A,
                                                                         "start"),
                                                 null, 16);
        await().until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("a1");
        // this means we have the first initial result. Let's send some update
        queryBus1.emitUpdate(m -> true,
                             () -> new GenericSubscriptionQueryUpdateMessage(MESSAGE_TYPE_STRING, "u1"),
                             null);
        await().until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("a2");
        // this means we have all the initial results. Let's send some more update
        queryBus1.emitUpdate(m -> true,
                             () -> new GenericSubscriptionQueryUpdateMessage(MESSAGE_TYPE_STRING, "u2"),
                             null);
        queryBus1.completeSubscriptions(m -> true, null);

        // and check for these update to arrive
        await().atMost(Duration.ofSeconds(1)).until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("u1");
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("u2");
        await().atMost(Duration.ofSeconds(1)).until(result::isCompleted);
    }

    @Test
    void canSendQueryAndReceiveStreamingResponse() {
        QueueMessageStream<QueryResponseMessage> queryResponse = new QueueMessageStream<>();
        queryBus1.subscribe(QUERY_TYPE_A.qualifiedName(),
                            (query, ctx) -> queryResponse
        );
        var result = queryBus2.query(new GenericQueryMessage(QUERY_TYPE_A, "start"), null);
        assertThat(result.hasNextAvailable()).isFalse();
        queryResponse.offer(new GenericQueryResponseMessage(MESSAGE_TYPE_STRING, "a"), Context.empty());

        await().until(result::hasNextAvailable);
        assertThat(result.next()).isPresent()
                                 .get()
                                 .extracting(this::messagePayloadAsString)
                                 .isEqualTo("a");

        assertThat(result.hasNextAvailable()).isFalse();
        assertThat(result.isCompleted()).isFalse();
        queryResponse.offer(new GenericQueryResponseMessage(MESSAGE_TYPE_STRING, "c"), Context.empty());
        queryResponse.complete();
        await().until(result::hasNextAvailable);
        await().untilAsserted(() -> {
            assertThat(result.next()).isPresent()
                                     .get()
                                     .extracting(this::messagePayloadAsString)
                                     .isEqualTo("c");
            assertThat(result.isCompleted()).isTrue();
        });
    }

    @Test
    void canStillHandleQueryResponsesWhileManyQueriesHandling() {
        queryBus2.subscribe(QUERY_TYPE_B.qualifiedName(), (query, ctx) -> {
            try {
                secondaryQueryBlock.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MessageStream.just(new GenericQueryResponseMessage(MESSAGE_TYPE_STRING, "b"));
        });

        queryBus1.subscribe(QUERY_TYPE_A.qualifiedName(), (query, ctx) -> {
            waitingQueries.incrementAndGet();
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE_B,
                                                             "start");
            try {
                QueryResponseMessage b = queryBus1.query(testQuery, null)
                                                  .first()
                                                  .asCompletableFuture()
                                                  .thenApply(MessageStream.Entry::message)
                                                  .get();
                return MessageStream.just(new GenericQueryResponseMessage(MESSAGE_TYPE_STRING,
                                                                          "a" + b.payload()))
                                    .onClose(waitingQueries::decrementAndGet)
                                    .cast();
            } catch (InterruptedException | ExecutionException e) {
                waitingQueries.decrementAndGet();
                return MessageStream.failed(e);
            }
        });

        MessageStream<QueryResponseMessage> query1 = queryBus1.query(
                new GenericQueryMessage(QUERY_TYPE_A, "start"), null
        );
        MessageStream<QueryResponseMessage> query2 = queryBus1.query(
                new GenericQueryMessage(QUERY_TYPE_A, "start"), null
        );
        MessageStream<QueryResponseMessage> query3 = queryBus1.query(
                new GenericQueryMessage(QUERY_TYPE_A, "start"), null
        );
        MessageStream<QueryResponseMessage> query4 = queryBus1.query(
                new GenericQueryMessage(QUERY_TYPE_A, "start"), null
        );
        MessageStream<QueryResponseMessage> query5 = queryBus1.query(
                new GenericQueryMessage(QUERY_TYPE_A, "start"), null
        );
        MessageStream<QueryResponseMessage> query6 = queryBus1.query(
                new GenericQueryMessage(QUERY_TYPE_A, "start"), null
        );

        // Wait until all queries are waiting on the secondary query. With 5 threads, we expect exactly 5 to be
        // triggered while the 6th is waiting for an available thread.
        await().pollDelay(500, TimeUnit.MILLISECONDS)
               .atMost(10, TimeUnit.SECONDS)
               .until(() -> {
                   log.info("Waiting queries: {}", waitingQueries.get());
                   return waitingQueries.get() == 5;
               });

        // We should still have the queries not done, it's waiting on the secondary one.
        assertFalse(query1.hasNextAvailable());
        assertFalse(query2.hasNextAvailable());
        assertFalse(query3.hasNextAvailable());
        assertFalse(query4.hasNextAvailable());
        assertFalse(query5.hasNextAvailable());
        assertFalse(query6.hasNextAvailable());

        // unblock the query, it should now process all queries
        secondaryQueryBlock.countDown();

        await().atMost(5, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   assertEquals(0, waitingQueries.get());
                   assertTrue(query1.hasNextAvailable());
                   assertTrue(query2.hasNextAvailable());
                   assertTrue(query3.hasNextAvailable());
                   assertTrue(query4.hasNextAvailable());
                   assertTrue(query5.hasNextAvailable());
                   assertTrue(query6.hasNextAvailable());
               });
    }

    private String messagePayloadAsString(MessageStream.Entry<QueryResponseMessage> entry) {
        return entry.message().payloadAs(String.class, converter);
    }
}
