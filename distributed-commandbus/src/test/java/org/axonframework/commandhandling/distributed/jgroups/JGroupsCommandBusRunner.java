/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.RoutingKeyExtractor;
import org.axonframework.serializer.XStreamSerializer;
import org.axonframework.unitofwork.UnitOfWork;
import org.jgroups.JChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class JGroupsCommandBusRunner {

    private static final Logger logger = LoggerFactory.getLogger(JGroupsCommandBusRunner.class);

    private static DistributedCommandBus dcb;
    private static final int MESSAGE_COUNT = 1000;
    private static JGroupsConnector connector;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        JChannel channel = new JChannel("org/axonframework/commandhandling/distributed/jgroups/tcp_mcast.xml");

        connector = new JGroupsConnector(channel,
                                         "testing",
                                         new SimpleCommandBus(),
                                         new XStreamSerializer());
        dcb = new DistributedCommandBus(connector, new RoutingKeyExtractor() {
            @Override
            public String getRoutingKey(CommandMessage<?> command) {
                return command.getPayload().toString();
            }
        });
        dcb.subscribe(String.class, new CommandHandler<String>() {
            @Override
            public Object handle(CommandMessage<String> stringCommandMessage, UnitOfWork unitOfWork) throws Throwable {
                logger.error("Received message: " + stringCommandMessage.getPayload());
                return null;
            }
        });
        System.out.println("Subscribed to group. Ready to join.");
        Scanner scanner = new Scanner(System.in);
        Integer loadFactor = null;
        while (loadFactor == null) {
            System.out.println("Please enter the load factor to join with:");
            String loadFactorString = scanner.nextLine();
            try {
                loadFactor = Integer.parseInt(loadFactorString);
            } catch (NumberFormatException e) {
                System.out.println("This is not a number.");
            }
        }
        dcb.subscribe(String.class, new CommandHandler<String>() {
            @Override
            public Object handle(CommandMessage<String> stringCommandMessage, UnitOfWork unitOfWork) throws Throwable {
                System.out.println("Received message: " + stringCommandMessage.getPayload());
                return null;
            }
        });
        connector.connect(loadFactor);
        System.out.println("Waiting for Joining to complete");
        connector.awaitJoined();
        System.out.println(
                "Runner is ready to start sending messages. Enter 'burst' to send 1000 messages, and 'quit' to exit");

        String line = "";
        while (!line.startsWith("quit")) {
            line = scanner.nextLine();
            if ("burst".equalsIgnoreCase(line)) {
                readAndSendMessages();
            } else if (line.startsWith("loadfactor ")) {
                String newLoadFactor = line.substring(11);
                try {
                    connector.connect(Integer.parseInt(newLoadFactor));
                } catch (NumberFormatException e) {
                    System.out.println(newLoadFactor + " is not a number");
                }
            } else if ("members".equals(line)) {
                System.out.println(connector.getConsistentHash().toString());
            } else if (line.matches("join [0-9]+")) {
                int factor = Integer.parseInt(line.split(" ")[1]);
                connector.connect(factor);
            } else if (!"quit".equals(line)) {
                dcb.dispatch(new GenericCommandMessage<String>(line));
            }
        }
        channel.close();
    }

    private static void readAndSendMessages() throws Exception {
        String messageBase = UUID.randomUUID().toString();
        for (int t = 0; t < MESSAGE_COUNT; t++) {
            dcb.dispatch(new GenericCommandMessage<String>(messageBase + " #" + t), new VoidCallback() {
                @Override
                protected void onSuccess() {
                    System.out.println("Successfully receive response");
                }

                @Override
                public void onFailure(Throwable cause) {
                    cause.printStackTrace();
                }
            });
        }
    }
}
