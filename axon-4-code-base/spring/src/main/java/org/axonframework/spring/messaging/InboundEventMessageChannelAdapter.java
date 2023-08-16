/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.spring.messaging;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Adapter class that publishes Events from a Spring Messaging Message Channel on the Event Bus. All events are
 * expected to be contained in the payload of the Message instances.
 * <p/>
 * Optionally, this adapter can be configured with a filter, which can block or accept messages based on their type.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class InboundEventMessageChannelAdapter implements MessageHandler, SubscribableMessageSource<EventMessage<?>> {

    private final CopyOnWriteArrayList<Consumer<List<? extends EventMessage<?>>>> messageProcessors = new CopyOnWriteArrayList<>();
    private final EventMessageConverter eventMessageConverter;

    /**
     * Initialize the adapter to publish all incoming events to the subscribed processors. Note that this instance should
     *  be registered as a consumer of a Spring Message Channel.
     */
    public InboundEventMessageChannelAdapter() {
        this(emptyList(), new DefaultEventMessageConverter());
    }

    /**
     * Initialize an InboundEventMessageChannelAdapter instance that sends all incoming Event Messages to the given
     * {@code eventBus}. It is still possible for other Event Processors to subscribe to this MessageChannelAdapter.
     *
     * @param eventBus The EventBus instance for forward all messages to
     */
    public InboundEventMessageChannelAdapter(EventBus eventBus) {
        this(singletonList(eventBus::publish), new DefaultEventMessageConverter());
    }

    /**
     * Initialize the adapter to publish all incoming events to the subscribed processors. Note that this instance should
     *  be registered as a consumer of a Spring Message Channel.
     *
     * @param processors Processors to be subscribed
     * @param eventMessageConverter The message converter to use to convert spring message into event message
     */
    public InboundEventMessageChannelAdapter(List<Consumer<List<? extends EventMessage<?>>>> processors,
                                             EventMessageConverter eventMessageConverter){
        messageProcessors.addAll(processors);
        this.eventMessageConverter = eventMessageConverter;
    }

    @Nonnull
    @Override
    public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        messageProcessors.add(messageProcessor);
        return () -> messageProcessors.remove(messageProcessor);
    }

    /**
     * Handles the given {@code message}. If the filter refuses the message, it is ignored.
     *
     * @param message The message containing the event to publish
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public void handleMessage(@Nonnull Message<?> message) {
        List<? extends EventMessage<?>> messages = singletonList(transformMessage(message));
        for (Consumer<List<? extends EventMessage<?>>> messageProcessor : messageProcessors) {
            messageProcessor.accept(messages);
        }
    }

    /**
     * Transforms the given incoming Spring Messaging {@code message} to an Axon EventMessage. This method may be
     * overridden to change how messages are translated between the two frameworks.
     *
     * @param message the Spring message to convert to an event
     * @return an EventMessage from given Spring message
     */
    protected EventMessage<?> transformMessage(Message<?> message) {
        return eventMessageConverter.convertFromInboundMessage(message);
    }
}
