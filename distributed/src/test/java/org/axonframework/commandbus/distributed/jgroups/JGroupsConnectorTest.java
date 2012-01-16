package org.axonframework.commandbus.distributed.jgroups;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.serializer.XStreamSerializer;
import org.jgroups.JChannel;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

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

    private static JChannel channel1;
    private static JChannel channel2;
    private JGroupsConnector connector1;
    private CommandBus mockComandBus1;
    private JGroupsConnector connector2;
    private CommandBus mockComandBus2;

    @BeforeClass
    public static void startChannels() throws Exception {
        channel1 = connect();
        channel2 = connect();
    }

    @AfterClass
    public static void stopChanneld() {
        closeSilently(channel1);
        closeSilently(channel2);
    }

    @Before
    public void setUp() throws Exception {
        if (!channel1.isConnected()) {
            channel1 = connect();
        }
        if (!channel2.isConnected()) {
            channel2 = connect();
        }
        mockComandBus1 = mock(CommandBus.class);
        mockComandBus2 = mock(CommandBus.class);
        connector1 = new JGroupsConnector(channel1, "test", mockComandBus1, new XStreamSerializer());
        connector2 = new JGroupsConnector(channel2, "test", mockComandBus2, new XStreamSerializer());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages() throws Exception {
        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);

        doAnswer(new InvokeCallback(counter1, "The Reply!")).when(mockComandBus1).dispatch(isA(CommandMessage.class),
                                                                                           isA(CommandCallback.class));
        doAnswer(new InvokeCallback(counter2, "The Reply!")).when(mockComandBus2).dispatch(isA(CommandMessage.class),
                                                                                           isA(CommandCallback.class));

        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));
        connector2.connect(80);
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

//        Thread.sleep(250);

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
        verify(mockComandBus1, atMost(50)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
        verify(mockComandBus2, atLeast(50)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
    }

    private static void closeSilently(JChannel channel) {
        try {
            channel.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private static JChannel connect() throws Exception {
        return new JChannel("org/axonframework/commandbus/distributed/jgroups/tcp.xml");
    }

    private static class InvokeCallback implements Answer<Object> {

        private final AtomicInteger counter;
        private final String result;

        private InvokeCallback(AtomicInteger counter, String result) {
            this.counter = counter;
            this.result = result;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            CommandCallback callback = (CommandCallback) invocation.getArguments()[1];
            callback.onSuccess(result);
            counter.incrementAndGet();
            return null;
        }
    }
}
