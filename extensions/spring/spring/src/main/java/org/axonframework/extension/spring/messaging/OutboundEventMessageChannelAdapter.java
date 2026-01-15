/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.spring.messaging;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.SubscribableEventSource;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Adapter class that sends Events from an event bus to a Spring Messaging Message Channel. All events are wrapped in
 * GenericMessage instances. The adapter automatically subscribes itself to the provided {@code EventBus}.
 * <p/>
 * Optionally, this adapter can be configured with a filter, which can block or accept messages based on their type.
 *
 * @author Allard Buijze
 * @since 2.3.1
 */
public class OutboundEventMessageChannelAdapter implements InitializingBean {

    private final MessageChannel channel;
    private final Predicate<? super EventMessage> filter;
    private final SubscribableEventSource eventSource;
    private final EventMessageConverter eventMessageConverter;

    /**
     * Initialize an adapter to forward messages from the given {@code eventSource} to the given {@code channel}.
     * Messages are not filtered; all messages are forwarded to the MessageChannel
     *
     * @param eventSource The event bus to subscribe to.
     * @param channel       The channel to send event messages to.
     */
    public OutboundEventMessageChannelAdapter(@Nonnull SubscribableEventSource eventSource,
                                              @Nonnull MessageChannel channel) {
        this(eventSource, channel, m -> true);
    }

    /**
     * Initialize an adapter to forward messages from the given {@code eventSource} to the given {@code channel}.
     * Messages are filtered using the given {@code filter}.
     *
     * @param eventSource The source of messages to subscribe to.
     * @param channel       The channel to send event messages to.
     * @param filter        The filter that indicates which messages to forward.
     */
    public OutboundEventMessageChannelAdapter(@Nonnull SubscribableEventSource eventSource,
                                              @Nonnull MessageChannel channel, Predicate<? super EventMessage> filter) {
        this(eventSource, channel, filter, new DefaultEventMessageConverter());
    }

    /**
     * Initialize an adapter to forward messages from the given {@code eventSource} to the given {@code channel}.
     * Messages are filtered using the given {@code filter}.
     *
     * @param eventSource The source of messages to subscribe to.
     * @param channel       The channel to send event messages to.
     * @param filter        The filter that indicates which messages to forward.
     * @param eventMessageConverter The converter to use to convert event message into Spring message
     */
    public OutboundEventMessageChannelAdapter(@Nonnull SubscribableEventSource eventSource,
                                              @Nonnull MessageChannel channel, Predicate<? super EventMessage> filter,
                                              @Nonnull EventMessageConverter eventMessageConverter) {
        this.channel = Objects.requireNonNull(channel, "MessageChannel may not be null.");
        this.eventSource = Objects.requireNonNull(eventSource, "SubscribableEventSource may not be null.");
        this.filter = Objects.requireNonNull(filter, "Filter may not be null.");
        this.eventMessageConverter = Objects.requireNonNull(eventMessageConverter, "EventMessageConverter may not be null.");
    }

    /**
     * Subscribes this event listener to the event bus.
     */
    @Override
    public void afterPropertiesSet() {
        eventSource.subscribe((events, context) -> {
            handle(events, context);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * If allows by the filter, wraps the given {@code event} in a {@link GenericMessage} ands sends it to the
     * configured {@link MessageChannel}.
     *
     * @param events the events to handle
     */
    protected void handle(List<? extends EventMessage> events, ProcessingContext context) {
        events.stream()
                .filter(filter::test)
                .forEach(event -> channel.send(transform(event)));
    }

    /**
     * Transforms the given Axon {@code event} into a Spring Messaging Message. This method may be overridden to change
     * how this transformation should occur.
     *
     * @param event The Axon EventMessage to transform
     * @return The Spring Messaging Message representing the Event Message
     */
    protected Message transform(EventMessage event) {
        return eventMessageConverter.convertToOutboundMessage(event);
    }
}
