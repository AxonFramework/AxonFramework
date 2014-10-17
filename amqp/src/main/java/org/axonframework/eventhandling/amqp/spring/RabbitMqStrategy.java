package org.axonframework.eventhandling.amqp.spring;

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

/**
 * Interface describing a strategy for creating a SimpleMessageListenerContainer class and setting the exclusive mode
 * on the underlying subscriptions. Up until Spring AMQP 1.2, this was not supported. Support was added in Spring AMQP
 * 1.3 in a backward incompatible fashion.
 *
 * @author Allard Buijze
 * @since 2.4
 */
public interface RabbitMqStrategy {

    /**
     * Define exclusive mode on the underlying container
     *
     * @param container The container to set exclusive mode on
     * @param exclusive Whether to enable or disable exclusive mode
     */
    void setExclusive(SimpleMessageListenerContainer container, boolean exclusive);

    /**
     * Create a new instance of the container
     *
     * @return a new instance of the container
     */
    SimpleMessageListenerContainer createContainer();
}
