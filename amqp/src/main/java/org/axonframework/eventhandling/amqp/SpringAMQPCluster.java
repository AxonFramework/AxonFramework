package org.axonframework.eventhandling.amqp;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.AbstractCluster;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.io.EventMessageReader;
import org.axonframework.serializer.Serializer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Cluster implementation that ignores internally dispatched events. This cluster implements {@link MessageListener},
 * which allows it to be connected with Spring's {@link
 * org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer}, for instance. The latter provides the
 * connectivity and resilience required to reliably listen to events.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SpringAMQPCluster extends AbstractCluster implements MessageListener {

    private final Serializer serializer;

    /**
     * Initialize the cluster using given <code>serializer</code> to deserialize received events.
     *
     * @param serializer The serializer to deserialize events with.
     */
    public SpringAMQPCluster(Serializer serializer) {
        this.serializer = serializer;
        getMetaData().setProperty("ClusterType", "Spring AMQP");
    }

    /**
     * This method does nothing. Internally dispatched messages are ignored.
     *
     * @param event the event to publish - ignored in this implementation
     */
    @Override
    public void publish(EventMessage event) {
        // internally dispatched events are ignored
    }

    @Override
    public void onMessage(Message message) {
        EventMessage eventMessage = fromByteArray(message.getBody());
        for (EventListener listener : getMembers()) {
            listener.handle(eventMessage);
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
}
