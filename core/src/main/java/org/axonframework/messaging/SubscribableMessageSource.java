package org.axonframework.messaging;

import org.axonframework.common.Registration;

import java.util.List;
import java.util.function.Consumer;

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
