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

package org.axonframework.integrationtests.axonserverconnector;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.grpc.ManagedChannelBuilder;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.command.AxonServerCommandBusConnector;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotations.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.DistributedCommandBusConfiguration;
import org.axonframework.commandhandling.distributed.PayloadConvertingCommandBusConnector;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.serialization.json.JacksonConverter;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating whether the provided message priority for {@link CommandMessage#priority() commands} is
 * respected.
 * <p>
 * Priorities for command messages are calculated through the {@link CommandPriorityCalculator} and typically set by the
 * {@link CommandGateway}.
 *
 * @author Jens Mayer
 * @author Steven van Beelen
 */
@Testcontainers
class MessagePriorityIntegrationTest {

    @Container
    private static final AxonServerContainer axonServer = new AxonServerContainer()
            .withAxonServerHostname("localhost")
            .withDevMode(true);

    private static final int PRIORITY = 42;
    private static final int REGULAR = 0;

    private static final Logger logger = LoggerFactory.getLogger(MessagePriorityIntegrationTest.class);

    private AxonServerConnectionManager connectionManager;
    private DistributedCommandBus commandBus;
    private CommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        var server = axonServer.getHost() + ":" + axonServer.getGrpcPort();
        var serverConfiguration = AxonServerConfiguration.builder()
                                                         .componentName("messagePriority")
                                                         .servers(server)
                                                         .build();
        serverConfiguration.setCommandThreads(1);
        serverConfiguration.setQueryThreads(1);
        connectionManager = AxonServerConnectionManager.builder()
                                                       .axonServerConfiguration(serverConfiguration)
                                                       .channelCustomizer(ManagedChannelBuilder::directExecutor)
                                                       .build();
        connectionManager.start();

        var unitOfWorkFactory = new SimpleUnitOfWorkFactory(
                EmptyApplicationContext.INSTANCE,
                c -> c.workScheduler(Executors.newSingleThreadExecutor())
        );
        var localCommandBus = new SimpleCommandBus(unitOfWorkFactory, Collections.emptyList());
        var commandBusConnector =
                new AxonServerCommandBusConnector(connectionManager.getConnection(), new AxonServerConfiguration());
        CommandBusConnector serializingConnector = new PayloadConvertingCommandBusConnector(
                commandBusConnector,
                new DelegatingMessageConverter(new JacksonConverter()),
                byte[].class
        );

        CommandPriorityCalculator commandPriorityCalculator =
                command -> Objects.equals(command.payloadType(), PriorityMessage.class) ? PRIORITY : REGULAR;

        var commandBusConfig = DistributedCommandBusConfiguration.DEFAULT.loadFactor(1);
        commandBus = new DistributedCommandBus(localCommandBus, serializingConnector, commandBusConfig);
        commandGateway = new DefaultCommandGateway(commandBus,
                                                   new ClassBasedMessageTypeResolver(),
                                                   commandPriorityCalculator,
                                                   new AnnotationRoutingStrategy());
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
    }

    @Test
    void commandPriorityIsRespectedWithinThresholdByDistributedCommandBus() throws InterruptedException {
        int numberOfCommands = 20;
        // No priority command should occur in the last fifth part of all dispatched commands.
        // Quite some lenience is given to account for thread ordering within the buses.
        int priorityThreshold = numberOfCommands - (numberOfCommands / 5);

        Queue<Handled> handlingOrder = new ConcurrentLinkedQueue<>();
        CountDownLatch processingGate = new CountDownLatch(1);
        CountDownLatch finishedGate = new CountDownLatch(numberOfCommands);

        commandBus.subscribe(new QualifiedName(StartMessage.class), (command, context) -> {
            try {
                processingGate.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return MessageStream.just(new GenericCommandResultMessage(new MessageType(String.class), "started"));
        });

        commandBus.subscribe(
                new QualifiedName(RegularMessage.class),
                (command, context) -> MessageStream.just(
                        new GenericCommandResultMessage(new MessageType(String.class), "regular")
                )
        );

        commandBus.subscribe(
                new QualifiedName(PriorityMessage.class),
                (command, context) -> MessageStream.just(
                        new GenericCommandResultMessage(new MessageType(String.class), "priority")
                )
        );

        logger.info("About to start dispatcher.");
        Thread dispatcher = new Thread(() -> {
            commandGateway.send(new StartMessage("start"), null);

            for (int i = 0; i < numberOfCommands; i++) {
                Object command;
                if (i % 5 == 0) {
                    command = new PriorityMessage(Integer.toString(i));
                } else {
                    command = new RegularMessage(Integer.toString(i));
                }
                commandGateway.send(command, null).onSuccess((resultMessage) -> {
                    //noinspection DataFlowIssue
                    if (resultMessage.payload().toString().equals("regular")) {
                        handlingOrder.add(Handled.regular());
                    } else {
                        handlingOrder.add(Handled.priority());
                    }
                    logger.info("Command {} handled", command);
                    finishedGate.countDown();
                });
                logger.info("Iteration {}: command {} sent", i, command);
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

    private static class Handled {

        private final boolean priority;

        private Handled(boolean priority) {
            this.priority = priority;
        }

        private static Handled priority() {
            return new Handled(true);
        }

        private static Handled regular() {
            return new Handled(false);
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

    private record StartMessage(@JsonProperty("text") String text) {

    }

    private record RegularMessage(@JsonProperty("text") String text) {

    }

    private record PriorityMessage(@JsonProperty("text") String text) {

    }
}