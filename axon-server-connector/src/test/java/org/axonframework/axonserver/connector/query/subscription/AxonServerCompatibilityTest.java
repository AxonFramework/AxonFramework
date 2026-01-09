package org.axonframework.axonserver.connector.query.subscription;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating whether Axon Framework 4 is compatible with Axon Server Connector 2025.2.1, where the
 * {@link org.axonframework.messaging.responsetypes.ResponseType} is no longer of interest to the connector.
 * <p>
 * This problem specifically shows itself for the update messages of a subscription query.
 *
 * @author Allard Buijze
 */
@Testcontainers
class AxonServerCompatibilityTest {

    @Container
    public static AxonServerContainer axonServer = new AxonServerContainer()
            .withDevMode(true)
            .withReuse(true);

    private AxonServerQueryBus queryBus;

    @BeforeEach
    void setUp() {
        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(axonServer.getAxonServerAddress());
        axonServerConfiguration.setClientId("test");
        axonServerConfiguration.setSslEnabled(false);
        SimpleQueryUpdateEmitter updateEmitter = SimpleQueryUpdateEmitter.builder().build();
        AxonServerConnectionManager connectionManager =
                AxonServerConnectionManager.builder()
                                           .axonServerConfiguration(axonServerConfiguration)
                                           .routingServers(axonServer.getAxonServerAddress())
                                           .build();
        queryBus = AxonServerQueryBus.builder()
                                     .axonServerConnectionManager(connectionManager)
                                     .localSegment(SimpleQueryBus.builder().queryUpdateEmitter(updateEmitter).build())
                                     .configuration(axonServerConfiguration)
                                     .updateEmitter(updateEmitter)
                                     .genericSerializer(JacksonSerializer.defaultSerializer())
                                     .messageSerializer(JacksonSerializer.defaultSerializer())
                                     .build();
    }

    @AfterEach
    void tearDown() {
        queryBus.shutdownDispatching();
        queryBus.disconnect();
    }

    @Test
    void subscriptionQueryIsCompatibleWithNewConnectorApi() {
        //noinspection resource
        queryBus.subscribe("test", String.class, (MessageHandler<QueryMessage<?, String>>) message -> "hello, world");
        QueryMessage<String, String> directQuery =
                new GenericQueryMessage<>("Welcome", "test", ResponseTypes.instanceOf(String.class));
        await().pollDelay(Duration.ofMillis(50))
               .untilAsserted(() -> {
                   CompletableFuture<QueryResponseMessage<String>> qr = queryBus.query(directQuery);
                   assertEquals("hello, world", qr.get().getPayload());
               });

        SubscriptionQueryMessage<String, String, String> subscriptionQuery = new GenericSubscriptionQueryMessage<>(
                "Welcome", "test", ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class)
        );
        try (SubscriptionQueryResult<? extends ResultMessage<String>, ? extends ResultMessage<String>> result
                     = queryBus.subscriptionQuery(subscriptionQuery)) {

            // the old api had an issue that subscription queries could not retrieve the initial result when the update stream was closed
            StepVerifier.create(result.initialResult().map(Message::getPayload))
                        .expectNext("hello, world")
                        .expectComplete().verify(Duration.ofSeconds(2));

            queryBus.queryUpdateEmitter().emit(msg -> "test".equals(msg.getQueryName()), "Update");
            queryBus.queryUpdateEmitter().complete(msg -> true);

            StepVerifier.create(result.updates().map(Message::getPayload))
                        .expectNext("Update")
                        .expectComplete()
                        .verify(Duration.ofSeconds(2));
        }
    }
}
