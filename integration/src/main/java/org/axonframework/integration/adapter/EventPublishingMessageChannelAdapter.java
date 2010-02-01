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

package org.axonframework.integration.adapter;

import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventBus;
import org.springframework.integration.core.Message;
import org.springframework.integration.message.MessageHandler;
import org.springframework.integration.message.MessageRejectedException;

/**
 * @author Allard Buijze
 * @since 0.4
 */
public class EventPublishingMessageChannelAdapter implements MessageHandler {

    private final EventFilter filter;
    private final EventBus eventBus;

    public EventPublishingMessageChannelAdapter(EventBus eventBus) {
        this.eventBus = eventBus;
        this.filter = new NoFilter();
    }

    public EventPublishingMessageChannelAdapter(EventBus eventBus, EventFilter filter) {
        this.eventBus = eventBus;
        this.filter = filter;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void handleMessage(Message<?> message) {
        if (!(message.getPayload() instanceof Event)) {
            throw new MessageRejectedException(message, String.format(
                    "The payload of incoming messages must be of type: %s",
                    Event.class.getName()));
        }
        Class<? extends Event> eventType = (Class<? extends Event>) message.getPayload().getClass();
        if (filter.accept(eventType)) {
            eventBus.publish((Event) message.getPayload());
        } else {
            throw new MessageRejectedException(message, String.format(
                    "The event of type [%s] was blocked by the filter.",
                    eventType.getSimpleName()));
        }
    }
}
