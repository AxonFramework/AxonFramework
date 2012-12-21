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
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.unitofwork.UnitOfWork;
import org.jgroups.JChannel;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class JGroupsConnectorTest {

    private JChannel channel1;
    private JChannel channel2;
    private JGroupsConnector connector1;
    private CommandBus mockCommandBus1;
    private JGroupsConnector connector2;
    private CommandBus mockCommandBus2;

    @Before
    public void setUp() throws Exception {
        channel1 = createChannel();
        channel2 = createChannel();
        mockCommandBus1 = spy(new SimpleCommandBus());
        mockCommandBus2 = spy(new SimpleCommandBus());
        connector1 = new JGroupsConnector(channel1, "test", mockCommandBus1, new XStreamSerializer());
        connector2 = new JGroupsConnector(channel2, "test", mockCommandBus2, new XStreamSerializer());
    }

    @After
    public void tearDown() {
        closeSilently(channel1);
        closeSilently(channel2);
    }

    @SuppressWarnings("unchecked")
    @Test(timeout = 30000)
    public void testConnectAndDispatchMessages_Balanced() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(String.class.getName(), new CountingCommandHandler(counter2));
        connector2.connect(80);
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        List<FutureCallback> callbacks = new ArrayList<FutureCallback>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object> callback = new FutureCallback<Object>();
            String message = "message" + t;
            if ((t & 1) == 0) {
                connector1.send(message, new GenericCommandMessage<Object>(message), callback);
            } else {
                connector2.send(message, new GenericCommandMessage<Object>(message), callback);
            }
            callbacks.add(callback);
        }
        for (FutureCallback callback : callbacks) {
            assertEquals("The Reply!", callback.get());
        }
        assertEquals(100, counter1.get() + counter2.get());
        System.out.println("Node 1 got " + counter1.get());
        System.out.println("Node 2 got " + counter2.get());
        verify(mockCommandBus1, atMost(40)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
        verify(mockCommandBus2, atLeast(60)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
    }

    @Test(expected = ConnectionFailedException.class, timeout = 30000)
    public void testRingsProperlySynchronized_ChannelAlreadyConnectedToOtherCluster() throws Exception {
        channel1.connect("other");
        connector1.connect(20);
    }

    @Test(timeout = 30000)
    public void testRingsProperlySynchronized_ChannelAlreadyConnected() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        channel1.connect("test");
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(Long.class.getName(), new CountingCommandHandler(counter2));
        channel2.connect("test");
        connector2.connect(80);

        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        waitForConnectorSync();

        FutureCallback<Object> callback1 = new FutureCallback<Object>();
        connector1.send("1", new GenericCommandMessage<Object>("Hello"), callback1);
        FutureCallback<?> callback2 = new FutureCallback();
        connector1.send("1", new GenericCommandMessage<Object>(1L), callback2);

        FutureCallback<Object> callback3 = new FutureCallback<Object>();
        connector2.send("1", new GenericCommandMessage<String>("Hello"), callback3);
        FutureCallback<?> callback4 = new FutureCallback();
        connector2.send("1", new GenericCommandMessage<Long>(1L), callback4);

        assertEquals("The Reply!", callback1.get());
        assertEquals("The Reply!", callback2.get());
        assertEquals("The Reply!", callback3.get());
        assertEquals("The Reply!", callback4.get());

        assertTrue(connector1.getConsistentHash().equals(connector2.getConsistentHash()));
    }

    private void waitForConnectorSync() throws InterruptedException {
        int t = 0;
        while (ConsistentHash.emptyRing().equals(connector1.getConsistentHash())
                || !connector1.getConsistentHash().equals(connector2.getConsistentHash())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (t++ > 500) {
                assertEquals("Connectors did not synchronize within 10 seconds.", connector1.getConsistentHash().toString(), connector2.getConsistentHash().toString());
            }
            Thread.sleep(20);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages_SingleCandidate() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(Object.class.getName(), new CountingCommandHandler(counter2));
        connector2.connect(80);
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        List<FutureCallback> callbacks = new ArrayList<FutureCallback>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object> callback = new FutureCallback<Object>();
            String message = "message" + t;
            if ((t & 1) == 0) {
                connector1.send(message, new GenericCommandMessage<Object>(message), callback);
            } else {
                connector2.send(message, new GenericCommandMessage<Object>(message), callback);
            }
            callbacks.add(callback);
        }
        for (FutureCallback callback : callbacks) {
            assertEquals("The Reply!", callback.get());
        }
        assertEquals(100, counter1.get() + counter2.get());
        System.out.println("Node 1 got " + counter1.get());
        System.out.println("Node 2 got " + counter2.get());
        verify(mockCommandBus1, times(100)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
        verify(mockCommandBus2, never()).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
    }


    private static void closeSilently(JChannel channel) {
        try {
            channel.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private static JChannel createChannel() throws Exception {
        return new JChannel("org/axonframework/commandhandling/distributed/jgroups/tcp_static.xml");
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
