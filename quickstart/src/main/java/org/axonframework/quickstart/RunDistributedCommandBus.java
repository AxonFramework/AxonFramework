/*
 * Copyright (c) 2010-2013. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.quickstart;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.jgroups.JGroupsConnector;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.quickstart.api.CreateToDoItemCommand;
import org.axonframework.quickstart.api.MarkCompletedCommand;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.jgroups.stack.GossipRouter;

import java.net.BindException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This runner demonstrates the basic capabilities of the distributed command bus. It uses the default routing
 * strategy based on annotations. JGroups is used to connect the multiple jvm instances. Two very basic logging
 * command handlers are used to print the received commands. Each line starts with a number counting the amount
 * of commands handled by the instance of the command handler.
 * <p/>
 * When starting the runner two parameters are asked. The load factor and the amount of times to send the commands.
 *
 * @author Jettro Coenradie
 */
public class RunDistributedCommandBus {
    public static void main(String[] args) throws Exception {
        // Load the Load factor from the command line or use default 100
        Integer loadFactor = determineLoadFactor();

        // Start the GossipRouter if it is not already running
        GossipRouter gossipRouter = new GossipRouter();
        try {
            gossipRouter.start();
        } catch (BindException e) {
            System.out.println("Gossip router is already started in another JVM instance.");
        }

        // let's start with the local Command Bus that registers the handlers
        CommandBus localSegment = new SimpleCommandBus();

        // Configure the required components for jgroup, first een channel and serializer
        JChannel channel = new JChannel("tcp_gossip.xml");
        Serializer serializer = new XStreamSerializer();

        // Use the jgroup channel and the serializer to setup the connector to the jgroup cluster
        JGroupsConnector connector = new JGroupsConnector(channel, "myCluster", localSegment, serializer);

        // Setup the distributed command bus using the connector and the routing strategy
        DistributedCommandBus commandBus = new DistributedCommandBus(connector);

        // Register the Command Handlers with the command bus using the annotated methods of the object.
        AnnotationCommandHandlerAdapter.subscribe(new ToDoLoggingCommandHandler(), commandBus);

        // Start the connection to the distributed command bus
        connector.connect(loadFactor);

        // Load the amount of times to send the commands from the command line or use default 1
        Integer numberOfCommandLoops = determineNumberOfCommandLoops();

        // and let's send some Commands on the CommandBus.
        CommandGateway gateway = new DefaultCommandGateway(commandBus);
        for (int i = 0; i < numberOfCommandLoops; i++) {
            CommandGenerator.sendCommands(gateway);
        }
    }

    public static Integer determineNumberOfCommandLoops() {
        Integer numberOfLoops = readNumberFromCommandlinePlusDefault("Please enter the number of times to send commands", 1);
        System.out.println(String.format("Sending %d times the commands to the cluster.", numberOfLoops));
        return numberOfLoops;
    }

    public static Integer determineLoadFactor() {
        Integer loadFactor = readNumberFromCommandlinePlusDefault("Please enter the load factor to join with", 100);
        System.out.println(String.format("Using a load factor %d when connecting to the cluster.", loadFactor));
        return loadFactor;
    }


    public static Integer readNumberFromCommandlinePlusDefault(String message, int defaultValue) {
        Scanner scanner = new Scanner(System.in);
        Integer numberOfLoops = null;
        while (numberOfLoops == null) {
            System.out.println(String.format(message + ": [%d]", defaultValue));
            String loadFactorString = scanner.nextLine();
            if (loadFactorString.isEmpty()) {
                numberOfLoops = defaultValue;
                break;
            }
            try {
                numberOfLoops = Integer.parseInt(loadFactorString);
            } catch (NumberFormatException e) {
                System.out.println("This is not a number.");
            }
        }
        return numberOfLoops;
    }

    /* Handlers used to log the incoming commands */
    public static class ToDoLoggingCommandHandler {
        private AtomicInteger numberOfReceivedCommands = new AtomicInteger(0);

        @CommandHandler
        public void handle(CreateToDoItemCommand commandMessage) {
            int commandCounter = numberOfReceivedCommands.incrementAndGet();
            System.out.println(String.format("[%d] Received a Create todo item command with id: %s", commandCounter, commandMessage.getTodoId()));
        }

        @CommandHandler
        public void handle(MarkCompletedCommand commandMessage) {
            System.out.println(String.format("Received a Mark completed command with id: %s", commandMessage.getTodoId()));
        }
    }
}
