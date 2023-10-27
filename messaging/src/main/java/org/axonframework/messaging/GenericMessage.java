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

package org.axonframework.messaging;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedObjectHolder;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * Generic implementation of a {@link Message} that contains the payload and metadata as unserialized values.
 * <p>
 * If a GenericMessage is created while a {@link org.axonframework.messaging.unitofwork.UnitOfWork} is active it copies
 * over the correlation data of the UnitOfWork to the created message.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericMessage<T> extends AbstractMessage<T> {

    private static final long serialVersionUID = 7937214711724527316L;
    private final MetaData metaData;
    private final Class<T> payloadType;
    private final T payload;
    private transient volatile SerializedObjectHolder serializedObjectHolder;

    /**
     * Returns a Message representing the given {@code payloadOrMessage}, either by wrapping it or by returning it
     * as-is. If the given {@code payloadOrMessage} already implements {@link Message}, it is returned as-is, otherwise
     * a {@link Message} is returned with the parameter as its payload.
     *
     * @param payloadOrMessage The payload to wrap or message to return
     * @return a Message with the given payload or the message
     */
    public static Message<?> asMessage(Object payloadOrMessage) {
        if (payloadOrMessage instanceof Message) {
            return (Message<?>) payloadOrMessage;
        } else {
            return new GenericMessage<>(payloadOrMessage);
        }
    }

    /**
     * Constructs a Message for the given {@code payload} using the correlation data of the current Unit of Work, if
     * present.
     *
     * @param payload The payload for the message
     */
    public GenericMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a Message for the given {@code payload} and {@code meta data}. The given {@code metaData} is merged
     * with the MetaData from the correlation data of the current unit of work, if present. In case the {@code payload
     * == null}, {@link Void} will be used as the {@code payloadType}.
     *
     * @param payload  The payload for the message as a generic {@code T}
     * @param metaData The meta data {@link Map} for the message
     */
    public GenericMessage(T payload, Map<String, ?> metaData) {
        this(getDeclaredPayloadType(payload), payload, metaData);
    }

    /**
     * Constructs a Message for the given {@code payload} and {@code meta data}. The given {@code metaData} is merged
     * with the MetaData from the correlation data of the current unit of work, if present.
     *
     * @param declaredPayloadType The declared type of message payload
     * @param payload             The payload for the message
     * @param metaData            The meta data for the message
     */
    public GenericMessage(Class<T> declaredPayloadType, T payload, Map<String, ?> metaData) {
        this(IdentifierFactory.getInstance().generateIdentifier(), declaredPayloadType, payload,
             CurrentUnitOfWork.correlationData().mergedWith(MetaData.from(metaData)));
    }

    /**
     * Constructor to reconstruct a Message using existing data. Note that no correlation data from a UnitOfWork is
     * attached when using this constructor. If you're constructing a new Message, use {@link #GenericMessage(Object,
     * Map)} instead.
     *
     * @param identifier The identifier of the Message
     * @param payload    The payload of the message
     * @param metaData   The meta data of the message
     * @throws NullPointerException when the given {@code payload} is {@code null}.
     */
    public GenericMessage(String identifier, T payload, Map<String, ?> metaData) {
        this(identifier, getDeclaredPayloadType(payload), payload, metaData);
    }

    /**
     * Constructor to reconstruct a Message using existing data. Note that no correlation data from a UnitOfWork is
     * attached when using this constructor. If you're constructing a new Message, use {@link #GenericMessage(Object,
     * Map)} instead
     *
     * @param identifier          The identifier of the Message
     * @param declaredPayloadType The declared type of message payload
     * @param payload             The payload for the message
     * @param metaData            The meta data for the message
     */
    public GenericMessage(String identifier, Class<T> declaredPayloadType, T payload, Map<String, ?> metaData) {
        super(identifier);
        this.metaData = MetaData.from(metaData);
        this.payload = payload;
        this.payloadType = declaredPayloadType;
    }

    private GenericMessage(GenericMessage<T> original, MetaData metaData) {
        super(original.getIdentifier());
        this.payload = original.getPayload();
        this.payloadType = original.getPayloadType();
        this.metaData = metaData;
    }

    /**
     * Extract the {@link Class} of the provided {@code payload}. If {@code payload == null} this function returns
     * {@link Void} as the payload type.
     *
     * @param payload the payload of this {@link Message}
     * @return the declared type of the given {@code payload} or {@link Void} if {@code payload == null}
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> getDeclaredPayloadType(T payload) {
        return payload != null ? (Class<T>) payload.getClass() : (Class<T>) Void.class;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Class<T> getPayloadType() {
        return payloadType;
    }

    @Override
    protected Message<T> withMetaData(MetaData metaData) {
        return new GenericMessage<>(this, metaData);
    }

    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        return serializedObjectHolder().serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        return serializedObjectHolder().serializeMetaData(serializer, expectedRepresentation);
    }

    private SerializedObjectHolder serializedObjectHolder() {
        if (serializedObjectHolder == null) {
            serializedObjectHolder = new SerializedObjectHolder(this);
        }
        return serializedObjectHolder;
    }
}
