package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNoException;

/**
 * @author Allard Buijze
 */
public class AMQPTerminalTest {

    private Connection connection;
    private AMQPTerminal terminal;

    @Before
    public void setUp() throws Exception {
        try {
            connection = new ConnectionFactory().newConnection();
            Channel channel = connection.createChannel();
            channel.queueDeclare("Axon.EventBus.Default", false, false, false, null);
            while (channel.basicGet("Axon.EventBus.Default", true) != null) {
            }
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            assumeNoException(e);
        }
        Serializer serializer = new XStreamSerializer();
        terminal = new AMQPTerminal(connection, serializer, "Axon.EventBus.Default");
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null && connection.isOpen()) {
            connection.createChannel().exchangeDelete("Axon.EventBus");
            connection.createChannel().queueDelete("Axon.EventBus.Default");
        }
        terminal.disconnect();
    }

    @Test
    public void testConnectAndDispatch_DefaultQueueAndExchange() throws Exception {
        final EventMessage<String> sentEvent = GenericEventMessage.asEventMessage("Hello world");

        SimpleCluster cluster = new SimpleCluster();
        terminal.onClusterCreated(cluster);
        final CountDownLatch cdl = new CountDownLatch(1);
        cluster.subscribe(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                assertEquals(sentEvent.getPayload(), event.getPayload());
                assertEquals(sentEvent.getIdentifier(), event.getIdentifier());
                cdl.countDown();
            }
        });
        terminal.connect();

        terminal.publish(sentEvent);

        cdl.await(1000, TimeUnit.MILLISECONDS);
        assertEquals("Did not receive message in time", 0, cdl.getCount());
    }

    @Test
    public void testConnectAndDispatch_TwoClustersOnDefaultQueueAndExchange() throws Exception {
        SimpleCluster cluster1 = new SimpleCluster();
        SimpleCluster cluster2 = new SimpleCluster();
        terminal.onClusterCreated(cluster1);
        terminal.onClusterCreated(cluster2);
        final CountDownLatch cdl = new CountDownLatch(2);
        cluster1.subscribe(new CountingEventListener(cdl));
        cluster2.subscribe(new CountingEventListener(cdl));

        terminal.connect();

        terminal.publish(GenericEventMessage.asEventMessage("Hello world"));

        cdl.await(1000, TimeUnit.MILLISECONDS);
        assertEquals("Did not receive message in time. ", 0, cdl.getCount());
    }

    private static class CountingEventListener implements EventListener {

        private final CountDownLatch cdl;

        public CountingEventListener(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        @Override
        public void handle(EventMessage event) {
            cdl.countDown();
        }
    }
}
