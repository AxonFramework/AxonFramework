package org.axonframework.eventhandling.amqp;

import org.axonframework.domain.EventMessage;

import java.util.Map;

/**
 * Interface describing a mechanism that converts AMQP Messages from an Axon Messages and vice versa.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface AMQPMessageConverter {

    /**
     * Creates an AMQPMessage from given <code>eventMessage</code>.
     *
     * @param eventMessage The EventMessage to create the AMQP Message from
     * @return an AMQP Message containing the data and characteristics of the Message to send to the AMQP Message
     *         Broker.
     */
    AMQPMessage createAMQPMessage(EventMessage eventMessage);

    /**
     * Reconstruct an EventMessage from the given <code>messageBody</code> and <code>headers</code>.
     *
     * @param messageBody The body of the AMQP Message
     * @param headers     The headers attached to the AMQP Message
     * @return The Event Message to publish on the local clusters
     */
    EventMessage readAMQPMessage(byte[] messageBody, Map<String, Object> headers);
}
