package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.serializer.XStreamSerializer;
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

    private static JChannel channel1;
    private static JChannel channel2;
    private JGroupsConnector connector1;
    private CommandBus mockCommandBus1;
    private JGroupsConnector connector2;
    private CommandBus mockCommandBus2;

    @BeforeClass
    public static void startChannels() throws Exception {
        channel1 = connect();
        channel2 = connect();
    }

    @AfterClass
    public static void stopChannel() {
        closeSilently(channel1);
        closeSilently(channel2);
    }

    @Before
    public void setUp() throws Exception {
        channel1 = connect();
        channel2 = connect();
        mockCommandBus1 = spy(new SimpleCommandBus());
        mockCommandBus2 = spy(new SimpleCommandBus());
        connector1 = new JGroupsConnector(channel1, "test", mockCommandBus1, new XStreamSerializer());
        connector2 = new JGroupsConnector(channel2, "test", mockCommandBus2, new XStreamSerializer());
    }

    @After
    public void tearDown() {
        channel1.disconnect();
        channel2.disconnect();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages_Balanced() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe(String.class, new CountingCommandHandler(counter1));
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(String.class, new CountingCommandHandler(counter2));
        connector2.connect(80);
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        Thread.sleep(250);

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
        verify(mockCommandBus1, atMost(50)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
        verify(mockCommandBus2, atLeast(50)).dispatch(any(CommandMessage.class), isA(CommandCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAndDispatchMessages_SingleCandidate() throws Exception {
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        connector1.subscribe(String.class, new CountingCommandHandler(counter1));
        connector1.connect(20);
        assertTrue("Expected connector 1 to connect within 10 seconds", connector1.awaitJoined(10, TimeUnit.SECONDS));

        connector2.subscribe(Object.class, new CountingCommandHandler(counter2));
        connector2.connect(80);
        assertTrue("Connector 2 failed to connect", connector2.awaitJoined());

        Thread.sleep(250);

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

    private static JChannel connect() throws Exception {
        return new JChannel("org/axonframework/commandhandling/distributed/jgroups/tcp.xml");
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
