package org.axonframework.eventhandling.amqp;

/**
 * Implementation of the AMQPConsumerConfiguration that allows the Queue Name to be configured. The configuration
 * specifies an exclusive consumer and no explicit prefetch count.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultAMQPConsumerConfiguration implements AMQPConsumerConfiguration {

    private final String queueName;

    /**
     * Initializes the configuration with the given <code>queueName</code>.
     *
     * @param queueName The name of the Queue a cluster should connect to
     */
    public DefaultAMQPConsumerConfiguration(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public Boolean getExclusive() {
        return true;
    }

    @Override
    public Integer getPrefetchCount() {
        return null;
    }
}
