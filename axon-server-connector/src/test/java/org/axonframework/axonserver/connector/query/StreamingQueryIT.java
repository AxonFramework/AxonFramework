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
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryBusTestUtils;
import org.axonframework.messaging.queryhandling.QueryExecutionException;
import org.axonframework.messaging.queryhandling.QueryHandlingComponent;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.annotation.AnnotatedQueryHandlingComponent;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBus;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBusConfiguration;
import org.axonframework.messaging.queryhandling.distributed.PayloadConvertingQueryBusConnector;
import org.axonframework.conversion.PassThroughConverter;
import org.axonframework.conversion.json.JacksonConverter;
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
import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.axonframework.messaging.core.FluxUtils.streamToPublisher;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for Streaming Query functionality. They include backwards compatibility end-to-end tests as well.
 */
@Testcontainers
@Disabled("#3488 - This test is failing, but possibly just because of setup.")
class StreamingQueryIT {

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };
    private static final int HTTP_PORT = 8024;
    private static final int GRPC_PORT = 8124;
    private static final String HOSTNAME = "localhost";

    private static String axonServerAddress;
    private static String nonStreamingAxonServerAddress;

    private DistributedQueryBus senderQueryBus;
    private DistributedQueryBus nonStreamingSenderQueryBus;

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

        DistributedQueryBus handlerQueryBus = axonServerQueryBus(QueryBusTestUtils.aQueryBus(), axonServerAddress);
        senderQueryBus = axonServerQueryBus(senderLocalSegment, axonServerAddress);

        DistributedQueryBus nonStreamingHandlerQueryBus =
                axonServerQueryBus(QueryBusTestUtils.aQueryBus(), nonStreamingAxonServerAddress);
        nonStreamingSenderQueryBus =
                axonServerQueryBus(senderLocalSegment, nonStreamingAxonServerAddress);

        MyQueryHandler handler = new MyQueryHandler();
        QueryHandlingComponent queryHandlingComponent = new AnnotatedQueryHandlingComponent<>(
                handler,
                ClasspathParameterResolverFactory.forClass(handler.getClass()),
                ClasspathHandlerDefinition.forClass(handler.getClass()),
                new AnnotationMessageTypeResolver(),
                new DelegatingMessageConverter(PassThroughConverter.INSTANCE)
        );
        handlerQueryBus.subscribe(queryHandlingComponent);
        nonStreamingHandlerQueryBus.subscribe(queryHandlingComponent);
    }


    private DistributedQueryBus axonServerQueryBus(QueryBus localSegment, String axonServerAddress) {
        var axonServerQueryBusConnector = new AxonServerQueryBusConnector(
                connectionManager(axonServerAddress).getConnection(),
                configuration(axonServerAddress)
        );
        var payloadConvertingQueryBusConnector = new PayloadConvertingQueryBusConnector(
                axonServerQueryBusConnector,
                new DelegatingMessageConverter(new JacksonConverter()),
                byte[].class
        );
        return new DistributedQueryBus(
                localSegment,
                payloadConvertingQueryBusConnector,
                DistributedQueryBusConfiguration.DEFAULT
        );
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
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(FluxQuery.class), new FluxQuery()
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
                                        new GenericQueryMessage(new MessageType(FluxQuery.class),
                                                                new FluxQuery()),
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
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(ErrorFluxQuery.class), new ErrorFluxQuery()
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, supportsStreaming))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().equals("oops"))
                    .verify();
    }

    @Test
    void streamingHandlerErrorFluxQuery() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(HandlerErrorFluxQuery.class), new HandlerErrorFluxQuery()
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, true))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().startsWith("Error starting stream"))
                    .verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void streamingListQuery(boolean supportsStreaming) {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery()
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class, supportsStreaming))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void listQuery(boolean supportsStreaming) throws Throwable {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery()
        );

        assertEquals(asList("a", "b", "c", "d"), directQueryPayload(testQuery, LIST_OF_STRINGS, supportsStreaming));
    }

    private <R> Flux<R> streamingQueryPayloads(QueryMessage query, Class<R> cls, boolean supportsStreaming) {
        Function<QueryBus, Flux<R>> streamingQuery = queryBus -> Flux.from(streamToPublisher(
                () -> queryBus.query(query, null))
        ).mapNotNull(m -> m.payloadAs(cls));

        return supportsStreaming
                ? streamingQuery.apply(senderQueryBus)
                : streamingQuery.apply(nonStreamingSenderQueryBus);
    }

    private <R> R directQueryPayload(QueryMessage query, TypeReference<R> type, boolean supportsStreaming)
            throws Throwable {
        MessageStream<QueryResponseMessage> response = supportsStreaming
                ? senderQueryBus.query(query, null)
                : nonStreamingSenderQueryBus.query(query, null);
        return response.first()
                       .asCompletableFuture()
                       .thenApply(MessageStream.Entry::message)
                       .thenApply(responseMessage -> responseMessage.payloadAs(type))
                       .get();
    }

    private static class MyQueryHandler {

        @QueryHandler
        public Flux<String> handle(FluxQuery query) {
            return Flux.range(0, 1000).map(Objects::toString);
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
