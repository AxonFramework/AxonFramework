package org.axonframework.axonserver.connector.query.subscription;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.*;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class AxonServerSubscriptionQueryIntegrationTest {

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
        queryBus = AxonServerQueryBus.builder()
                                     .axonServerConnectionManager(AxonServerConnectionManager.builder()
                                                                                             .axonServerConfiguration(axonServerConfiguration)
                                                                                             .routingServers(axonServer.getAxonServerAddress()).build())
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
    void testSubscriptionQueryIsCompatibleWithNewConnectorApi() throws Exception {
        queryBus.subscribe("test", String.class, (MessageHandler<QueryMessage<?, String>>) message -> "hello, world");

        Thread.sleep(1000);

        CompletableFuture<QueryResponseMessage<String>> qr = queryBus.query(new GenericQueryMessage<>("Welcome", "test", ResponseTypes.instanceOf(String.class)));
        assertEquals("hello, world", qr.get().getPayload());

        try (SubscriptionQueryResult<? extends ResultMessage<String>, ? extends ResultMessage<String>> result = queryBus.subscriptionQuery(new GenericSubscriptionQueryMessage<>("Welcome", "test", ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class)))) {

            // the old api had an issue that subscription queries could not retrieve the initial result when the update stream was closed
            StepVerifier.create(result.initialResult().map(Message::getPayload)).expectNext("hello, world").expectComplete().verify(Duration.ofSeconds(2));

            queryBus.queryUpdateEmitter().emit(msg -> "test".equals(msg.getQueryName()), "Update");
            queryBus.queryUpdateEmitter().complete(msg -> true);

            StepVerifier.create(result.updates().map(Message::getPayload)).expectNext("Update").expectComplete().verify(Duration.ofSeconds(2));
        }
    }
}
