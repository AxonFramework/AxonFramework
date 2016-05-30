/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.eventhandling.amqp.spring;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.amqp.AMQPMessageConverter;
import org.axonframework.serialization.UnknownSerializedTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * MessageListener implementation that deserializes incoming messages and forwards them to one or more event processors.
 * The <code>byte[]</code> making up the message payload must the format as used by the {@link SpringAMQPEventBus}.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventProcessorMessageListener implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorMessageListener.class);

    private final List<Consumer<List<? extends EventMessage<?>>>> eventProcessors = new CopyOnWriteArrayList<>();
    private final AMQPMessageConverter messageConverter;

    /**
     * Initializes a EventProcessorMessageListener with given <code>serializer</code> to deserialize the message's
     * contents into an EventMessage.
     *
     * @param messageConverter The message converter to use to convert AMQP Messages to Event Messages
     */
    public EventProcessorMessageListener(AMQPMessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    @Override
    public void onMessage(Message message) {
        if (!eventProcessors.isEmpty()) {
            try {
                EventMessage<?> event = messageConverter
                        .readAMQPMessage(message.getBody(), message.getMessageProperties().getHeaders());
                Optional.ofNullable(event).map(Collections::singletonList)
                        .ifPresent(events -> eventProcessors.forEach(eventProcessor -> eventProcessor.accept(events)));
            } catch (UnknownSerializedTypeException e) {
                logger.warn("Unable to deserialize an incoming message. Ignoring it. {}", e.toString());
            }
        }
    }

    /**
     * Registers an additional event processor. This processor will receive messages once registered.
     *
     * @param eventProcessor the event processor to add to the listener
     * @return a handle to unsubscribe the <code>eventProcessor</code>. When unsubscribed it will no longer receive
     * messages.
     */
    public Registration addEventProcessor(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
        eventProcessors.add(eventProcessor);
        return () -> eventProcessors.remove(eventProcessor);
    }

    public boolean isEmpty() {
        return eventProcessors.isEmpty();
    }
}
