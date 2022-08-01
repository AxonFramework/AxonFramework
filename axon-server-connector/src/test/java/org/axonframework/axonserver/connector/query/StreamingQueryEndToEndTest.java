/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.query;

import com.thoughtworks.xstream.XStream;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericStreamingQueryMessage;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
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
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for Streaming Query functionality. They include backwards compatibility end-to-end tests as well.
 */
@Testcontainers
class StreamingQueryEndToEndTest {

    private static String axonServerAddress;
    private static String nonStreamingAxonServerAddress;

    private AxonServerQueryBus senderQueryBus;
    private AxonServerQueryBus nonStreamingSenderQueryBus;

    private Registration subscription;
    private Registration nonStreamingSubscription;

    @Container
    private static final GenericContainer<?> axonServerContainer =
            new GenericContainer<>(System.getProperty("AXON_SERVER_IMAGE", "axoniq/axonserver"))
                    .withExposedPorts(8024, 8124)
                    .withEnv("AXONIQ_AXONSERVER_NAME", "axonserver")
                    .withEnv("AXONIQ_AXONSERVER_HOSTNAME", "localhost")
                    .withEnv("AXONIQ_AXONSERVER_DEVMODE_ENABLED", "true")
                    .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(1)))
                    .withNetwork(Network.newNetwork())
                    .withNetworkAliases("axonserver")
                    .waitingFor(Wait.forHttp("/actuator/health").forPort(8024));

    @Container
    private static final GenericContainer<?> nonStreamingAxonServerContainer =
            new GenericContainer<>(System.getProperty("AXON_SERVER_IMAGE", "axoniq/axonserver:4.5.10"))
                    .withExposedPorts(8024, 8124)
                    .withEnv("AXONIQ_AXONSERVER_NAME", "axonserver")
                    .withEnv("AXONIQ_AXONSERVER_HOSTNAME", "localhost")
                    .withEnv("AXONIQ_AXONSERVER_DEVMODE_ENABLED", "true")
                    .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(1)))
                    .withNetwork(Network.newNetwork())
                    .withNetworkAliases("axonserver")
                    .waitingFor(Wait.forHttp("/actuator/health").forPort(8024));

    @BeforeAll
    static void initialize() {
        axonServerAddress = axonServerContainer.getHost()
                + ":" +
                axonServerContainer.getMappedPort(8124);
        nonStreamingAxonServerAddress = nonStreamingAxonServerContainer.getHost()
                + ":" +
                nonStreamingAxonServerContainer.getMappedPort(8124);
    }

    @BeforeEach
    void setUp() {
        SimpleQueryBus handlerLocalSegment = SimpleQueryBus.builder().build();
        SimpleQueryBus senderLocalSegment = SimpleQueryBus.builder().build();

        AxonServerQueryBus handlerQueryBus = axonServerQueryBus(handlerLocalSegment, axonServerAddress);
        senderQueryBus = axonServerQueryBus(senderLocalSegment, axonServerAddress);

        AxonServerQueryBus nonStreamingHandlerQueryBus =
                axonServerQueryBus(handlerLocalSegment, nonStreamingAxonServerAddress);
        nonStreamingSenderQueryBus =
                axonServerQueryBus(senderLocalSegment, nonStreamingAxonServerAddress);

        subscription =
                new AnnotationQueryHandlerAdapter<>(new MyQueryHandler()).subscribe(handlerQueryBus);
        nonStreamingSubscription =
                new AnnotationQueryHandlerAdapter<>(new MyQueryHandler()).subscribe(nonStreamingHandlerQueryBus);
    }

    @AfterEach
    void tearDown() {
        subscription.cancel();
        nonStreamingSubscription.cancel();
    }

    private AxonServerQueryBus axonServerQueryBus(SimpleQueryBus localSegment, String axonServerAddress) {
        QueryUpdateEmitter emitter = SimpleQueryUpdateEmitter.builder().build();
        Serializer serializer = XStreamSerializer.builder()
                                                 .xStream(new XStream())
                                                 .build();
        return AxonServerQueryBus.builder()
                                 .localSegment(localSegment)
                                 .configuration(configuration(axonServerAddress))
                                 .axonServerConnectionManager(connectionManager(axonServerAddress))
                                 .updateEmitter(emitter)
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
    void testStreamingFluxQuery(boolean supportsStreaming) {
        StreamingQueryMessage<FluxQuery, String> query =
                new GenericStreamingQueryMessage<>(new FluxQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query, supportsStreaming))
                    .expectNextCount(1000)
                    .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConcurrentStreamingQueries(boolean supportsStreaming) {
        int count = 100;

        StepVerifier.create(Flux.range(0, count)
                                .flatMap(i -> streamingQueryPayloads(new GenericStreamingQueryMessage<>(new FluxQuery(), String.class), supportsStreaming)))
                    .expectNextCount(count * 1000)
                    .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testStreamingErrorFluxQuery(boolean supportsStreaming) {
        StreamingQueryMessage<ErrorFluxQuery, String> query =
                new GenericStreamingQueryMessage<>(new ErrorFluxQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query, supportsStreaming))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().equals("oops"))
                    .verify();
    }

    @Test
    void testStreamingHandlerErrorFluxQuery() {
        StreamingQueryMessage<HandlerErrorFluxQuery, String> query =
                new GenericStreamingQueryMessage<>(new HandlerErrorFluxQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query, true))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().startsWith("No suitable handler"))
                    .verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testStreamingListQuery(boolean supportsStreaming) {
        StreamingQueryMessage<ListQuery, String> query =
                new GenericStreamingQueryMessage<>(new ListQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query, supportsStreaming))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testListQuery(boolean supportsStreaming) throws ExecutionException, InterruptedException {
        QueryMessage<ListQuery, List<String>> query = new GenericQueryMessage<>(new ListQuery(),
                                                                                multipleInstancesOf(String.class));

        assertEquals(asList("a", "b", "c", "d"), directQueryPayload(query, supportsStreaming));
    }

    private <R> Flux<R> streamingQueryPayloads(StreamingQueryMessage<?, R> query, boolean supportsStreaming) {
        if (supportsStreaming) {
            return Flux.from(senderQueryBus.streamingQuery(query))
                       .map(Message::getPayload);
        }
        return Flux.from(nonStreamingSenderQueryBus.streamingQuery(query))
                   .map(Message::getPayload);
    }

    private <R> R directQueryPayload(QueryMessage<?, R> query, boolean supportsStreaming)
            throws ExecutionException, InterruptedException {
        if (supportsStreaming) {
            return senderQueryBus.query(query)
                                 .get()
                                 .getPayload();
        }
        return nonStreamingSenderQueryBus.query(query)
                                         .get()
                                         .getPayload();
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

    private static class FluxQuery {

    }

    private static class ListQuery {

    }

    private static class ErrorFluxQuery {

    }

    private static class HandlerErrorFluxQuery {

    }
}
