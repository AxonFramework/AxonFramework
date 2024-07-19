package org.axonframework.config;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.SubscribableMessageSource;

/**
 * Definition of a {@link SubscribableMessageSource}.
 *
 * @param <M> message type of the subscribable message source.
 */
public interface SubscribableMessageSourceDefinition<M extends Message<?>> {

    /**
     * Creates a {@link SubscribableMessageSource} based on this definition and the provided configuration.
     * @param configuration the Axon Configuration.
     * @return a subscribable message source.
     */
    SubscribableMessageSource<M> create(Configuration configuration);
}
