package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import org.axonframework.common.Assert;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBusTerminal;
import org.axonframework.io.EventMessageReader;
import org.axonframework.io.EventMessageWriter;
import org.axonframework.serializer.Serializer;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * EventBusTerminal implementation that uses an AMQP 0.9 compatible Message Broker to dispatch event messages. While
 * outgoing messages are sent to an Exchange, incoming messages for each of the registered clusters are read in from
 * one or more queues.
 * <p/>
 * Several clusters may safely read from the same queue, as long as these clusters are registered with the same
 * AMQPTerminal instance. Registering them to different instances may result in messages being sent only to part of the
 * clusters available.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AMQPTerminal implements EventBusTerminal {

    /**
     * The key of the ClusterMetaData property that indicates the name of the Queue the Cluster should be connected
     * with.
     */
    public static final String QUEUE_NAME_PROPERTY = "AMQP.QueueName";
    private static final String DEFAULT_EXCHANGE_NAME = "Axon.EventBus";
    private static final Logger logger = LoggerFactory.getLogger(AMQPTerminal.class);

    private final Connection connection;
    private final Serializer serializer;
    private final String exchangeName;
    private final ConcurrentMap<String, ClusterConsumer> consumers = new ConcurrentHashMap<String, ClusterConsumer>();
    private final String defaultQueue;

    // guarded by "this"
    private boolean connected = false;

    /**
     * Initialize the Terminal to read from the given <code>connection</code> and using the given
     * <code>serializer</code> to serialize and deserialize messages. The <code>exchangeName</code> is used when
     * sending
     * messages, while the <code>defaultQueue</code> is used to read messages for clusters that have no explicit queue
     * defined.
     *
     * @param connection   The connection to the broker to use
     * @param serializer   The serializer to serialize and deserialize messages with
     * @param exchangeName The exchange to send outgoing messages to
     * @param defaultQueue The queue to read messages from for clusters that do not specify their own
     */
    public AMQPTerminal(Connection connection, Serializer serializer, String exchangeName, String defaultQueue) {
        this.connection = connection;
        this.serializer = serializer;
        this.exchangeName = exchangeName;
        this.defaultQueue = defaultQueue;
    }

    /**
     * Initialize the Terminal to read from the given <code>connection</code> and using the given
     * <code>serializer</code> to serialize and deserialize messages. The <code>defaultQueue</code> is used to read
     * messages for clusters that have no explicit queue defined.
     * The exchange used is &quot;{@value #DEFAULT_EXCHANGE_NAME}&quot;.
     *
     * @param connection   The connection to the broker to use
     * @param serializer   The serializer to serialize and deserialize messages with
     * @param defaultQueue The queue to read messages from for clusters that do not specify their own
     */
    public AMQPTerminal(Connection connection, Serializer serializer, String defaultQueue) {
        this(connection, serializer, DEFAULT_EXCHANGE_NAME, defaultQueue);
    }

    /**
     * Connects the AMQP Terminal with the given connection. Should be called only once, and after all the clusters
     * have been registered with the Terminal.
     *
     * @throws IOException when an I/O error occurs while communicating with the message broker.
     */
    public synchronized void connect() throws IOException {
        Assert.state(!connected, "This terminal is already connected.");
        connected = true;
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(exchangeName, "fanout");
        channel.close();
        for (Map.Entry<String, ClusterConsumer> entry : consumers.entrySet()) {
            String queueName = entry.getKey();
            ClusterConsumer consumer = entry.getValue();
            connectCluster(queueName, consumer);
        }
    }

    /**
     * Cleanly disconnects this terminal. This method will return when all shutdown procedures have finished, and the
     * connection with AMQP has successfully closed.
     *
     * @throws IOException when an exception occurs while closing the AMQP connection.
     */
    public synchronized void disconnect() throws IOException {
        Assert.state(connected, "This terminal is not connected or is already disconnected");
        connected = false;
        if (connection.isOpen()) {
            connection.close();
        }
    }

    private void connectCluster(String queueName, ClusterConsumer consumer) throws IOException {
        Channel consumerChannel = connection.createChannel();
        consumerChannel.queueDeclare(queueName, true, false, false, null);
        consumerChannel.queueBind(queueName, exchangeName, "");
        consumerChannel.setDefaultConsumer(consumer);
        consumer.setConsumerTag(
                consumerChannel.basicConsume(queueName, true, consumer.getConsumerTag(), consumer));
    }

    @Override
    public void publish(EventMessage event) {
        final Channel publisherChannel = openChannel();
        try {
            publisherChannel.txSelect();
            publisherChannel.basicPublish(exchangeName, "", false, true, null, asByteArray(event));
            if (CurrentUnitOfWork.isStarted()) {
                CurrentUnitOfWork.get().registerListener(new TransactionalUnitOfWorkListener(publisherChannel));
            } else {
                publisherChannel.txCommit();
            }
        } catch (IOException e) {
            closeQuietly(publisherChannel);
            throw new EventPublicationFailedException("Failed to dispatch Events to the", e);
        }
    }

    @Override
    public synchronized void onClusterCreated(final Cluster cluster) {
        String queueName = exchangeName + "." + defaultQueue;
        if (cluster.getMetaData().isPropertySet(QUEUE_NAME_PROPERTY)) {
            queueName = (String) cluster.getMetaData().getProperty(QUEUE_NAME_PROPERTY);
        }
        boolean newQueue = false;
        if (!consumers.containsKey(queueName)) {
            newQueue = true;
            consumers.putIfAbsent(queueName, new ClusterConsumer());
        }
        consumers.get(queueName).addCluster(cluster);
        if (newQueue && connected) {
            try {
                connectCluster(queueName, consumers.get(queueName));
            } catch (IOException e) {
                logger.warn("A new cluster was registered with the AMQPConnector, "
                                    + "but was unable to connect it to a Queue", e);
            }
        }
    }

    private Channel openChannel() {
        Channel publisherChannel;
        try {
            publisherChannel = connection.createChannel();
        } catch (IOException e) {
            throw new EventPublicationFailedException("Could not open channel to dispatch events", e);
        }
        return publisherChannel;
    }

    private void closeQuietly(Channel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException ex) {
                // we did our best
            }
        }
    }

    private byte[] asByteArray(EventMessage event) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EventMessageWriter outputStream = new EventMessageWriter(new DataOutputStream(baos), serializer);
            outputStream.writeEventMessage(event);
            return baos.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream doesn't throw IOException... anyway...
            throw new EventPublicationFailedException("Failed to serialize an EventMessage", e);
        }
    }

    private EventMessage fromByteArray(byte[] payload) {
        try {
            EventMessageReader in = new EventMessageReader(new DataInputStream(new ByteArrayInputStream(payload)),
                                                           serializer);
            return in.readEventMessage();
        } catch (IOException e) {
            throw new EventPublicationFailedException("Failed to serialize an EventMessage", e);
        }
    }

    private class ClusterConsumer implements Consumer {

        private final Set<Cluster> clusters = new CopyOnWriteArraySet<Cluster>();
        private volatile String consumerTag = "";

        public ClusterConsumer() {
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                throws IOException {
            for (Cluster cluster : clusters) {
                cluster.publish(fromByteArray(body));
            }
        }

        @Override
        public void handleConsumeOk(String consumerTag) {
        }

        @Override
        public void handleCancelOk(String consumerTag) {
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
        }

        @Override
        public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        }

        @Override
        public void handleRecoverOk(String consumerTag) {
        }

        public void addCluster(Cluster cluster) {
            clusters.add(cluster);
        }

        public String getConsumerTag() {
            return consumerTag;
        }

        public void setConsumerTag(String consumerTag) {
            this.consumerTag = consumerTag;
        }
    }

    private class TransactionalUnitOfWorkListener extends UnitOfWorkListenerAdapter {

        private final Channel publisherChannel;

        public TransactionalUnitOfWorkListener(Channel publisherChannel) {
            this.publisherChannel = publisherChannel;
        }

        @Override
        public void afterCommit() {
            try {
                publisherChannel.txCommit();
            } catch (IOException e) {
                logger.error("An error occurred while sending a commit message to the AMQP Channel", e);
                throw new EventPublicationFailedException(
                        "Failed to commit an AMQP transaction. Events may not have been published", e);
            } finally {
                closeQuietly(publisherChannel);
            }
        }

        @Override
        public void onRollback(Throwable failureCause) {
            try {
                publisherChannel.txRollback();
            } catch (IOException e) {
                logger.error("An error occurred while sending a rollback message to the AMQP Channel", e);
            } finally {
                closeQuietly(publisherChannel);
            }
        }
    }
}
