/*
 * Copyright (c) 2010-2024. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedObjectHolder;
import org.axonframework.serialization.Serializer;

import java.io.Serial;
import java.util.Map;

/**
 * Generic implementation of the {@link Message} interface containing the {@link #getPayload() payload} and
 * {@link #getMetaData() metadata} in deserialized form.
 * <p>
 * If a {@link GenericMessage} is created while a {@link org.axonframework.messaging.unitofwork.UnitOfWork} is active it
 * copies over the correlation data of the {@code UnitOfWork} to the created message.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link Message}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.0
 */
public class GenericMessage<P> extends AbstractMessage<P> {

    @Serial
    private static final long serialVersionUID = 7937214711724527316L;

    private final P payload;
    private final MetaData metaData;

    private transient volatile SerializedObjectHolder serializedObjectHolder;

    @Deprecated
    private final Class<P> payloadType;

    /**
     * Returns a Message representing the given {@code payloadOrMessage}, either by wrapping it or by returning it
     * as-is. If the given {@code payloadOrMessage} already implements {@link Message}, it is returned as-is, otherwise
     * a {@link Message} is returned with the parameter as its payload.
     *
     * @param payloadOrMessage The payload to wrap or message to return
     * @return a Message with the given payload or the message
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName type}.
     */
    @Deprecated
    public static <P> Message<P> asMessage(Object payloadOrMessage) {
        if (payloadOrMessage instanceof Message) {
            //noinspection unchecked
            return (Message<P>) payloadOrMessage;
        }
        QualifiedName type = payloadOrMessage == null
                ? QualifiedName.dottedName("empty.command.payload")
                : QualifiedName.className(payloadOrMessage.getClass());
        //noinspection unchecked
        return new GenericMessage<>(type, (P) payloadOrMessage);
    }

    /**
     * Constructs a {@link GenericMessage} for the given {@code type} and {@code payload}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type    The {@link QualifiedName type} for this {@link Message}.
     * @param payload The payload of type {@code P} for this {@link Message}.
     * @see #asMessage(Object)
     */
    public GenericMessage(@Nonnull QualifiedName type,
                          @Nullable P payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericMessage} for the given {@code type}, {@code payload}, and {@code metaData}.
     * <p>
     * The given {@code metaData} is merged with the {@link MetaData} from the correlation data of the current Unit of
     * Work, if present. In case the {@code payload == null}, {@link Void} will be used as the {@code payloadType}.
     *
     * @param type     The {@link QualifiedName type} for this {@link Message}.
     * @param payload  The payload of type {@code P} for this {@link Message}.
     * @param metaData The metadata for this {@link Message}.
     * @see #asMessage(Object)
     */
    public GenericMessage(@Nonnull QualifiedName type,
                          @Nullable P payload,
                          @Nonnull Map<String, ?> metaData) {
        this(type, payload, metaData, getDeclaredPayloadType(payload));
    }

    /**
     * Constructs a {@link GenericMessage} for the given {@code type}, {@code payload}, and {@code metaData}.
     * <p>
     * The given {@code metaData} is merged with the MetaData from the correlation data of the current Unit of Work, if
     * present.
     *
     * @param type     The {@link QualifiedName type} for this {@link Message}.
     * @param payload  The payload of type {@code P} for this {@link Message}.
     * @param metaData The metadata for this {@link Message}.
     * @deprecated In favor of {@link #GenericMessage(QualifiedName, Object, Map)} once the {@code declaredPayloadType}
     * is removed completely.
     */
    @Deprecated
    public GenericMessage(@Nonnull QualifiedName type,
                          @Nullable P payload,
                          @Nonnull Map<String, ?> metaData,
                          @Deprecated Class<P> declaredPayloadType) {
        this(IdentifierFactory.getInstance().generateIdentifier(),
             type, payload,
             CurrentUnitOfWork.correlationData().mergedWith(MetaData.from(metaData)),
             declaredPayloadType);
    }

    /**
     * Constructs a {@link GenericMessage} for the given {@code identifier}, {@code type}, {@code payload}, and
     * {@code metaData}, intended to reconstruct another {@link Message}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work. If you in tend to construct a new {@link GenericMessage}, please use
     * {@link #GenericMessage(QualifiedName, Object)} instead.
     *
     * @param identifier The identifier of this {@link Message}.
     * @param type       The {@link QualifiedName type} for this {@link Message}.
     * @param payload    The payload of type {@code P} for this {@link Message}.
     * @param metaData   The metadata for this {@link Message}.
     */
    public GenericMessage(@Nonnull String identifier,
                          @Nonnull QualifiedName type,
                          @Nullable P payload,
                          @Nonnull Map<String, ?> metaData) {
        this(identifier, type, payload, metaData, getDeclaredPayloadType(payload));
    }

    /**
     * Constructs a {@link GenericMessage} for the given {@code identifier}, {@code type}, {@code payload}, and
     * {@code metaData}, intended to reconstruct another {@link Message}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work. If you in tend to construct a new {@link GenericMessage}, please use
     * {@link #GenericMessage(QualifiedName, Object)} instead.
     *
     * @param identifier The identifier of this {@link Message}.
     * @param type       The {@link QualifiedName type} for this {@link Message}.
     * @param payload    The payload of type {@code P} for this {@link Message}.
     * @param metaData   The metadata for this {@link Message}.
     * @deprecated In favor of {@link #GenericMessage(String, QualifiedName, Object, Map)} once the
     * {@code declaredPayloadType} is removed completely.
     */
    @Deprecated
    public GenericMessage(@Nonnull String identifier,
                          @Nonnull QualifiedName type,
                          @Nullable P payload,
                          @Nonnull Map<String, ?> metaData,
                          @Deprecated Class<P> declaredPayloadType) {
        super(identifier, type);
        this.payload = payload;
        this.metaData = MetaData.from(metaData);
        this.payloadType = declaredPayloadType;
    }

    private GenericMessage(@Nonnull GenericMessage<P> original,
                           @Nonnull MetaData metaData) {
        super(original.getIdentifier(), original.type());
        this.payload = original.getPayload();
        this.metaData = metaData;
        this.payloadType = original.getPayloadType();
    }

    /**
     * Extract the {@link Class} of the provided {@code payload}. If {@code payload == null} this function returns
     * {@link Void} as the payload type.
     *
     * @param payload the payload of this {@link Message}
     * @return the declared type of the given {@code payload} or {@link Void} if {@code payload == null}
     * @deprecated Remove this method entirely once the {@link #type()} has taken over the {@link #getPayloadType()}
     * entirely.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    private static <T> Class<T> getDeclaredPayloadType(T payload) {
        return payload != null ? (Class<T>) payload.getClass() : (Class<T>) Void.class;
    }

    /**
     * Construct an empty message.
     *
     * @return A message with {@code null} {@link Message#getPayload()}, no {@link MetaData}, and a
     * {@link Message#type()} of {@code "empty"}.
     */
    public static Message<Void> emptyMessage() {
        return new GenericMessage<>(QualifiedName.dottedName("empty"), null);
    }

    @Override
    public MetaData getMetaData() {
        return this.metaData;
    }

    @Override
    public P getPayload() {
        return this.payload;
    }

    @Override
    public Class<P> getPayloadType() {
        return this.payloadType;
    }

    @Override
    protected Message<P> withMetaData(MetaData metaData) {
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
