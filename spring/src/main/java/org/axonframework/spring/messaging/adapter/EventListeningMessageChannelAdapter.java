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

package org.axonframework.spring.messaging.adapter;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.PublishingEventProcessor;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

/**
 * Adapter class that sends Events from an event bus to a Spring Messaging Message Channel. All events are wrapped in
 * GenericMessage instances. The adapter automatically subscribes itself to the provided {@code EventBus}.
 * <p/>
 * Optionally, this adapter can be configured with a filter, which can block or accept messages based on their type.
 *
 * @author Allard Buijze
 * @since 2.3.1
 */
public class EventListeningMessageChannelAdapter implements EventListener, InitializingBean, BeanNameAware {

    private final MessageChannel channel;
    private final EventFilter filter;
    private final EventBus eventBus;
    private String beanName;

    /**
     * Initialize an adapter to forward messages from the given {@code eventBus} to the given
     * {@code channel}.
     * Messages are not filtered; all messages are forwarded to the MessageChannel
     *
     * @param eventBus The event bus to subscribe to.
     * @param channel  The channel to send event messages to.
     */
    public EventListeningMessageChannelAdapter(EventBus eventBus, MessageChannel channel) {
        this.eventBus = eventBus;
        this.channel = channel;
        this.filter = new NoFilter();
    }

    /**
     * Initialize an adapter to forward messages from the given {@code eventBus} to the given
     * {@code channel}.
     * Messages are filtered using the given {@code filter}
     *
     * @param eventBus The event bus to subscribe to.
     * @param channel  The channel to send event messages to.
     * @param filter   The filter that indicates which messages to forward.
     */
    public EventListeningMessageChannelAdapter(EventBus eventBus, MessageChannel channel, EventFilter filter) {
        this.channel = channel;
        this.eventBus = eventBus;
        this.filter = filter;
    }

    /**
     * Subscribes this event listener to the event bus.
     */
    @Override
    public void afterPropertiesSet() {
        eventBus.subscribe(new PublishingEventProcessor(beanName, this));
    }

    /**
     * If allows by the filter, wraps the given {@code event} in a {@link GenericMessage} ands sends it to the
     * configured {@link MessageChannel}.
     *
     * @param event the event to handle
     */
    @Override
    public void handle(EventMessage event) {
        if (filter.accept(event.getPayload().getClass())) {
            channel.send(new GenericMessage<>(event.getPayload(), event.getMetaData()));
        }
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }
}
