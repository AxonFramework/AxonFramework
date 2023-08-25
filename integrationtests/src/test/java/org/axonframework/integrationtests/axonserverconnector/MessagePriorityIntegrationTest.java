/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.integrationtests.axonserverconnector;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.grpc.ManagedChannelBuilder;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating whether the provided message priority for commands and queries is respected. These priorities
 * are typically defined by the {@link CommandPriorityCalculator} and {@link QueryPriorityCalculator} for commands and
 * queries respectively.
 *
 * @author Steven van Beelen
 */
@Testcontainers
class MessagePriorityIntegrationTest {

    private static final String HOSTNAME = "localhost";

    private static final int PRIORITY = 42;
    private static final int REGULAR = 0;

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
                                                                       .componentName("messagePriority")
                                                                       .servers(server)
                                                                       .build();
        configuration.setCommandThreads(1);
        configuration.setQueryThreads(1);
        connectionManager = AxonServerConnectionManager.builder()
                                                       .axonServerConfiguration(configuration)
                                                       .channelCustomizer(ManagedChannelBuilder::directExecutor)
                                                       .build();
        connectionManager.start();

        CommandPriorityCalculator commandPriorityCalculator =
                command -> Objects.equals(command.getPayloadType(), PriorityMessage.class) ? PRIORITY : REGULAR;
        CommandBus localCommandBus = SimpleCommandBus.builder().build();
        commandBus = AxonServerCommandBus.builder()
                                         .axonServerConnectionManager(connectionManager)
                                         .configuration(configuration)
                                         .localSegment(localCommandBus)
                                         .serializer(serializer)
                                         .routingStrategy(AnnotationRoutingStrategy.defaultStrategy())
                                         .priorityCalculator(commandPriorityCalculator)
                                         .build();
        commandBus.start();

        QueryPriorityCalculator queryPriorityCalculator =
                query -> Objects.equals(query.getPayloadType(), PriorityMessage.class) ? PRIORITY : REGULAR;
        QueryBus localQueryBus = SimpleQueryBus.builder().build();
        queryBus = AxonServerQueryBus.builder()
                                     .axonServerConnectionManager(connectionManager)
                                     .configuration(configuration)
                                     .localSegment(localQueryBus)
                                     .updateEmitter(localQueryBus.queryUpdateEmitter())
                                     .messageSerializer(serializer)
                                     .genericSerializer(serializer)
                                     .priorityCalculator(queryPriorityCalculator)
                                     .build();
        queryBus.start();
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
    void commandPriorityIsRespectedWithinThresholdByAxonServerCommandBus() throws InterruptedException {
        int numberOfCommands = 10;
        // No priority command should occur in the last fifth part of all dispatched commands.
        // Quite some lenience is given to account for thread ordering within the buses.
        int priorityThreshold = numberOfCommands - (numberOfCommands / 5);

        Queue<Handled> handlingOrder = new ConcurrentLinkedQueue<>();
        CountDownLatch processingGate = new CountDownLatch(1);
        CountDownLatch finishedGate = new CountDownLatch(numberOfCommands);

        commandBus.subscribe("processGate", command -> {
            processingGate.await();
            return "start-processing";
        });
        commandBus.subscribe("regular", command -> "regular");
        commandBus.subscribe("priority", command -> "priority");

        Thread dispatcher = new Thread(() -> {
            commandBus.dispatch(new GenericCommandMessage<>(asCommandMessage("start"), "processGate"));
            for (int i = 0; i < numberOfCommands; i++) {
                GenericCommandMessage<Object> command;
                if (i % 5 == 0) {
                    command = new GenericCommandMessage<>(asCommandMessage(new PriorityMessage(Integer.toString(i))),
                                                          "priority");
                } else {
                    command = new GenericCommandMessage<>(asCommandMessage(new RegularMessage(Integer.toString(i))),
                                                          "regular");
                }
                commandBus.dispatch(command, (c, r) -> {
                    if (r.getPayload().equals("regular")) {
                        handlingOrder.add(Handled.regular());
                    } else {
                        handlingOrder.add(Handled.priority());
                    }
                    finishedGate.countDown();
                });
            }
            processingGate.countDown();
        });
        dispatcher.start();

        assertTrue(finishedGate.await(2, TimeUnit.SECONDS),
                   () -> "Failed with [" + finishedGate.getCount() + "] unprocessed command(s).");

        assertEquals(numberOfCommands, handlingOrder.size());
        for (int i = 0; i < handlingOrder.size(); i++) {
            Handled handled = handlingOrder.poll();
            if (i >= priorityThreshold) {
                assertFalse(handled.priority,
                            "A priority command was handled at index [" + i + "], "
                                    + "while it is at least expected to come before [" + priorityThreshold + "].");
            }
        }

        dispatcher.join(1000);
    }

    @SuppressWarnings("resource")
    @Test
    void queryPriorityIsRespectedWithinThresholdByAxonServerQueryBus() throws InterruptedException {
        int numberOfQueries = 10;
        // No priority query should occur in the last fifth part of all dispatched queries.
        // Quite some lenience is given to account for thread ordering within the buses.
        int priorityThreshold = numberOfQueries - (numberOfQueries / 5);

        Queue<Handled> handlingOrder = new ConcurrentLinkedQueue<>();
        CountDownLatch processingGate = new CountDownLatch(1);
        CountDownLatch finishedGate = new CountDownLatch(numberOfQueries);

        queryBus.subscribe("processGate", String.class, query -> {
            processingGate.await();
            return "start-processing";
        });
        queryBus.subscribe("regular", String.class, query -> "regular");
        queryBus.subscribe("priority", String.class, query -> "priority");

        Thread dispatcher = new Thread(() -> {
            queryBus.query(new GenericQueryMessage<>("start", "processGate", ResponseTypes.instanceOf(String.class)));
            for (int i = 0; i < numberOfQueries; i++) {
                GenericQueryMessage<?, String> query;
                if (i % 5 == 0) {
                    query = new GenericQueryMessage<>(
                            new PriorityMessage(Integer.toString(i)), "priority", ResponseTypes.instanceOf(String.class)
                    );
                } else {
                    query = new GenericQueryMessage<>(
                            new RegularMessage(Integer.toString(i)), "regular", ResponseTypes.instanceOf(String.class)
                    );
                }
                CompletableFuture<QueryResponseMessage<String>> result = queryBus.query(query);
                result.whenComplete((response, ex) -> {
                    if (response.getPayload().equals("regular")) {
                        handlingOrder.add(Handled.regular());
                    } else {
                        handlingOrder.add(Handled.priority());
                    }
                    finishedGate.countDown();
                });
            }
            processingGate.countDown();
        });
        dispatcher.start();

        assertTrue(finishedGate.await(2, TimeUnit.SECONDS),
                   () -> "Failed with [" + finishedGate.getCount() + "] unprocessed query/queries");

        assertEquals(numberOfQueries, handlingOrder.size());
        for (int i = 0; i < handlingOrder.size(); i++) {
            Handled handled = handlingOrder.poll();
            if (i >= priorityThreshold) {
                assertFalse(handled.priority,
                            "A priority command was handled at index [" + i + "], "
                                    + "while it is at least expected to come before [" + priorityThreshold + "].");
            }
        }

        dispatcher.join(1000);
    }

    private static class Handled {

        private final boolean priority;

        private static Handled priority() {
            return new Handled(true);
        }

        private static Handled regular() {
            return new Handled(false);
        }

        private Handled(boolean priority) {
            this.priority = priority;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Handled handled = (Handled) o;
            return priority == handled.priority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(priority);
        }

        @Override
        public String toString() {
            return priority ? "P" : "R";
        }
    }

    @SuppressWarnings("unused")
    private static class RegularMessage {

        private final String text;

        public RegularMessage(@JsonProperty("text") String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    @SuppressWarnings("unused")
    private static class PriorityMessage {

        private final String text;

        public PriorityMessage(@JsonProperty("text") String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
