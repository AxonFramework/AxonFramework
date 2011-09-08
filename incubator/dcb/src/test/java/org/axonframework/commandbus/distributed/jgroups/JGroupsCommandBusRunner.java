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

package org.axonframework.commandbus.distributed.jgroups;

import org.jgroups.ChannelClosedException;
import org.jgroups.ChannelNotConnectedException;
import org.jgroups.JChannel;

import java.util.Scanner;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class JGroupsCommandBusRunner {

    private static JGroupsCommandBus dcb;
    private static final int MESSAGE_COUNT = 1000;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        JChannel channel = new JChannel("jgroups_config/tcp.xml");
        channel.connect("testing");
        dcb = new JGroupsCommandBus(channel);
        dcb.subscribe();
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
        dcb.joinGroup(loadFactor);
        System.out.println("Waiting for Joining to complete");
        dcb.awaitJoined();
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
                    dcb.joinGroup(Integer.parseInt(newLoadFactor));
                } catch (NumberFormatException e) {
                    System.out.println(newLoadFactor + " is not a number");
                }
            } else if ("debug".equals(line)) {
                dcb.printHashRing();
            } else if ("members".equals(line)) {
                System.out.println(channel.getViewAsString());
            } else if (!"quit".equals(line)) {
                dcb.send(line);
            }
        }
        channel.close();
    }

    private static void readAndSendMessages() throws ChannelNotConnectedException, ChannelClosedException {
        for (int t = 0; t < MESSAGE_COUNT; t++) {
            String message = UUID.randomUUID().toString();
            dcb.send(message);
        }
    }
}
