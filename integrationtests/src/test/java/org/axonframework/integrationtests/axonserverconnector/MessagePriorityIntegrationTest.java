/*
 * Copyright (c) 2010-2022. Axon Framework
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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
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
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.beans.ConstructorProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    private static final int HTTP_PORT = 8024;
    private static final int GRPC_PORT = 8124;
    private static final String HOSTNAME = "localhost";

    private static final int PRIORITY = 42;
    private static final int REGULAR = 0;

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> axonServer =
            new GenericContainer<>(DockerImageName.parse("axoniq/axonserver"))
                    .withExposedPorts(HTTP_PORT, GRPC_PORT)
                    .withEnv("AXONIQ_AXONSERVER_NAME", "axonserver")
                    .withEnv("AXONIQ_AXONSERVER_HOSTNAME", HOSTNAME)
                    .withEnv("AXONIQ_AXONSERVER_DEVMODE_ENABLED", "true")
                    .withEnv("AXONIQ_AXONSERVER_INSTRUCTION-CACHE-TIMEOUT", "1000")
                    .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(1)))
                    .withNetworkAliases("axonserver")
                    .withNetwork(Network.newNetwork())
                    .waitingFor(Wait.forHttp("/actuator/health").forPort(HTTP_PORT))
                    .waitingFor(Wait.forLogMessage(".*Started AxonServer.*", 1));

    private AxonServerConnectionManager connectionManager;
    private AxonServerCommandBus commandBus;
    private AxonServerQueryBus queryBus;

    private AtomicBoolean dispatchLatch = new AtomicBoolean(false);
    private Lock dispatchLock;

    @BeforeEach
    void setUp() {
        Serializer serializer = JacksonSerializer.defaultSerializer();
        dispatchLock = new ReentrantLock();

        String server = axonServer.getContainerIpAddress() + ":" + axonServer.getMappedPort(GRPC_PORT);
        AxonServerConfiguration configuration = AxonServerConfiguration.builder()
                                                                       .componentName("messagePriority")
                                                                       .context("test")
                                                                       .servers(server)
                                                                       .build();
        configuration.setCommandThreads(1);
        configuration.setQueryThreads(1);
        connectionManager = AxonServerConnectionManager.builder()
                                                       .axonServerConfiguration(configuration)
                                                       .channelCustomizer(ManagedChannelBuilder::directExecutor)
                .channelCustomizer(builder -> builder.intercept(new ClientInterceptor() {
                    @Override
                    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                               CallOptions callOptions, Channel channel) {
                        ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
                        dispatchLock.unlock();
                        return call;
                    }
                }))
                                                       .build();
        connectionManager.start();

        CommandPriorityCalculator commandPriorityCalculator =
                command -> Objects.equals(command.getPayloadType(), PriorityCommand.class) ? PRIORITY : REGULAR;
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
                query -> Objects.equals(query.getPayloadType(), PriorityQuery.class) ? PRIORITY : REGULAR;
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

//    @Test
//    @RepeatedTest(5)
    void testCommandPriorityAndOrderingIsRespected() throws InterruptedException {
        int numberOfCommands = 250;

        List<Handled> actualOrdering = new CopyOnWriteArrayList<>();
        List<Handled> expectedOrdering = new ArrayList<>();
        List<Handled> regularOrdering = new ArrayList<>();
        for (int i = 0; i < numberOfCommands; i++) {
            if (i % 5 == 0) {
                expectedOrdering.add(Handled.priority(i));
            } else {
                regularOrdering.add(Handled.regular(i));
            }
        }
        expectedOrdering.addAll(regularOrdering);

        CountDownLatch processingGate = new CountDownLatch(1);
        CountDownLatch finishedGate = new CountDownLatch(numberOfCommands);

        commandBus.subscribe("processGate", command -> {
            processingGate.await();
            return "start-processing";
        });
        commandBus.subscribe("regular", command -> {
            actualOrdering.add(Handled.regular(((RegularCommand) command.getPayload()).getIndex()));
            finishedGate.countDown();
            return "handled-regular";
        });
        commandBus.subscribe("priority", command -> {
            actualOrdering.add(Handled.priority(((PriorityCommand) command.getPayload()).getIndex()));
            finishedGate.countDown();
            return "handled-priority";
        });

        commandBus.dispatch(new GenericCommandMessage<>(asCommandMessage("start"), "processGate"));
        for (int i = 0; i < numberOfCommands; i++) {
            dispatchLock.lock();
            if (i % 5 == 0) {
                commandBus.dispatch(new GenericCommandMessage<>(asCommandMessage(new PriorityCommand(i)), "priority"));
            } else {
                commandBus.dispatch(new GenericCommandMessage<>(asCommandMessage(new RegularCommand(i)), "regular"));
            }
        }

        processingGate.countDown();
        //noinspection ResultOfMethodCallIgnored
        finishedGate.await(5, TimeUnit.SECONDS);

        assertEquals(numberOfCommands, expectedOrdering.size());
//        for (int i = 0; i < expectedOrdering.size(); i++) {
//            Handled expected = expectedOrdering.get(i);
//            Handled actual = actualOrdering.get(i);
//            assertEquals(expected.priority, actual.priority, "Expected [" + expected + "] & Actual [" + actual + "]");
//        }
        assertEquals(expectedOrdering, actualOrdering);
    }

//    @Test
//    @RepeatedTest(5)
    void testQueryPriorityAndOrderingIsRespected() throws InterruptedException {
        int numberOfQueries = 250;

        List<Handled> actualOrdering = new CopyOnWriteArrayList<>();
        List<Handled> expectedOrdering = new ArrayList<>();
        List<Handled> regularOrdering = new ArrayList<>();
        for (int i = 0; i < numberOfQueries; i++) {
            if (i % 5 == 0) {
                expectedOrdering.add(Handled.priority(i));
            } else {
                regularOrdering.add(Handled.regular(i));
            }
        }
        expectedOrdering.addAll(regularOrdering);

        CountDownLatch processingGate = new CountDownLatch(1);
        CountDownLatch finishedGate = new CountDownLatch(numberOfQueries);

        //noinspection resource
        queryBus.subscribe("processGate", String.class, query -> {
            processingGate.await();
            return "start-processing";
        });
        //noinspection resource
        queryBus.subscribe("regular", String.class, query -> {
            actualOrdering.add(Handled.regular(((RegularQuery) query.getPayload()).getIndex()));
            finishedGate.countDown();
            return "handled-regular";
        });
        //noinspection resource
        queryBus.subscribe("priority", String.class, query -> {
            actualOrdering.add(Handled.priority(((PriorityQuery) query.getPayload()).getIndex()));
            finishedGate.countDown();
            return "handled-priority";
        });

        queryBus.query(new GenericQueryMessage<>("start", "processGate", ResponseTypes.instanceOf(String.class)));
        for (int i = 0; i < numberOfQueries; i++) {
            if (i % 5 == 0) {
                queryBus.query(new GenericQueryMessage<>(new PriorityQuery(i),
                                                         "priority",
                                                         ResponseTypes.instanceOf(String.class)));
            } else {
                queryBus.query(new GenericQueryMessage<>(new RegularQuery(i),
                                                         "regular",
                                                         ResponseTypes.instanceOf(String.class)));
            }
        }

        processingGate.countDown();
        //noinspection ResultOfMethodCallIgnored
        finishedGate.await(5, TimeUnit.SECONDS);

        assertEquals(numberOfQueries, expectedOrdering.size());
        for (int i = 0; i < expectedOrdering.size(); i++) {
            Handled expected = expectedOrdering.get(i);
            Handled actual = actualOrdering.get(i);
            assertEquals(expected.priority, actual.priority, "Expected [" + expected + "] & Actual [" + actual + "]");
        }
//        assertEquals(expectedOrdering, actualOrdering);
    }

    private static class Handled {

        private final boolean priority;
        private final int index;

        private static Handled priority(int index) {
            return new Handled(true, index);
        }

        private static Handled regular(int index) {
            return new Handled(false, index);
        }

        private Handled(boolean priority, int index) {
            this.priority = priority;
            this.index = index;
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
            return priority == handled.priority && index == handled.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(priority, index);
        }

        @Override
        public String toString() {
            return priority ? "P[" + index + "]" : "R[" + index + "]";
        }
    }

    private static class RegularCommand {

        private final int index;

        @ConstructorProperties({"index"})
        private RegularCommand(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RegularCommand that = (RegularCommand) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }
    }

    private static class PriorityCommand {

        private final int index;

        @ConstructorProperties({"index"})
        private PriorityCommand(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PriorityCommand that = (PriorityCommand) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }
    }

    private static class RegularQuery {

        private final int index;

        @ConstructorProperties({"index"})
        private RegularQuery(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RegularQuery that = (RegularQuery) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }
    }

    private static class PriorityQuery {

        private final int index;

        @ConstructorProperties({"index"})
        private PriorityQuery(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PriorityQuery that = (PriorityQuery) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }
    }
}
