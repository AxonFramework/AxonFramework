package org.axonframework.messaging;

import org.axonframework.common.Registration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for a source of {@link Message messages} to which message processors can subscribe.
 *
 * @param <M> the message type
 */
public interface SubscribableMessageSource<M extends Message<?>> {

    /**
     * Subscribe the given {@code messageProcessor} to this message source. When subscribed, it will receive all
     * messages published to this source.
     * <p>
     * If the given {@code messageProcessor} is already subscribed, nothing happens.
     *
     * @param messageProcessor The message processor to subscribe
     * @return a handle to unsubscribe the {@code messageProcessor}. When unsubscribed it will no longer receive
     * messages.
     */
    Registration subscribe(Consumer<List<? extends M>> messageProcessor);

}
