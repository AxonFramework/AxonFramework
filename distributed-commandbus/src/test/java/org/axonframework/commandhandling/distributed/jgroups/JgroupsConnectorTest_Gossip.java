/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.unitofwork.UnitOfWork;
import org.jgroups.JChannel;
import org.jgroups.stack.GossipRouter;
import org.junit.*;

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

    @Before
    public void setUp() throws Exception {
        channel1 = createChannel();
        channel2 = createChannel();
        mockCommandBus1 = spy(new SimpleCommandBus());
        mockCommandBus2 = spy(new SimpleCommandBus());
        clusterName = "test-" + new Random().nextInt(Integer.MAX_VALUE);
        connector1 = new JGroupsConnector(channel1, clusterName, mockCommandBus1, new XStreamSerializer());
        connector2 = new JGroupsConnector(channel2, clusterName, mockCommandBus2, new XStreamSerializer());
        gossipRouter = new GossipRouter(12001, "127.0.0.1");
    }

    @After
    public void tearDown() throws Exception {
        if (gossipRouter != null) {
            gossipRouter.stop();
        }
    }

    @Test
    public void testConnectorRecoversWhenGossipRouterReconnects() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe(String.class.getName(), new CountingCommandHandler<String>(counter1));
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(Long.class.getName(), new CountingCommandHandler<Long>(counter2));
        connector2.connect(80);

        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // the nodes joined, but didn't detect eachother
        gossipRouter.start();

        // now, they should detect eachother and start syncing their state
        waitForConnectorSync(60);
    }

    private void waitForConnectorSync(int timeoutInSeconds) throws InterruptedException {
        int t = 0;
        while (ConsistentHash.emptyRing().equals(connector1.getConsistentHash())
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
        return new JChannel("org/axonframework/commandhandling/distributed/jgroups/tcp_gossip.xml");
    }

    private static class CountingCommandHandler<T> implements CommandHandler<T> {

        private final AtomicInteger counter;

        public CountingCommandHandler(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public Object handle(CommandMessage<T> stringCommandMessage, UnitOfWork unitOfWork) throws Throwable {
            counter.incrementAndGet();
            return "The Reply!";
        }
    }
}
