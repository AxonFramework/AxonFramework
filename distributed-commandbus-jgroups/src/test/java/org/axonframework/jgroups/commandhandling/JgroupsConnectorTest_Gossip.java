/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.jgroups.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.UnresolvedRoutingKeyPolicy;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.jgroups.stack.GossipRouter;
import org.junit.*;
import org.mockito.*;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
public class JgroupsConnectorTest_Gossip {

    private JChannel channel1;
    private JChannel channel2;
    private JGroupsConnector connector1;
    private CommandBus mockCommandBus1;
    private JGroupsConnector connector2;
    private CommandBus mockCommandBus2;
    private GossipRouter gossipRouter;
    private String clusterName;
    private XStreamSerializer serializer;
    private RoutingStrategy routingStrategy;

    private static JChannel createChannel() throws Exception {
        return new JChannel("org/axonframework/jgroups/commandhandling/tcp_gossip.xml");
    }

    @Before
    public void setUp() throws Exception {
        channel1 = createChannel();
        channel2 = createChannel();
        routingStrategy = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY);
        mockCommandBus1 = spy(SimpleCommandBus.builder().build());
        mockCommandBus2 = spy(SimpleCommandBus.builder().build());
        clusterName = "test-" + new Random().nextInt(Integer.MAX_VALUE);
        serializer = spy(XStreamSerializer.builder().build());
        connector1 = JGroupsConnector.builder()
                                     .localSegment(mockCommandBus1)
                                     .channel(channel1)
                                     .clusterName(clusterName)
                                     .serializer(serializer)
                                     .routingStrategy(routingStrategy)
                                     .build();
        connector2 = JGroupsConnector.builder()
                                     .localSegment(mockCommandBus2)
                                     .channel(channel2)
                                     .clusterName(clusterName)
                                     .serializer(serializer)
                                     .routingStrategy(routingStrategy)
                                     .build();
        gossipRouter = new GossipRouter("127.0.0.1", 12001);
    }

    @After
    public void tearDown() {
        if (gossipRouter != null) {
            gossipRouter.stop();
        }
    }

    @Test
    public void testConnectorRecoversWhenGossipRouterReconnects() throws Exception {

        connector1.updateMembership(20, AcceptAll.INSTANCE);
        connector1.connect();
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.updateMembership(80, AcceptAll.INSTANCE);
        connector2.connect();

        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // the nodes joined, but didn't detect eachother
        gossipRouter.start();

        // now, they should detect eachother and start syncing their state
        long deadline = System.currentTimeMillis() + 60000;
        while (!connector1.getConsistentHash().equals(connector2.getConsistentHash())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (System.currentTimeMillis() > deadline) {
                assertEquals("Connectors did not manage to synchronize consistent hash ring within 60 seconds...",
                             connector1.getConsistentHash(), connector2.getConsistentHash());
            }
            Thread.sleep(100);
        }
    }

    @Test(timeout = 30000)
    public void testDistributedCommandBusInvokesCallbackOnSerializationFailure() throws Exception {
        gossipRouter.start();

        final AtomicInteger counter2 = new AtomicInteger(0);

        DistributedCommandBus bus1 = DistributedCommandBus.builder()
                                                          .commandRouter(connector1)
                                                          .connector(connector1)
                                                          .build();
        bus1.updateLoadFactor(20);
        connector1.connect();
        assertTrue("Failed to connect", connector1.awaitJoined(5, TimeUnit.SECONDS));

        DistributedCommandBus bus2 = DistributedCommandBus.builder()
                                                          .commandRouter(connector2)
                                                          .connector(connector2)
                                                          .build();
        bus2.subscribe(String.class.getName(), new CountingCommandHandler(counter2));
        bus2.updateLoadFactor(20);
        connector2.connect();
        assertTrue("Failed to connect", connector2.awaitJoined(5, TimeUnit.SECONDS));

        // now, they should detect eachother and start syncing their state
        waitForConnectorSync();

        CommandGateway gateway1 = DefaultCommandGateway.builder().commandBus(bus1).build();

        doThrow(new RuntimeException("Mock")).when(serializer)
                                             .deserialize(argThat((ArgumentMatcher<SerializedObject<byte[]>>) x -> Arrays
                                                     .equals("<string>Try this!</string>"
                                                                     .getBytes(Charset.forName("UTF-8")),
                                                             x.getData())));

        try {
            gateway1.sendAndWait("Try this!");
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertEquals("Wrong exception. \nConsistent hash status of connector2: \n" + connector2.getConsistentHash(),
                         "Mock",
                         e.getMessage());
        }
    }

    private void waitForConnectorSync() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while ((connector1.getConsistentHash().getMembers().isEmpty())
                || !connector1.getConsistentHash().equals(connector2.getConsistentHash())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (System.currentTimeMillis() > deadline) {
                assertEquals("Connectors did not manage to synchronize consistent hash ring within 10 seconds...",
                             connector1.getConsistentHash(), connector2.getConsistentHash());
            }
            Thread.sleep(100);
        }
    }

    private static class CountingCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final AtomicInteger counter;

        public CountingCommandHandler(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public Object handle(CommandMessage<?> message) {
            counter.incrementAndGet();
            return "The Reply!";
        }
    }
}
