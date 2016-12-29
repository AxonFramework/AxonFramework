/*
 * Copyright (c) 2010-2016. Axon Framework
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
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jgroups.JChannel;
import org.jgroups.stack.GossipRouter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
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

    @Before
    public void setUp() throws Exception {
        channel1 = createChannel();
        channel2 = createChannel();
        routingStrategy = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY);
        mockCommandBus1 = spy(new SimpleCommandBus());
        mockCommandBus2 = spy(new SimpleCommandBus());
        clusterName = "test-" + new Random().nextInt(Integer.MAX_VALUE);
        serializer = spy(new XStreamSerializer());
        connector1 = new JGroupsConnector(mockCommandBus1, channel1, clusterName, serializer, routingStrategy);
        connector2 = new JGroupsConnector(mockCommandBus2, channel2, clusterName, serializer, routingStrategy);
        gossipRouter = new GossipRouter("127.0.0.1", 12001);
    }

    @After
    public void tearDown() throws Exception {
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
        int t = 0;
        while (!connector1.getConsistentHash().getMembers().equals(connector2.getConsistentHash().getMembers())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (t++ > 600) {
                fail("Connectors did not manage to synchronize consistent hash ring within " + 60
                             + " seconds...");
            }
            Thread.sleep(100);
        }
    }

    @Test(timeout = 30000)
    public void testDistributedCommandBusInvokesCallbackOnSerializationFailure() throws Exception {
        gossipRouter.start();

        final AtomicInteger counter2 = new AtomicInteger(0);
        connector1.updateMembership(20, AcceptAll.INSTANCE);
        connector1.connect();
        connector2.updateMembership(20, AcceptAll.INSTANCE);
        connector2.connect();
        assertTrue("Failed to connect", connector1.awaitJoined(5, TimeUnit.SECONDS));
        assertTrue("Failed to connect", connector2.awaitJoined(5, TimeUnit.SECONDS));

        DistributedCommandBus bus1 = new DistributedCommandBus(connector1, connector1);
        //bus1.subscribe(String.class.getName(), new CountingCommandHandler(counter2));
        DistributedCommandBus bus2 = new DistributedCommandBus(connector2, connector2);
        bus2.subscribe(String.class.getName(), new CountingCommandHandler(counter2));

        // now, they should detect eachother and start syncing their state
        waitForConnectorSync(10);

        CommandGateway gateway1 = new DefaultCommandGateway(bus1);

        doThrow(new RuntimeException("Mock")).when(serializer).deserialize(argThat(new TypeSafeMatcher<SerializedObject<byte[]>>() {
            @Override
            protected boolean matchesSafely(SerializedObject<byte[]> item) {
                return Arrays.equals("<string>Try this!</string>".getBytes(Charset.forName("UTF-8")), item.getData());
            }

            @Override
            public void describeTo(Description description) {
            }
        }));

        try {
            gateway1.sendAndWait("Try this!");
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertEquals("Wrong exception. \nConsistent hash status of connector2: \n" + connector2.getConsistentHash(), "Mock", e.getMessage());
        }
    }

    private void waitForConnectorSync(int timeoutInSeconds) throws InterruptedException {
        int t = 0;
        while ((connector1.getConsistentHash().getMembers().isEmpty())
                || !connector1.getConsistentHash().equals(connector2.getConsistentHash())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (t++ > timeoutInSeconds * 10) {
                fail("Connectors did not manage to synchronize consistent hash ring within " + timeoutInSeconds
                             + " seconds...");
            }
            Thread.sleep(100);
        }
    }

    private static JChannel createChannel() throws Exception {
        return new JChannel("org/axonframework/jgroups/commandhandling/tcp_gossip.xml");
    }

    private static class CountingCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final AtomicInteger counter;

        public CountingCommandHandler(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public Object handle(CommandMessage<?> message) throws Exception {
            counter.incrementAndGet();
            return "The Reply!";
        }
    }
}
