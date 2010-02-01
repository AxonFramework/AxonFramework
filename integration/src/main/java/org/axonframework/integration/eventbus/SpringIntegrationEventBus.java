/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integration.eventbus;

import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventListener;
import org.springframework.integration.channel.SubscribableChannel;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.message.MessageHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link org.axonframework.core.eventhandler.EventBus} implementation that delegates all subscription and publishing
 * requests to a {@link org.springframework.integration.channel.SubscribableChannel Spring Integration channel}.
 * <p/>
 * Use {@link #setChannel(org.springframework.integration.channel.SubscribableChannel)} to set the channel to delegate
 * all the requests to.
 * <p/>
 * This EventBus will automatically wrap and unwrap events in {@link org.springframework.integration.core.Message
 * Messages} and {@link org.axonframework.core.eventhandler.EventListener EventListeners} in {@link
 * org.springframework.integration.message.MessageHandler MessageHandlers}.
 * <p/>
 * This implementation expects the Spring Integration to be configured to handle messages asynchronously.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class SpringIntegrationEventBus implements EventBus {

    private SubscribableChannel channel;
    private final ConcurrentMap<EventListener, MessageHandler> handlers = new ConcurrentHashMap<EventListener, MessageHandler>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        MessageHandler messageHandler = handlers.remove(eventListener);
        channel.unsubscribe(messageHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(EventListener eventListener) {
        MessageHandler messagehandler = new MessageHandlerAdapter(eventListener);
        handlers.putIfAbsent(eventListener, messagehandler);
        channel.subscribe(messagehandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(Event event) {
        channel.send(new GenericMessage<Event>(event));
    }

    /**
     * Sets the Spring Integration Channel that this event bus should publish events to.
     *
     * @param channel the channel to publish events to
     */
    public void setChannel(SubscribableChannel channel) {
        this.channel = channel;
    }

}
