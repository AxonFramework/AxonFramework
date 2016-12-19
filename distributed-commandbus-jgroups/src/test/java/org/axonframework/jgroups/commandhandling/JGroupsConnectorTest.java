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

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.UnresolvedRoutingKeyPolicy;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.stack.IpAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    private DistributedCommandBus dcb1;
    private JGroupsConnector connector2;
    private CommandBus mockCommandBus2;
    private DistributedCommandBus dcb2;
    private Serializer serializer;
    private String clusterName;
    private RoutingStrategy routingStrategy;

    @Before
    public void setUp() throws Exception {
        routingStrategy = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY);
        channel1 = createChannel();
        channel2 = createChannel();
        mockCommandBus1 = spy(new SimpleCommandBus());
        mockCommandBus2 = spy(new SimpleCommandBus());
        clusterName = "test-" + new Random().nextInt(Integer.MAX_VALUE);
        serializer = new XStreamSerializer();
        connector1 = new JGroupsConnector(mockCommandBus1, channel1, clusterName, serializer, routingStrategy);
        connector2 = new JGroupsConnector(mockCommandBus2, channel2, clusterName, serializer, routingStrategy);

        dcb1 = new DistributedCommandBus(connector1, connector1);
        dcb2 = new DistributedCommandBus(connector2, connector2);
    }

    @After
    public void tearDown() {
        closeSilently(channel1);
        closeSilently(channel2);
    }

    @Test
    public void testSetupOfReplyingCallback() throws Exception {
        final String mockPayload = "DummyString";
        final CommandMessage<String> commandMessage = new GenericCommandMessage<>(mockPayload);

        dcb1.subscribe(String.class.getName(), m -> "ok");
        connector1.connect();
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector1.awaitJoined();

        FutureCallback<String, Object> futureCallback = new FutureCallback<>();
        dcb1.dispatch(commandMessage, futureCallback);
        futureCallback.awaitCompletion(10, TimeUnit.SECONDS);

        //Verify that the newly introduced ReplyingCallBack class is being wired in. Actual behaviour of ReplyingCallback is tested in its unit tests
        verify(mockCommandBus1).dispatch(argThat(new ArgumentMatcher<CommandMessage<Object>>() {
            @Override
            public boolean matches(Object argument) {
                return argument instanceof CommandMessage && ((CommandMessage) argument).getPayload().equals(mockPayload);
            }
        }), any(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test(timeout = 30000)
    public void testConnectAndDispatchMessages_Balanced() throws Exception {
        assertNull(connector1.getNodeName());
        assertNull(connector2.getNodeName());

        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        dcb1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        dcb2.subscribe(String.class.getName(), new CountingCommandHandler(counter2));

        dcb1.updateLoadFactor(20);
        dcb2.updateLoadFactor(80);

        connector1.connect();
        connector2.connect();

        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        List<FutureCallback> callbacks = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object, Object> callback = new FutureCallback<>();
            String message = "message" + t;
            if ((t & 1) == 0) {
                dcb1.dispatch(new GenericCommandMessage<>(message), callback);
            } else {
                dcb2.dispatch(new GenericCommandMessage<>(message), callback);
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
        assertEquals(connector1.getConsistentHash(), connector2.getConsistentHash());
        assertNotNull(connector1.getNodeName());
        assertNotNull(connector2.getNodeName());
        assertNotEquals(connector1.getNodeName(), connector2.getNodeName());
    }

    @Test(expected = ConnectionFailedException.class, timeout = 30000)
    public void testRingsProperlySynchronized_ChannelAlreadyConnectedToOtherCluster() throws Exception {
        channel1.connect("other");
        connector1.connect();
    }

    @Test(timeout = 30000)
    public void testRingsProperlySynchronized_ChannelAlreadyConnected() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        dcb1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        dcb1.updateLoadFactor(20);
        connector1.connect();
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        dcb2.subscribe(Long.class.getName(), new CountingCommandHandler(counter2));
        dcb2.updateLoadFactor(20);
        connector2.connect();

        assertTrue("Connector 2 failed to connect", connector2.awaitJoined(10, TimeUnit.SECONDS));

        waitForConnectorSync();

        FutureCallback<Object, Object> callback1 = new FutureCallback<>();
        dcb1.dispatch(new GenericCommandMessage<>("Hello"), callback1);
        FutureCallback<Object, Object> callback2 = new FutureCallback<>();
        dcb1.dispatch(new GenericCommandMessage<>(1L), callback2);

        FutureCallback<Object, Object> callback3 = new FutureCallback<>();
        dcb2.dispatch(new GenericCommandMessage<>("Hello"), callback3);
        FutureCallback<Object, Object> callback4 = new FutureCallback<>();
        dcb2.dispatch(new GenericCommandMessage<>(1L), callback4);

        assertEquals("The Reply!", callback1.get());
        assertEquals("The Reply!", callback2.get());
        assertEquals("The Reply!", callback3.get());
        assertEquals("The Reply!", callback4.get());

        assertEquals(connector1.getConsistentHash(), connector2.getConsistentHash());
    }

    @Test
    public void testJoinMessageReceivedForDisconnectedHost() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        dcb1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        dcb1.updateLoadFactor(20);
        connector1.connect();

        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        dcb2.subscribe(String.class.getName(), new CountingCommandHandler(counter2));
        dcb2.updateLoadFactor(80);
        connector2.connect();
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        // secretly insert an illegal message
        channel1.getReceiver().receive(new Message(channel1.getAddress(), new IpAddress(12345),
                                                   new JoinMessage(new IpAddress(12345), 10, DenyAll.INSTANCE)));

        assertFalse("That message should not have changed the ring",
                    connector1.getConsistentHash().getMembers().stream()
                            .map(i -> i.getConnectionEndpoint(Address.class).orElse(null)).anyMatch(a -> a.equals(new IpAddress(12345))));
    }

    private void waitForConnectorSync() throws InterruptedException {
        int t = 0;
        while (!connector1.getConsistentHash().equals(connector2.getConsistentHash())) {
            // don't have a member for String yet, which means we must wait a little longer
            if (t++ > 300) {
                assertEquals("Connectors did not synchronize within 15 seconds.", connector1.getConsistentHash(),
                        connector2.getConsistentHash());
            }
            Thread.sleep(50);
        }
        Thread.yield();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages_SingleCandidate() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        dcb1.subscribe(String.class.getName(), new CountingCommandHandler(counter1));
        dcb1.updateLoadFactor(20);
        connector1.connect();
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        dcb2.subscribe(Object.class.getName(), new CountingCommandHandler(counter2));
        dcb2.updateLoadFactor(80);
        connector2.connect();
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        List<FutureCallback> callbacks = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object, Object> callback = new FutureCallback<>();
            String message = "message" + t;
            if ((t & 1) == 0) {
                dcb1.dispatch(new GenericCommandMessage<>(message), callback);
            } else {
                dcb2.dispatch(new GenericCommandMessage<>(message), callback);
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

    @Test
    public void testUnserializableResponseConvertedToNull() throws Exception {
        serializer = spy(new XStreamSerializer());
        Object successResponse = new Object();
        Exception failureResponse = new MockException("This cannot be serialized");
        when(serializer.serialize(successResponse, byte[].class)).thenThrow(new SerializationException("cannot serialize success"));
        when(serializer.serialize(failureResponse, byte[].class)).thenThrow(new SerializationException("cannot serialize failure"));

        connector1 = new JGroupsConnector(mockCommandBus1, channel1, clusterName, serializer, routingStrategy);
        dcb1 = new DistributedCommandBus(connector1, connector1);

        dcb1.subscribe(String.class.getName(), c -> successResponse);
        dcb1.subscribe(Integer.class.getName(), c -> {
            throw failureResponse;
        });
        connector1.connect();

        FutureCallback<Object, Object> callback = new FutureCallback<>();

        dcb1.dispatch(new GenericCommandMessage<>(1), callback);
        try {
            callback.getResult();
            fail("Expected exception");
        } catch (Exception e) {
            //expected
        }
        verify(mockCommandBus1).dispatch(any(CommandMessage.class), isA(CommandCallback.class));

        callback = new FutureCallback<>();
        dcb1.dispatch(new GenericCommandMessage<>("string"), callback);
        assertNull(callback.getResult());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages_CustomCommandName() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        dcb1.subscribe("myCommand1", new CountingCommandHandler(counter1));
        dcb1.updateLoadFactor(80);
        connector1.connect();
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        dcb2.subscribe("myCommand2", new CountingCommandHandler(counter2));
        dcb2.updateLoadFactor(20);
        connector2.connect();
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        // wait for both connectors to have the same view
        waitForConnectorSync();

        List<FutureCallback> callbacks = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            FutureCallback<Object, Object> callback = new FutureCallback<>();
            String message = "message" + t;
            if ((t % 3) == 0) {
                dcb1.dispatch(
                        new GenericCommandMessage<>(new GenericMessage<>(message), "myCommand1"),
                        callback);
            } else {
                dcb2.dispatch(
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

    @Test
    public void testDisconnectClosesJChannelConnection() throws Exception {
        connector1.connect();
        connector1.awaitJoined();

        connector1.disconnect();

        assertFalse("Expected channel to be disconnected on connector.disconnect()", channel1.isConnected());
    }

    private static void closeSilently(JChannel channel) {
        try {
            channel.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private static JChannel createChannel() throws Exception {
        return new JChannel("org/axonframework/jgroups/commandhandling/tcp_static.xml");
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
