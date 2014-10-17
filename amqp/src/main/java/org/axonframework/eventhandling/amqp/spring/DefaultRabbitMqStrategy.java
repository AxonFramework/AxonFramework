package org.axonframework.eventhandling.amqp.spring;

import org.axonframework.common.AxonConfigurationException;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.lang.reflect.Method;

/**
 * Strategy for creating a SimpleMessageListenerContainer instance using the Spring AMQP 1.3 API. Since then, the final
 * "setExclusive" method has been added, causing the ExtendedMessageListenerContainer to fail. This implementation
 * creates the SimpleMessageListenerContainer directly.
 *
 * @author Allard Buijze
 * @since 2.4
 */
public class DefaultRabbitMqStrategy implements RabbitMqStrategy {

    private static Method exclusiveMethod;

    static {
        try {
            exclusiveMethod = SimpleMessageListenerContainer.class.getMethod("setExclusive", boolean.class);
        } catch (NoSuchMethodException e) {
            throw new AxonConfigurationException("Issue initializing RabbitMQ. "
                                                         + "Please report this at http://issues.axonframework.org", e);
        }
    }

    @Override
    public void setExclusive(SimpleMessageListenerContainer container, boolean exclusive) {
        try {
            exclusiveMethod.invoke(container, exclusive);
        } catch (Exception e) {
            throw new AxonConfigurationException(
                    "Error invoking setter method, please report this at http://issues.axonframework.org",
                    e);
        }
    }

    @Override
    public SimpleMessageListenerContainer createContainer() {
        return new SimpleMessageListenerContainer();
    }
}
