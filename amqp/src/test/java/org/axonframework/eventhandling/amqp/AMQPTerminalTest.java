package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.Channel;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.serializer.Serializer;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(locations = "/META-INF/spring/messaging-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AMQPTerminalTest {

    private SpringAMQPTerminal terminal;

    @Autowired
    private CachingConnectionFactory connectionFactory;
    @Autowired
    private Serializer serializer;
    @Autowired
    @Qualifier("cluster1")
    private SpringAMQPCluster cluster;
    private static final int EVENT_COUNT = 1000;
    private static final int THREAD_COUNT = 5;

    @Before
    public void setUp() throws Exception {
        try {
            Channel channel = connectionFactory.createConnection().createChannel(false);
            while (channel.basicGet("Axon.EventBus.Default", true) != null) {
            }
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
        terminal = new SpringAMQPTerminal(connectionFactory, serializer, "Axon.EventBus");
    }

    @Test
    public void testConnectAndDispatch_DefaultQueueAndExchange() throws Exception {
        final EventMessage<String> sentEvent = GenericEventMessage.asEventMessage("Hello world");
        final CountDownLatch cdl = new CountDownLatch(EVENT_COUNT * THREAD_COUNT);

        cluster.subscribe(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                assertEquals(sentEvent.getPayload(), event.getPayload());
                assertEquals(sentEvent.getIdentifier(), event.getIdentifier());
                cdl.countDown();
            }
        });

        List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < EVENT_COUNT; i++) {
                        boolean sent = false;
                        while (!sent) {
                            try {
                                terminal.publish(sentEvent);
                                sent = true;
                            } catch (Exception e) {
                                System.out.print(".");
                                // continue trying...
                            }
                        }
                    }
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        while (!cdl.await(1, TimeUnit.SECONDS)) {
            System.out.println("Waiting for more messages: " + cdl.getCount());
        }
        assertEquals("Did not receive message in time", 0, cdl.getCount());
    }
}
