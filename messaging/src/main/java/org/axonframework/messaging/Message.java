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
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Representation of a {@link Message}, containing a {@link QualifiedName type}, payload of type {@code T}, and
 * {@link MetaData}.
 * <p>
 * Typical examples of a {@code Messages} are {@link org.axonframework.commandhandling.CommandMessage commands},
 * {@link org.axonframework.eventhandling.EventMessage events}, and
 * {@link org.axonframework.queryhandling.QueryMessage queries}.
 * <p/>
 * Instead of implementing {@code Message} directly, consider implementing {@code CommandMessage}, {@code EventMessage}
 * or {@code QueryMessage} instead.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link Message}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see org.axonframework.commandhandling.CommandMessage
 * @see org.axonframework.eventhandling.EventMessage
 * @see org.axonframework.queryhandling.QueryMessage
 * @since 2.0.0
 */
public interface Message<P> extends Serializable {

    /**
     * Returns the identifier of this {@link Message}.
     * <p>
     * Two messages with the same identifiers should be interpreted as different representations of the same conceptual
     * message. In such case, the {@link Message#getMetaData() metadata} may be different for both representations. The
     * {@link Message#getPayload() payload} <em>may</em> be identical.
     *
     * @return the unique identifier of this message
     */
    String getIdentifier();

    /**
     * Returns the {@link QualifiedName type} of this {@link Message}.
     *
     * @return The {@link QualifiedName type} of this {@link Message}.
     */
    @Nonnull
    QualifiedName type();

    /**
     * Returns the {@link MetaData} for this {@link Message}.
     * <p>
     * The {@code MetaData} is a collection of key-value pairs, where the key is a {@link String}, and the value is a
     * serializable object.
     *
     * @return The {@link MetaData} for this {@link Message}.
     */
    MetaData getMetaData();

    /**
     * Returns the payload of this {@link Message}.
     * <p>
     * The payload is the application-specific information.
     *
     * @return The payload of this {@link Message}.
     */
    P getPayload();

    /**
     * Returns the type of the payload.
     * <p/>
     * Is semantically equal to {@code getPayload().getClass()}, but allows implementations to optimize by using lazy
     * loading or deserialization.
     *
     * @return the type of payload.
     * @deprecated Payloads are just jvm-internal representations. No need for matching against payload types
     */
    @Deprecated
    // TODO #3085 - Replace for getMessageType once fully integrated
    Class<P> getPayloadType();

    /**
     * Returns a copy of this {@link Message} with the given {@code metaData}.
     * <p>
     * The payload remains unchanged.
     * <p/>
     * While the implementation returned may be different then the implementation of {@code this}, implementations must
     * take special care in returning the same type of {@code Message}to prevent errors further downstream.
     *
     * @param metaData The new metadata for the {@link Message}.
     * @return A copy of this {@link Message} with the given {@code metaData}.
     */
    Message<P> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this {@link Message} with its {@link Message#getMetaData() metadata} merged with the given
     * {@code metaData}.
     * <p>
     * The payload remains unchanged.
     *
     * @param metaData The metadata to merge with.
     * @return A copy of this {@link Message} with the given {@code metaData}.
     */
    Message<P> andMetaData(@Nonnull Map<String, ?> metaData);

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
     * Serialize the metadata of this message to the {@code expectedRepresentation} using given {@code serializer}. This
     * method <em>should</em> return the same SerializedObject instance when invoked multiple times using the same
     * serializer.
     *
     * @param serializer             The serializer to serialize meta data with
     * @param expectedRepresentation The type of data to serialize to
     * @param <R>                    The type of the serialized data
     * @return a SerializedObject containing the serialized representation of the message's metadata
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
    default <C> Message<C> withConvertedPayload(@Nonnull Function<P, C> conversion) {
        if (Objects.equals(getPayload(), conversion.apply(getPayload()))) {
            //noinspection unchecked
            return (Message<C>) this;
        }
        throw new UnsupportedOperationException("To be implemented");
    }
}
