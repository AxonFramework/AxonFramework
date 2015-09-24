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

package org.axonframework.serializer;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.Map;

/**
 * Wrapper around am EventMessage that adds "Serialization Awareness" to the message it wraps. This implementation
 * ensures that, when the payload or meta data is being serialized more than once using the same serializer, only a
 * single serialization will actually occur. Subsequent invocations will return the same <code>SerializedObject</code>
 * instance as the first.
 *
 * @param <T> The payload type of the Message
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializationAwareEventMessage<T> implements SerializationAware, EventMessage<T> {

    private static final long serialVersionUID = 4760330133615704145L;

    private final EventMessage<T> eventMessage;
    private final SerializedObjectHolder serializedObjectHolder;

    /**
     * Wrap the given <code>message</code> to make it SerializationAware. The returning object can be safely cast to
     * {@link SerializationAware}. If the given <code>message</code> already implements
     * <code>SerializationAware</code>, it is returned as-is. It is therefore not safe to assume the returning message
     * is an instance of <code>SerializationAwareEventMessage</code>.
     *
     * @param message The message to wrap
     * @param <T>     The payload type of the message
     * @return a serialization aware version of the given message
     */
    public static <T> EventMessage<T> wrap(EventMessage<T> message) {
        if (message instanceof SerializationAware) {
            return message;
        }
        return new SerializationAwareEventMessage<>(message);
    }

    /**
     * Initializes a new wrapper for the given <code>eventMessage</code>.
     *
     * @param eventMessage The message to wrap
     */
    protected SerializationAwareEventMessage(EventMessage<T> eventMessage) {
        this.eventMessage = eventMessage;
        this.serializedObjectHolder = new SerializedObjectHolder(eventMessage);
    }

    @Override
    public String getIdentifier() {
        return eventMessage.getIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return eventMessage.getMetaData();
    }

    @Override
    public T getPayload() {
        return eventMessage.getPayload();
    }

    @Override
    public Class<T> getPayloadType() {
        return eventMessage.getPayloadType();
    }

    @Override
    public Instant getTimestamp() {
        return eventMessage.getTimestamp();
    }

    @Override
    public EventMessage<T> withMetaData(Map<String, ?> metaData) {
        final EventMessage<T> newMessage = eventMessage.withMetaData(metaData);
        if (eventMessage == newMessage) { // NOSONAR - Equal instance check on purpose
            return this;
        }
        return new SerializationAwareEventMessage<>(newMessage);
    }

    @Override
    public EventMessage<T> andMetaData(Map<String, ?> metaData) {
        final EventMessage<T> newMessage = eventMessage.andMetaData(metaData);
        if (eventMessage == newMessage) { // NOSONAR - Equal instance check on purpose
            return this;
        }
        return new SerializationAwareEventMessage<>(newMessage);
    }

    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer,
                                                    Class<R> expectedRepresentation) {
        return serializedObjectHolder.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer,
                                                     Class<R> expectedRepresentation) {
        return serializedObjectHolder.serializeMetaData(serializer, expectedRepresentation);
    }

    /**
     * Replacement function for Java Serialization API. When this object is serialized, it is replaced by the
     * implementation it wraps.
     *
     * @return the EventMessage wrapped by this message
     */
    protected Object writeReplace() {
        return eventMessage;
    }
}
