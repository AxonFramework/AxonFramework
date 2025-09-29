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

package org.axonframework.axonserver.connector.query;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericStreamingQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusTestUtils;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.serialization.PassThroughConverter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for Streaming Query functionality. They include backwards compatibility end-to-end tests as well.
 */
@Disabled("TODO #3488 - Axon Server Query Bus replacement")
@Testcontainers
class StreamingQueryEndToEndTest {

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {};
    private static final int HTTP_PORT = 8024;
    private static final int GRPC_PORT = 8124;
    private static final String HOSTNAME = "localhost";

    private static String axonServerAddress;
    private static String nonStreamingAxonServerAddress;

    private AxonServerQueryBus senderQueryBus;
    private AxonServerQueryBus nonStreamingSenderQueryBus;


    @Container
    private static final AxonServerContainer axonServerContainer =
            new AxonServerContainer()
                    .withAxonServerName("axonserver")
                    .withAxonServerHostname(HOSTNAME)
                    .withDevMode(true)
                    .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(1)))
                    .withNetwork(Network.newNetwork())
                    .withNetworkAliases("axonserver");

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> nonStreamingAxonServerContainer =
            new GenericContainer<>(System.getProperty("AXON_SERVER_IMAGE", "axoniq/axonserver:4.5.10"))
                    .withExposedPorts(HTTP_PORT, GRPC_PORT)
                    .withEnv("AXONIQ_AXONSERVER_NAME", "axonserver")
                    .withEnv("AXONIQ_AXONSERVER_HOSTNAME", HOSTNAME)
                    .withEnv("AXONIQ_AXONSERVER_DEVMODE_ENABLED", "true")
                    .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(1)))
                    .withNetwork(Network.newNetwork())
                    .withNetworkAliases("axonserver")
                    .waitingFor(Wait.forHttp("/actuator/health").forPort(HTTP_PORT));

    @BeforeAll
    static void initialize() {
        axonServerAddress = axonServerContainer.getHost() + ":" + axonServerContainer.getGrpcPort();
        nonStreamingAxonServerAddress = nonStreamingAxonServerContainer.getHost()
                + ":" + nonStreamingAxonServerContainer.getMappedPort(GRPC_PORT);
    }

    @BeforeEach
    void setUp() {
        QueryBus senderLocalSegment = QueryBusTestUtils.aQueryBus();

        AxonServerQueryBus handlerQueryBus = axonServerQueryBus(QueryBusTestUtils.aQueryBus(), axonServerAddress);
        senderQueryBus = axonServerQueryBus(senderLocalSegment, axonServerAddress);

        AxonServerQueryBus nonStreamingHandlerQueryBus =
                axonServerQueryBus(QueryBusTestUtils.aQueryBus(), nonStreamingAxonServerAddress);
        nonStreamingSenderQueryBus =
                axonServerQueryBus(senderLocalSegment, nonStreamingAxonServerAddress);

        QueryHandlingComponent queryHandlingComponent =
                new AnnotatedQueryHandlingComponent<>(new MyQueryHandler(), PassThroughConverter.MESSAGE_INSTANCE);
        handlerQueryBus.subscribe(queryHandlingComponent);
        nonStreamingHandlerQueryBus.subscribe(queryHandlingComponent);
    }

    private AxonServerQueryBus axonServerQueryBus(QueryBus localSegment, String axonServerAddress) {
        Serializer serializer = JacksonSerializer.defaultSerializer();
        return AxonServerQueryBus.builder()
                                 .localSegment(localSegment)
                                 .configuration(configuration(axonServerAddress))
                                 .axonServerConnectionManager(connectionManager(axonServerAddress))
                                 .updateEmitter(null)
                                 .genericSerializer(serializer)
                                 .messageSerializer(serializer)
                                 .build();
    }

    private AxonServerConnectionManager connectionManager(String axonServerAddress) {
        return AxonServerConnectionManager.builder()
                                          .axonServerConfiguration(configuration(axonServerAddress))
                                          .build();
    }

    private AxonServerConfiguration configuration(String axonServerAddress) {
        return AxonServerConfiguration.builder()
                                      .servers(axonServerAddress)
                                      .build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamingFluxQuery(boolean supportsStreaming) {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType(FluxQuery.class), new FluxQuery(), String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, supportsStreaming))
                    .expectNextCount(1000)
                    .verifyComplete();
    }

    @Timeout(25)
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentStreamingQueries(boolean supportsStreaming) {
        int count = 100;

        StepVerifier.create(Flux.range(0, count)
                                .flatMap(i -> streamingQueryPayloads(
                                        new GenericStreamingQueryMessage(new MessageType(FluxQuery.class),
                                                                         new FluxQuery(),
                                                                         String.class),
                                        String.class,
                                        supportsStreaming
                                ))
                    )
                    .expectNextCount(count * 1000)
                    .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamingErrorFluxQuery(boolean supportsStreaming) {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType(ErrorFluxQuery.class), new ErrorFluxQuery(), String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, supportsStreaming))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().equals("oops"))
                    .verify();
    }

    @Test
    void streamingHandlerErrorFluxQuery() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType(HandlerErrorFluxQuery.class), new HandlerErrorFluxQuery(), String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, true))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().startsWith("Error starting stream"))
                    .verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamingListQuery(boolean supportsStreaming) {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(), String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, supportsStreaming))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void listQuery(boolean supportsStreaming) throws Throwable {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(), multipleInstancesOf(String.class)
        );

        assertEquals(asList("a", "b", "c", "d"), directQueryPayload(testQuery, LIST_OF_STRINGS, supportsStreaming));
    }

    private <R> Flux<R> streamingQueryPayloads(StreamingQueryMessage query, Class<R> cls, boolean supportsStreaming) {
        if (supportsStreaming) {
            return Flux.from(senderQueryBus.streamingQuery(query, null))
                       .map(m -> m.payloadAs(cls));
        }
        return Flux.from(nonStreamingSenderQueryBus.streamingQuery(query, null))
                   .map(m -> m.payloadAs(cls));
    }

    private <R> R directQueryPayload(QueryMessage query,
                                     TypeReference<R> type,
                                     boolean supportsStreaming) throws Throwable {
        MessageStream<QueryResponseMessage> response = null;
        try {
            response = supportsStreaming
                    ? senderQueryBus.query(query, null)
                    : nonStreamingSenderQueryBus.query(query, null);
            return response.first()
                           .asCompletableFuture()
                           .thenApply(MessageStream.Entry::message)
                           .thenApply(responseMessage -> responseMessage.payloadAs(type))
                           .get();
        } catch (IllegalPayloadAccessException e) {
            // TODO #3488 - Axon Server Query Bus replacement
//            if (response != null && response.optionalExceptionResult().isPresent()) {
//                throw response.optionalExceptionResult().get();
//            } else {
                throw e;
//            }
        }
    }

    private static class MyQueryHandler {

        @QueryHandler
        public Flux<String> handle(FluxQuery query) {
            return Flux.range(0, 1000)
                       .map(Objects::toString);
        }

        @QueryHandler
        public Flux<String> handle(ErrorFluxQuery query) {
            return Flux.error(new RuntimeException("oops"));
        }

        @QueryHandler
        public Flux<String> handle(HandlerErrorFluxQuery query) {
            throw new RuntimeException("oops");
        }

        @QueryHandler
        public List<String> handle(ListQuery query) {
            return asList("a", "b", "c", "d");
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class FluxQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ListQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ErrorFluxQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class HandlerErrorFluxQuery {

    }
}
