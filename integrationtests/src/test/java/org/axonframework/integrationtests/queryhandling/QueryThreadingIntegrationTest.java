package org.axonframework.integrationtests.queryhandling;

import io.grpc.ManagedChannelBuilder;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class QueryThreadingIntegrationTest {

    private static final String HOSTNAME = "localhost";
    private static AtomicBoolean secondaryQueryBlock = new AtomicBoolean(true);

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
    private AxonServerCommandBus commandBus;
    private AxonServerQueryBus queryBus;

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

        queryBus.subscribe("query-b", String.class, query -> {
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
            QueryResponseMessage<String> b = queryBus.query(new GenericQueryMessage<>("start", "query-b", ResponseTypes.instanceOf(String.class))).get();
            return "a" + b.getPayload();
        });
    }

    @AfterEach
    void tearDown() {
        commandBus.shutdownDispatching();
        queryBus.shutdownDispatching();

        commandBus.disconnect();
        queryBus.disconnect();

        connectionManager.shutdown();
    }

    @SuppressWarnings("resource")
    @Test
    void canStillHandleQueryResponsesWhileManyQueriesHandling() throws InterruptedException {

        CompletableFuture<QueryResponseMessage<String>> query1 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query2 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query3 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query4 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query5 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));
        CompletableFuture<QueryResponseMessage<String>> query6 = queryBus.query(new GenericQueryMessage<>("start", "query-a", ResponseTypes.instanceOf(String.class)));

        Thread.sleep(500);
        // We should still have the queries not done, it's waiting on the secondary one.
        assertFalse(query1.isDone());
        assertFalse(query2.isDone());
        assertFalse(query3.isDone());
        assertFalse(query4.isDone());
        assertFalse(query5.isDone());
        assertFalse(query6.isDone());

        secondaryQueryBlock.set(false);

        await().untilAsserted(() -> {
            assertTrue(query1.isDone());
            assertTrue(query2.isDone());
            assertTrue(query3.isDone());
            assertTrue(query4.isDone());
            assertTrue(query5.isDone());
            assertTrue(query6.isDone());
        });
    }

}
