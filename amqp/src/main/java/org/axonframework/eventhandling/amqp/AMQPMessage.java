package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.AMQP;

/**
 * Representation of an AMQP Message. Used by AMQP Based Terminals to define the settings to use when dispatching an
 * Event to an AMQP Message Broker.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AMQPMessage {

    private final byte[] body;
    private final String routingKey;
    private final AMQP.BasicProperties properties;
    private final boolean mandatory;
    private final boolean immediate;

    /**
     * Creates an AMQP Message with given <code>body</code> and <code>routingKey</code>, which is not mandatory and
     * non-immediate and has no additional properties.
     *
     * @param body       The body of the message
     * @param routingKey The routing key of the message
     */
    public AMQPMessage(byte[] body, String routingKey) {
        this(body, routingKey, null, false, false);
    }

    /**
     * Creates an AMQPMessage. The given parameters define the properties returned by this instance.
     *
     * @param body       The body of the message
     * @param routingKey The routing key of the message
     * @param properties The properties defining AMQP specific characteristics of the message
     * @param mandatory  Whether the message is mandatory (i.e. at least one destination queue MUST be available)
     * @param immediate  Whether the message must be delivered immediately (i.e. a Consumer must be connected and
     *                   capable of reading the message right away).
     */
    public AMQPMessage(byte[] body, String routingKey, AMQP.BasicProperties properties,
                       boolean mandatory, boolean immediate) {
        this.body = body;
        this.routingKey = routingKey;
        this.properties = properties;
        this.mandatory = mandatory;
        this.immediate = immediate;
    }

    /**
     * Returns the body of this message
     *
     * @return the body of this message
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Returns the Routing Key this message should be dispatched with
     *
     * @return the Routing Key this message should be dispatched with
     */
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * Returns the additional properties to dispatch this Message with
     *
     * @return the additional properties to dispatch this Message with
     */
    public AMQP.BasicProperties getProperties() {
        return properties;
    }

    /**
     * Whether to dispatch this message using the "mandatory" flag
     *
     * @return whether to dispatch this message using the "mandatory" flag
     */
    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * Whether to dispatch this message using the "immediate" flag
     *
     * @return whether to dispatch this message using the "immediate" flag
     */
    public boolean isImmediate() {
        return immediate;
    }
}
