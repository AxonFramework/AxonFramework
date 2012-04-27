package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBusTerminal;
import org.axonframework.io.EventMessageWriter;
import org.axonframework.serializer.Serializer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * EventBusTerminal implementation that uses an AMQP 0.9 compatible Message Broker to dispatch event messages. All
 * outgoing messages are sent to a configured Exchange, which defaults to {@value #DEFAULT_EXCHANGE_NAME}.
 * <p/>
 * This terminal does not dispatch Events internally, as it relies on each cluster to listen to it's own AMQP Queue.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SpringAMQPTerminal implements EventBusTerminal {

    private static final String DEFAULT_EXCHANGE_NAME = "Axon.EventBus";

    private final ConnectionFactory connectionFactory;
    private final Serializer serializer;
    private final String exchangeName;
    private final EventBusTerminal delegate;
    private static final AMQP.BasicProperties DURABLE = new AMQP.BasicProperties.Builder().deliveryMode(2).build();
    private boolean isTransactional = false;
    private boolean isDurable = false;

    /**
     * Initialize the Terminal to read from the given <code>connection</code> and using the given
     * <code>serializer</code> to serialize messages. Messages are sent to the given
     * <code>exchangeName</code>.
     *
     * @param connectionFactory The factory providing access to connections and channels
     * @param serializer        The serializer to serialize and deserialize messages with
     * @param exchangeName      The exchange to send outgoing messages to
     * @param delegate          a delegate terminal which may also dispatch events
     */
    public SpringAMQPTerminal(ConnectionFactory connectionFactory, Serializer serializer, String exchangeName,
                              EventBusTerminal delegate) {
        this.connectionFactory = connectionFactory;
        this.serializer = serializer;
        this.exchangeName = exchangeName;
        this.delegate = delegate;
    }

    /**
     * Initialize the Terminal to read from the given <code>connection</code> and using the given
     * <code>serializer</code> to serialize messages. Messages are sent to the given
     * <code>exchangeName</code>.
     *
     * @param connectionFactory The factory providing access to connections and channels
     * @param serializer        The serializer to serialize and deserialize messages with
     * @param exchangeName      The exchange to send outgoing messages to
     */
    public SpringAMQPTerminal(ConnectionFactory connectionFactory, Serializer serializer, String exchangeName) {
        this(connectionFactory, serializer, exchangeName, NoOpTerminal.INSTANCE);
    }

    /**
     * Initialize the Terminal to fetch connections from the given <code>connectionFactory</code> and using the given
     * <code>serializer</code> to serialize and deserialize messages. The exchange used is &quot;{@value
     * #DEFAULT_EXCHANGE_NAME}&quot;.
     *
     * @param connectionFactory The factory providing access to connections and channels
     * @param serializer        The serializer to serialize and deserialize messages with
     */
    public SpringAMQPTerminal(ConnectionFactory connectionFactory, Serializer serializer) {
        this(connectionFactory, serializer, DEFAULT_EXCHANGE_NAME, NoOpTerminal.INSTANCE);
    }

    @Override
    public void publish(EventMessage event) {
        Channel channel = connectionFactory.createConnection().createChannel(isTransactional);
        try {
            doSendMessage(channel, asByteArray(event), isDurable ? DURABLE : null);
            delegate.publish(event);
            if (isTransactional) {
                channel.txCommit();
            }
        } catch (IOException e) {
            if (isTransactional) {
                tryRollback(channel);
            }
            throw new EventPublicationFailedException("Failed to dispatch Events to the", e);
        } catch (ShutdownSignalException e) {
            throw new EventPublicationFailedException("Failed to dispatch Events to the Message Broker.", e);
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                System.out.println("That went wrong");
                // whatever...
            }
        }
    }

    /**
     * Does the actual publishing of the given <code>body</code> on the given <code>channel</code>. This method can be
     * overridden to change the properties used to send a message.
     *
     * @param channel The channel to dispatch the message on
     * @param body The body of the message to dispatch
     * @param props The default properties for the message
     * @throws IOException any exception that occurs while dispatching the message
     */
    protected void doSendMessage(Channel channel, byte[] body, AMQP.BasicProperties props) throws IOException {
        channel.basicPublish(exchangeName, "", true, false, props, body);
    }

    private void tryRollback(Channel channel) {
        try {
            channel.txRollback();
        } catch (IOException e1) {
            // whatever...
        }
    }

    @Override
    public void onClusterCreated(final Cluster cluster) {
        delegate.onClusterCreated(cluster);
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

    /**
     * Whether this Terminal should dispatch its Events in a transaction or not. Defaults to <code>false</code>.
     * <p/>
     * If a delegate Terminal  is configured, the transaction will be committed <em>after</em> the delegate has
     * dispatched the events.
     *
     * @param transactional whteher dispatching should be transactional or not
     */
    public void setTransactional(boolean transactional) {
        isTransactional = transactional;
    }

    /**
     * Whether or not messages should be marked as "durable" when sending them out. Durable messages suffer from a
     * performance penalty, but will survive a reboot of the Message broker that stores them.
     *
     * @param durable whether or not messages should be durable
     */
    public void setDurable(boolean durable) {
        isDurable = durable;
    }

    private static final class NoOpTerminal implements EventBusTerminal {

        public static final NoOpTerminal INSTANCE = new NoOpTerminal();

        @Override
        public void publish(EventMessage event) {
        }

        @Override
        public void onClusterCreated(Cluster cluster) {
        }
    }
}
