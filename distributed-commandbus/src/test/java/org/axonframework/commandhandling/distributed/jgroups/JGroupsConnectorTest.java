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

package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.jgroups.support.callbacks.ReplyingCallback;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.stack.IpAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    private String clusterName;
    private RecordingHashChangeListener hashChangeListener;
    private XStreamSerializer serializer;

    @Before
    public void setUp() throws Exception {
        channel1 = createChannel();
        channel2 = createChannel();
        mockCommandBus1 = spy(new SimpleCommandBus());
        mockCommandBus2 = spy(new SimpleCommandBus());
        clusterName = "test-" + new Random().nextInt(Integer.MAX_VALUE);
        hashChangeListener = new RecordingHashChangeListener();
        serializer = new XStreamSerializer();
        connector1 = new JGroupsConnector(channel1, clusterName, mockCommandBus1, serializer,
                                          hashChangeListener);
        connector2 = new JGroupsConnector(channel2, clusterName, mockCommandBus2, serializer);
    }

    @After
    public void tearDown() {
        closeSilently(channel1);
        closeSilently(channel2);
    }

    @Test
    public void testSetupOfReplyingCallback() throws InterruptedException {
        final String mockPayload = "DummyString";
        final CommandMessage<String> commandMessage = new GenericCommandMessage<>(mockPayload);

        final DispatchMessage dispatchMessage = new DispatchMessage(commandMessage, serializer, true);
        final Message message = new Message(channel1.getAddress(), dispatchMessage);

        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        channel1.getReceiver().receive(message);

        //Verify that the newly introduced ReplyingCallBack class is being wired in. Actual behaviour of ReplyingCallback is tested in its unit tests
        verify(mockCommandBus1).dispatch(refEq(commandMessage), any(ReplyingCallback.class));

    }

    @SuppressWarnings("unchecked")
    @Test(timeout = 30000)
    public void testConnectAndDispatchMessages_Balanced() throws Exception {
        assertNull(connector1.getNodeName());
        assertNull(connector2.getNodeName());

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

        List<FutureCallback> callbacks = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object, Object> callback = new FutureCallback<>();
            String message = "message" + t;
            if ((t & 1) == 0) {
                connector1.send(message, new GenericCommandMessage<>(message), callback);
            } else {
                connector2.send(message, new GenericCommandMessage<>(message), callback);
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
        assertEquals(connector1.getMembers(), connector2.getMembers());
        assertNotNull(connector1.getNodeName());
        assertNotNull(connector2.getNodeName());
        assertNotEquals(connector1.getNodeName(), connector2.getNodeName());
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
        channel1.connect(clusterName);
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(Long.class.getName(), new CountingCommandHandler(counter2));
        channel2.connect(clusterName);
        connector2.connect(80);

        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        waitForConnectorSync();

        FutureCallback<Object, Object> callback1 = new FutureCallback<>();
        connector1.send("1", new GenericCommandMessage<>("Hello"), callback1);
        FutureCallback<Object, Object> callback2 = new FutureCallback<>();
        connector1.send("1", new GenericCommandMessage<>(1L), callback2);

        FutureCallback<Object, Object> callback3 = new FutureCallback<>();
        connector2.send("1", new GenericCommandMessage<>("Hello"), callback3);
        FutureCallback<Object, Object> callback4 = new FutureCallback<>();
        connector2.send("1", new GenericCommandMessage<>(1L), callback4);

        assertEquals("The Reply!", callback1.get());
        assertEquals("The Reply!", callback2.get());
        assertEquals("The Reply!", callback3.get());
        assertEquals("The Reply!", callback4.get());

        assertTrue(connector1.getConsistentHash().equals(connector2.getConsistentHash()));
    }

    @Test
    public void testJoinMessageReceivedForDisconnectedHost() throws Exception {
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

        ConsistentHash hashBefore = connector1.getConsistentHash();
        // secretly insert an illegal message
        channel1.getReceiver().receive(new Message(channel1.getAddress(), new IpAddress(12345),
                                                   new JoinMessage(10, Collections.<String>emptySet())));
        ConsistentHash hash2After = connector1.getConsistentHash();
        assertEquals("That message should not have changed the ring", hashBefore, hash2After);
    }

    private void waitForConnectorSync() throws InterruptedException {
        int t = 0;
        while (ConsistentHash.emptyRing().equals(connector1.getConsistentHash())
                || !connector1.getConsistentHash().equals(connector2.getConsistentHash())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (t++ > 1500) {
                assertEquals("Connectors did not synchronize within 30 seconds.",
                             connector1.getConsistentHash().toString(),
                             connector2.getConsistentHash().toString());
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

        List<FutureCallback> callbacks = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object, Object> callback = new FutureCallback<>();
            String message = "message" + t;
            if ((t & 1) == 0) {
                connector1.send(message, new GenericCommandMessage<>(message), callback);
            } else {
                connector2.send(message, new GenericCommandMessage<>(message), callback);
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

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages_CustomCommandName() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe("myCommand1", new CountingCommandHandler(counter1));
        connector1.connect(80);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe("myCommand2", new CountingCommandHandler(counter2));
        connector2.connect(20);
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        List<FutureCallback> callbacks = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object, Object> callback = new FutureCallback<>();
            String message = "message" + t;
            if ((t % 3) == 0) {
                connector1.send(message,
                                new GenericCommandMessage<>(new GenericMessage<>(message), "myCommand1"),
                                callback);
            } else {
                connector2.send(message,
                                new GenericCommandMessage<>(new GenericMessage<>(message), "myCommand2"),
                                callback);
            }
            callbacks.add(callback);
        }
        for (FutureCallback callback : callbacks) {
            assertEquals("The Reply!", callback.get());
        }
        assertEquals(100, counter1.get() + counter2.get());
        System.out.println("Node 1 got " + counter1.get());
        System.out.println("Node 2 got " + counter2.get());
        verify(mockCommandBus1, times(34)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
        verify(mockCommandBus2, times(66)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
    }

    @Test(timeout = 300000)
    public void testHashChangeNotification() throws Exception {
        connector1.connect(10);
        connector2.connect(10);

        // wait for both connectors to have the same view
        waitForConnectorSync();

        // connector 1 joined
        ConsistentHash notify1 = hashChangeListener.notifications.poll(5, TimeUnit.SECONDS);

        // connector 2 joined
        ConsistentHash notify2 = hashChangeListener.notifications.poll(5, TimeUnit.SECONDS);
        // Self and other node have joined
        assertEquals(connector1.getConsistentHash(), notify2);

        channel2.close();

        // Other node has left
        ConsistentHash notify3 = hashChangeListener.notifications.poll(5, TimeUnit.SECONDS);
        assertEquals(connector1.getConsistentHash(), notify3);
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

    private static class CountingCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final AtomicInteger counter;

        public CountingCommandHandler(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public Object handle(CommandMessage<?> stringCommandMessage, UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
            counter.incrementAndGet();
            return "The Reply!";
        }
    }

    private static class RecordingHashChangeListener implements HashChangeListener {

        public final BlockingQueue<ConsistentHash> notifications = new LinkedBlockingQueue<>();

        @Override
        public void hashChanged(ConsistentHash newHash) {
            notifications.add(newHash);
        }
    }
}
