/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.springframework.integration.Message;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.core.MessageHandler;

/**
 * Adapter class that publishes Events from a Spring Integration Message Channel on the Event Bus. All events are
 * expected to be contained in the payload of the Message instances.
 * <p/>
 * Optionally, this adapter can be configured with a filter, which can block or accept messages based on their type.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public class EventPublishingMessageChannelAdapter implements MessageHandler {

    private final EventFilter filter;
    private final EventBus eventBus;

    /**
     * Initialize the adapter to publish all incoming events to the given <code>eventBus</code>.
     *
     * @param eventBus The event bus to publish events on
     */
    public EventPublishingMessageChannelAdapter(EventBus eventBus) {
        this.eventBus = eventBus;
        this.filter = new NoFilter();
    }

    /**
     * Initialize the adapter to publish all incoming events to the given <code>eventBus</code> if they accepted by the
     * given <code>filter</code>.
     *
     * @param eventBus The event bus to publish events on.
     * @param filter   The filter that indicates which events to publish.
     */
    public EventPublishingMessageChannelAdapter(EventBus eventBus, EventFilter filter) {
        this.eventBus = eventBus;
        this.filter = filter;
    }

    /**
     * Handles the given <code>message</code>. The message is expected to contain an object of type {@link
     * org.axonframework.domain.EventMessage} in its payload. If that is not the case, a {@link
     * MessageRejectedException} is
     * thrown.
     * <p/>
     * If the <code>message</code> does contain an {@link org.axonframework.domain.EventMessage}, but the filter
     * refuses
     * it, a {@link
     * MessageRejectedException} is also thrown.
     *
     * @param message The message containing the event to publish
     * @throws MessageRejectedException is the payload could not be published on the event bus.
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public void handleMessage(Message<?> message) {
        Class<?> eventType = message.getPayload().getClass();
        if (filter.accept(eventType)) {
            eventBus.publish(new GenericEventMessage(message.getPayload(), message.getHeaders()));
        } else {
            throw new MessageRejectedException(message, String.format(
                    "The event of type [%s] was blocked by the filter.",
                    eventType.getSimpleName()));
        }
    }
}
