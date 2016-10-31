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

package org.axonframework.spring.messaging.adapter;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

/**
 * Adapter class that publishes Events from a Spring Messaging Message Channel on the Event Bus. All events are
 * expected to be contained in the payload of the Message instances.
 * <p/>
 * Optionally, this adapter can be configured with a filter, which can block or accept messages based on their type.
 *
 * @author Allard Buijze
 * @since 2.3.1
 */
public class EventPublishingMessageChannelAdapter implements MessageHandler {

    private final EventFilter filter;
    private final EventBus eventBus;

    /**
     * Initialize the adapter to publish all incoming events to the given {@code eventBus}.
     *
     * @param eventBus The event bus to publish events on
     */
    public EventPublishingMessageChannelAdapter(EventBus eventBus) {
        this.eventBus = eventBus;
        this.filter = NoFilter.INSTANCE;
    }

    /**
     * Initialize the adapter to publish all incoming events to the given {@code eventBus} if they accepted by the
     * given {@code filter}.
     *
     * @param eventBus The event bus to publish events on.
     * @param filter   The filter that indicates which events to publish.
     */
    public EventPublishingMessageChannelAdapter(EventBus eventBus, EventFilter filter) {
        this.eventBus = eventBus;
        this.filter = filter;
    }

    /**
     * Handles the given {@code message}. If the filter refuses the message, it is ignored.
     *
     * @param message The message containing the event to publish
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public void handleMessage(Message<?> message) {
        Class<?> eventType = message.getPayload().getClass();
        if (filter.accept(eventType)) {
            eventBus.publish(new GenericEventMessage(message.getPayload(), message.getHeaders()));
        }
    }
}
