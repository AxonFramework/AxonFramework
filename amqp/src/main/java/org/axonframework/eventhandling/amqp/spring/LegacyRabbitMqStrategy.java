package org.axonframework.eventhandling.amqp.spring;

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

/**
 * Strategy for creating a SimpleMessageListenerContainer instance using the Spring AMQP 1.2 API. This version did not
 * contain the option to set a consumer to exclusive mode. This strategy creates the ExtendedMessageListenerContainer,
 * which is incompatible with the Spring AMQP 1.3 API.
 *
 * @author Allard Buijze
 * @since 2.4
 */
public class LegacyRabbitMqStrategy implements RabbitMqStrategy {

    @Override
    public void setExclusive(SimpleMessageListenerContainer container, boolean exclusive) {
        ((ExtendedMessageListenerContainer) container).setExclusive(exclusive);
    }

    @Override
    public SimpleMessageListenerContainer createContainer() {
        return new ExtendedMessageListenerContainer();
    }
}
