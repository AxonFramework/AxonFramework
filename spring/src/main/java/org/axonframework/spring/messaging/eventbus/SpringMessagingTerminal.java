/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.messaging.eventbus;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * {@link org.axonframework.eventhandling.EventBus} implementation that delegates all subscription and publishing
 * requests to a {@link SubscribableChannel Spring Messaging channel}.
 * <p/>
 * Use {@link #setChannel(org.springframework.messaging.SubscribableChannel)} to set the channel to delegate all
 * the requests to.
 * <p/>
 * This EventBus will automatically wrap and unwrap events in {@link org.springframework.messaging.Message Messages}
 * and {@link org.axonframework.eventhandling.EventListener EventListeners} in {@link MessageHandler MessageHandlers}.
 * <p/>
 * This implementation expects the Spring Messaging to be configured to handle messages asynchronously.
 *
 * @author Allard Buijze
 * @since 2.3.1
 */
public class SpringMessagingTerminal {

    private final ConcurrentMap<Consumer<List<? extends EventMessage<?>>>, MessageHandler> handlers =
            new ConcurrentHashMap<>();
    private final EventBus eventBus;
    private Registration eventBusRegistration;
    private SubscribableChannel channel;

    public SpringMessagingTerminal(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void start() {
        eventBusRegistration = eventBus.subscribe(this::send);
    }

    public void shutDown() {
        Optional.ofNullable(eventBusRegistration).ifPresent(Registration::cancel);
    }

    protected void send(List<? extends EventMessage<?>> events) {
        for (EventMessage event : events) {
            channel.send(new GenericMessage<>(event.getPayload(),
                                              event.getMetaData()));
        }
    }

    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
        MessageHandler messagehandler = new MessageHandlerAdapter(eventProcessor);
        MessageHandler oldHandler = handlers.putIfAbsent(eventProcessor, messagehandler);
        if (oldHandler == null) {
            channel.subscribe(messagehandler);
        }
        return () -> {
            MessageHandler messageHandler = handlers.remove(eventProcessor);
            if (messageHandler != null) {
                channel.unsubscribe(messageHandler);
                return true;
            }
            return false;
        };
    }

    /**
     * Sets the Spring Messaging Channel that this event bus should publish events to.
     *
     * @param channel the channel to publish events to
     */
    public void setChannel(SubscribableChannel channel) {
        this.channel = channel;
    }
}
