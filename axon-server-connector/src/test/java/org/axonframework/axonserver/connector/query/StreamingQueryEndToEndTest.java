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
import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class StreamingQueryEndToEndTest {

    private static String axonServerAddress;

    private AxonServerQueryBus senderQueryBus;

    private Registration subscription;

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

    @BeforeAll
    static void initialize() {
        axonServerAddress = axonServerContainer.getContainerIpAddress()
                + ":" +
                axonServerContainer.getMappedPort(8124);
    }

    @BeforeEach
    void setUp() {
        SimpleQueryBus handlerLocalSegment = SimpleQueryBus.builder().build();
        SimpleQueryBus senderLocalSegment = SimpleQueryBus.builder().build();

        QueryUpdateEmitter emitter = SimpleQueryUpdateEmitter.builder().build();
        Serializer serializer = XStreamSerializer.builder()
                                                 .xStream(new XStream())
                                                 .build();

        AxonServerQueryBus handlerQueryBus = AxonServerQueryBus.builder()
                                                               .localSegment(handlerLocalSegment)
                                                               .configuration(handlerConfiguration())
                                                               .axonServerConnectionManager(handlerConnectionManager())
                                                               .updateEmitter(emitter)
                                                               .genericSerializer(serializer)
                                                               .messageSerializer(serializer)
                                                               .build();
        senderQueryBus = AxonServerQueryBus.builder()
                                           .localSegment(senderLocalSegment)
                                           .configuration(senderConfiguration())
                                           .axonServerConnectionManager(senderConnectionManager())
                                           .updateEmitter(emitter)
                                           .genericSerializer(serializer)
                                           .messageSerializer(serializer)
                                           .build();

        subscription = new AnnotationQueryHandlerAdapter<>(new MyQueryHandler()).subscribe(handlerQueryBus);
    }

    @AfterEach
    void tearDown() {
        subscription.cancel();
    }

    private AxonServerConnectionManager handlerConnectionManager() {
        return AxonServerConnectionManager.builder()
                                          .axonServerConfiguration(handlerConfiguration())
                                          .build();
    }

    private AxonServerConnectionManager senderConnectionManager() {
        return AxonServerConnectionManager.builder()
                                          .axonServerConfiguration(senderConfiguration())
                                          .build();
    }

    private AxonServerConfiguration handlerConfiguration() {
        return AxonServerConfiguration.builder()
                                      .componentName("Handler")
                                      .clientId("Handler")
                                      .servers(axonServerAddress)
                                      .build();
    }

    private AxonServerConfiguration senderConfiguration() {
        return AxonServerConfiguration.builder()
                                      .componentName("Sender")
                                      .clientId("Sender")
                                      .servers(axonServerAddress)
                                      .build();
    }

    @Test
    void testStreamingFluxQuery() {
        StreamingQueryMessage<FluxQuery, String> query =
                new GenericStreamingQueryMessage<>(new FluxQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query))
                    .expectNextCount(1000)
                    .verifyComplete();
    }

    @Test
    void testStreamingErrorFluxQuery() {
        StreamingQueryMessage<ErrorFluxQuery, String> query =
                new GenericStreamingQueryMessage<>(new ErrorFluxQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().equals("oops"))
                    .verify();
    }

    @Test
    void testStreamingHandlerErrorFluxQuery() {
        StreamingQueryMessage<HandlerErrorFluxQuery, String> query =
                new GenericStreamingQueryMessage<>(new HandlerErrorFluxQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().startsWith("No suitable handler"))
                    .verify();
    }

    @Test
    void testStreamingListQuery() {
        StreamingQueryMessage<ListQuery, String> query =
                new GenericStreamingQueryMessage<>(new ListQuery(), String.class);

        StepVerifier.create(streamingQueryPayloads(query))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testListQuery() throws ExecutionException, InterruptedException {
        QueryMessage<ListQuery, List<String>> query = new GenericQueryMessage<>(new ListQuery(),
                                                                                multipleInstancesOf(String.class));

        assertEquals(asList("a", "b", "c", "d"), senderQueryBus.query(query)
                                                               .get()
                                                               .getPayload());
    }

    private <R> Flux<R> streamingQueryPayloads(StreamingQueryMessage<?, R> query) {
        return Flux.from(senderQueryBus.streamingQuery(query))
                   .map(Message::getPayload);
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