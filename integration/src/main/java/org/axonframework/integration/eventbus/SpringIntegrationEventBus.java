/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.springframework.integration.core.MessageHandler;
import org.springframework.integration.core.SubscribableChannel;
import org.springframework.integration.message.GenericMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link org.axonframework.eventhandling.EventBus} implementation that delegates all subscription and publishing
 * requests to a {@link SubscribableChannel Spring Integration channel}.
 * <p/>
 * Use {@link #setChannel(org.springframework.integration.core.SubscribableChannel)} to set the channel to delegate all
 * the requests to.
 * <p/>
 * This EventBus will automatically wrap and unwrap events in {@link org.springframework.integration.Message Messages}
 * and {@link org.axonframework.eventhandling.EventListener EventListeners} in {@link MessageHandler MessageHandlers}.
 * <p/>
 * This implementation expects the Spring Integration to be configured to handle messages asynchronously.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class SpringIntegrationEventBus implements EventBus {

    private SubscribableChannel channel;
    private final ConcurrentMap<EventListener, MessageHandler> handlers =
            new ConcurrentHashMap<EventListener, MessageHandler>();

    @Override
    public void publish(EventMessage... events) {
        for(EventMessage event : events) {
            channel.send(new GenericMessage<Object>(event.getPayload(), event.getMetaData()));
        }
    }

    @Override
    public void unsubscribe(EventListener eventListener) {
        MessageHandler messageHandler = handlers.remove(eventListener);
        if (messageHandler != null) {
            channel.unsubscribe(messageHandler);
        }
    }

    @Override
    public void subscribe(EventListener eventListener) {
        MessageHandler messagehandler = new MessageHandlerAdapter(eventListener);
        MessageHandler oldHandler = handlers.putIfAbsent(eventListener, messagehandler);
        if (oldHandler == null) {
            channel.subscribe(messagehandler);
        }
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
