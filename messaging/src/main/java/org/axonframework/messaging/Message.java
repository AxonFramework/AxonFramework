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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Representation of a Message, containing a Payload and MetaData. Typical examples of Messages are Commands, Events and
 * Queries.
 * <p/>
 * Instead of implementing {@code Message} directly, consider implementing
 * {@link org.axonframework.commandhandling.CommandMessage {@code CommandMessage}}, {@link EventMessage} or
 * {@link org.axonframework.queryhandling.QueryMessage} instead.
 *
 * @param <T> the type of payload contained in this Message
 * @author Allard Buijze
 * @see org.axonframework.commandhandling.CommandMessage {@code CommandMessage}
 * @see EventMessage
 * @since 2.0
 */
public interface Message<T> extends Serializable {

    /**
     * Returns the identifier of this message. Two messages with the same identifiers should be interpreted as different
     * representations of the same conceptual message. In such case, the meta-data may be different for both
     * representations. The payload <em>may</em> be identical.
     *
     * @return the unique identifier of this message
     */
    String getIdentifier();

    /**
     * Returns the meta data for this message. This meta data is a collection of key-value pairs, where the key is a
     * String, and the value is a serializable object.
     *
     * @return the meta data for this message
     */
    MetaData getMetaData();

    /**
     * Returns the payload of this message. The payload is the application-specific information.
     *
     * @return the payload of this message
     */
    T getPayload();

    /**
     * Returns the type of the payload.
     * <p/>
     * Is semantically equal to {@code getPayload().getClass()}, but allows implementations to optimize by using lazy
     * loading or deserialization.
     *
     * @return the type of payload.
     * @deprecated Payloads are just jvm-internal representations. No need for matching against payload types
     */
    @Deprecated // TODO #3085 - Introduce QualifiedName
    Class<T> getPayloadType();

    /**
     * Returns a copy of this Message with the given {@code metaData}. The payload remains unchanged.
     * <p/>
     * While the implementation returned may be different than the implementation of {@code this}, implementations must
     * take special care in returning the same type of Message (e.g. EventMessage, DomainEventMessage) to prevent errors
     * further downstream.
     *
     * @param metaData The new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    Message<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this Message with it MetaData merged with the given {@code metaData}. The payload remains
     * unchanged.
     *
     * @param metaData The MetaData to merge with
     * @return a copy of this message with the given MetaData
     */
    Message<T> andMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Serialize the payload of this message to the {@code expectedRepresentation} using given {@code serializer}. This
     * method <em>should</em> return the same SerializedObject instance when invoked multiple times using the same
     * serializer.
     *
     * @param serializer             The serializer to serialize payload with
     * @param expectedRepresentation The type of data to serialize to
     * @param <R>                    The type of the serialized data
     * @return a SerializedObject containing the serialized representation of the message's payload
     * @deprecated Serialization is removed from messages themselves. Instead, use
     * {@link #withConvertedPayload(Function)}
     */
    @Deprecated
    default <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        return serializer.serialize(getPayload(), expectedRepresentation);
    }

    /**
     * Serialize the meta data of this message to the {@code expectedRepresentation} using given {@code serializer}.
     * This method <em>should</em> return the same SerializedObject instance when invoked multiple times using the same
     * serializer.
     *
     * @param serializer             The serializer to serialize meta data with
     * @param expectedRepresentation The type of data to serialize to
     * @param <R>                    The type of the serialized data
     * @return a SerializedObject containing the serialized representation of the message's meta data
     * @deprecated Serialization is removed from messages themselves. Instead, use
     * {@link #withConvertedPayload(Function)}
     */
    @Deprecated
    default <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        return serializer.serialize(getMetaData(), expectedRepresentation);
    }

    /**
     * Returns a message which is effectively a copy of this Message with its payload converted using the given
     * {@code conversion} function.
     * <p>
     * Will only return the same instance if the conversion returns a payload object that is equal to the current
     * payload.
     *
     * @param conversion The function to apply to the payload of this message
     * @param <C>        The new type of payload
     * @return a message with the converted payload
     */
    default <C> Message<C> withConvertedPayload(@Nonnull Function<T, C> conversion) {
        if (Objects.equals(getPayload(), conversion.apply(getPayload()))) {
            return (Message<C>) this;
        }
        throw new UnsupportedOperationException("To be implemented");
    }
}
