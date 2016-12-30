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

package org.axonframework.amqp.eventhandling.spring;

import com.rabbitmq.client.Channel;
import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.UnknownSerializedTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * MessageListener implementation that deserializes incoming messages and forwards them to one or more event processors.
 * <p>
 * The SpringAMQPMessageSource must be registered with a Spring MessageListenerContainer and forwards each message
 * to all subscribed processors.
 * <p>
 * Note that the Processors must be subscribed before the MessageListenerContainer is started. Otherwise, messages will
 * be consumed from the AMQP Queue without any processor processing them.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class SpringAMQPMessageSource implements ChannelAwareMessageListener,
                                                SubscribableMessageSource<EventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(SpringAMQPMessageSource.class);

    private final List<Consumer<List<? extends EventMessage<?>>>> eventProcessors = new CopyOnWriteArrayList<>();
    private final AMQPMessageConverter messageConverter;


    /**
     * Initializes an SpringAMQPMessageSource with {@link DefaultAMQPMessageConverter} using given {@code serializer}.
     *
     * @param serializer The serializer to serialize payload and metadata of events
     */
    public SpringAMQPMessageSource(Serializer serializer) {
        this(new DefaultAMQPMessageConverter(serializer));
    }

    /**
     * Initializes an SpringAMQPMessageSource with given {@code messageConverter} to convert the incoming AMQP
     * message into an EventMessage.
     *
     * @param messageConverter The message converter to use to convert AMQP Messages to Event Messages
     */
    public SpringAMQPMessageSource(AMQPMessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> messageProcessor) {
        eventProcessors.add(messageProcessor);
        return () -> eventProcessors.remove(messageProcessor);
    }

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        if (!eventProcessors.isEmpty()) {
            try {
                EventMessage<?> event = messageConverter
                        .readAMQPMessage(message.getBody(), message.getMessageProperties().getHeaders()).orElse(null);
                if (event != null) {
                    eventProcessors.forEach(ep -> ep.accept(Collections.singletonList(event)));
                }
            } catch (UnknownSerializedTypeException e) {
                logger.warn("Unable to deserialize an incoming message. Ignoring it. {}", e.toString());
            }
        }
    }

}
