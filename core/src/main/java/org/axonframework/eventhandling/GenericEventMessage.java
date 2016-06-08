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

package org.axonframework.eventhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.metadata.MetaData;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * @author Rene de Waele
 */
public class GenericEventMessage<T> extends MessageDecorator<T> implements EventMessage<T> {
    private final Instant timestamp;

    public static Clock clock = Clock.systemUTC();

    @SuppressWarnings("unchecked")
    public static <T> EventMessage<T> asEventMessage(Object event) {
        if (EventMessage.class.isInstance(event)) {
            return (EventMessage<T>) event;
        } else if (event instanceof Message) {
            Message message = (Message) event;
            return new GenericEventMessage<>(message, clock.instant());
        }
        return new GenericEventMessage<>(new GenericMessage<>((T) event), clock.instant());
    }

    public GenericEventMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    public GenericEventMessage(T payload, Map<String, ?> metaData) {
        this(new GenericMessage<>(payload, metaData), clock.instant());
    }

    public GenericEventMessage(String identifier, T payload, Map<String, ?> metaData, Instant timestamp) {
        this(new GenericMessage<>(identifier, payload, metaData), timestamp);
    }

    public GenericEventMessage(Message<T> delegate, Instant timestamp) {
        super(delegate);
        this.timestamp = timestamp;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public GenericEventMessage<T> withMetaData(Map<String, ?> metaData) {
        return new GenericEventMessage<>(getDelegate().withMetaData(metaData), timestamp);
    }

    @Override
    public GenericEventMessage<T> andMetaData(Map<String, ?> metaData) {
        return new GenericEventMessage<>(getDelegate().andMetaData(metaData), timestamp);
    }
}
