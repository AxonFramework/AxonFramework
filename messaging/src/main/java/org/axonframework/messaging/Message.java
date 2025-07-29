/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Representation of a {@link Message}, containing a {@link MessageType type}, payload of type {@code T}, and
 * {@link MetaData}.
 * <p>
 * Typical examples of a {@code Messages} are {@link org.axonframework.commandhandling.CommandMessage commands},
 * {@link org.axonframework.eventhandling.EventMessage events}, and
 * {@link org.axonframework.queryhandling.QueryMessage queries}.
 * <p/>
 * Instead of implementing {@code Message} directly, consider implementing {@code CommandMessage}, {@code EventMessage}
 * or {@code QueryMessage} instead.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@code Message}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see org.axonframework.commandhandling.CommandMessage
 * @see org.axonframework.eventhandling.EventMessage
 * @see org.axonframework.queryhandling.QueryMessage
 * @since 2.0.0
 */
public interface Message<P> {

    /**
     * The {@link Context.ResourceKey} used to store and retrieve the {@link Message} from the
     * {@link ProcessingContext}. Should always be the message for which a handler is being called.
     * For example, if an event handler is called within the context of a command, the message should be the event
     * message.
     */
    Context.ResourceKey<Message<?>> RESOURCE_KEY = Context.ResourceKey.withLabel("Message");

    /**
     * Adds the given {@code message} to the given {@code context} under the {@link #RESOURCE_KEY}. This allows
     * retrieving the message from the context later on, for example in a message handler.
     * <p>
     * Note that as the {@link ProcessingContext} might not be mutable in all implementations, this method returns a new
     * {@link ProcessingContext} instance which should be used in place of the original.
     *
     * @param context The {@link ProcessingContext} to which the {@code message} should be added.
     * @param message The {@link Message} to add to the {@code context}.
     * @return The updated {@link ProcessingContext} with the {@code message} added under the {@link #RESOURCE_KEY}.
     */
    @Nonnull
    static ProcessingContext addToContext(@Nonnull ProcessingContext context, @Nonnull Message<?> message) {
        return context.withResource(RESOURCE_KEY, message);
    }

    /**
     * Retrieves the {@link Message} from the given {@code context} using the {@link #RESOURCE_KEY}.
     *
     * @param context The {@link ProcessingContext} from which to retrieve the {@link Message}.
     * @return The {@link Message} stored in the {@code context} under the {@link #RESOURCE_KEY}, or {@code null} if not
     * found.
     */
    @Nullable
    static Message<?> fromContext(@Nonnull ProcessingContext context) {
        return context.getResource(RESOURCE_KEY);
    }

    /**
     * Returns the identifier of this {@code Message}.
     * <p>
     * Two messages with the same identifiers should be interpreted as different representations of the same conceptual
     * message. In such case, the {@link Message#getMetaData() metadata} may be different for both representations. The
     * {@link Message#getPayload() payload} <em>may</em> be identical.
     *
     * @return The unique identifier of this {@code Message}.
     */
    String getIdentifier();

    /**
     * Returns the message {@link QualifiedName qualifiedName} of this {@code Message}.
     *
     * @return The message {@link QualifiedName qualifiedName} of this {@code Message}.
     */
    @Nonnull
    MessageType type();

    /**
     * Returns the {@link MetaData} for this {@code Message}.
     * <p>
     * The {@code MetaData} is a collection of key-value pairs, where the key is a {@link String}, and the value is a
     * serializable object.
     *
     * @return The {@link MetaData} for this {@code Message}.
     */
    MetaData getMetaData();

    /**
     * Returns the payload of this {@code Message} of generic type {@code P}.
     * <p>
     * The payload is the application-specific information.
     *
     * @return The payload of this {@code Message} of generic type {@code P}.
     */
    P getPayload();

    /**
     * Returns the payload of this {@code Message}, converted to the given {@code type} by the given {@code converter}.
     * <p>
     * If {@link #getPayloadType()} is {@link Class#isAssignableFrom(Class) assignable from} the given {@code type},
     * {@link #getPayload()} may be invoked instead of using the given {@code converter}.
     * <p>
     * Implementers of this operation may optimize by storing the converted payloads, thus saving a
     * {@link Converter#convert(Object, Class)} invocation in the process. Only when this optimization is in place will
     * a {@code null converter} result in a successful invocation of this method.
     *
     * @param type      The type to convert this {@code Message's} payload too.
     * @param converter The converter to convert this {@code Message's} payload with.
     * @param <T>       The generic type to convert this {@code Message's} payload too.
     * @return The payload of this {@code Message}, converted to the given {@code type}.
     * @throws NullPointerException When {@link Converter#convert(Object, Class) conversion} is mandatory but no
     *                              {@code converter} is given.
     */
    default <T> T payloadAs(@Nonnull Class<T> type, @Nullable Converter converter) {
        if (getPayloadType().isAssignableFrom(type)) {
            return type.cast(getPayload());
        }
        return payloadAs(new TypeReference<>() {}, converter);
    }

    /**
     * Returns the payload of this {@code Message}, converted to the given {@code type} by the given {@code converter}.
     * <p>
     * If {@link #getPayloadType()} is {@link Class#isAssignableFrom(Class) assignable from} the given
     * {@link TypeReference#getType()}, {@link #getPayload()} may be invoked instead of using the given
     * {@code converter}.
     * <p>
     * Implementers of this operation may optimize by storing the converted payloads, thus saving a
     * {@link Converter#convert(Object, Class)} invocation in the process. Only when this optimization is in place will
     * a {@code null converter} result in a successful invocation of this method.
     *
     * @param type      The type to convert this {@code Message's} payload too.
     * @param converter The converter to convert this {@code Message's} payload with.
     * @param <T>       The generic type to convert this {@code Message's} payload too.
     * @return The payload of this {@code Message}, converted to the given {@code type}.
     * @throws NullPointerException When {@link Converter#convert(Object, Class) conversion} is mandatory but no
     *                              {@code converter} is given.
     */
    default <T> T payloadAs(@Nonnull TypeReference<T> type, @Nullable Converter converter) {
        return payloadAs(type.getType(), converter);
    }

    /**
     * Returns the payload of this {@code Message}, converted to the given {@code type} by the given {@code converter}.
     * <p>
     * If the given {@code type} is an instance of {@link Class} and {@link #getPayloadType()} is
     * {@link Class#isAssignableFrom(Class) assignable from} that {@code Class}, {@link #getPayload()} may be invoked
     * instead of using the given {@code converter}.
     * <p>
     * Implementers of this operation may optimize by storing the converted payloads, thus saving a
     * {@link Converter#convert(Object, Class)} invocation in the process. Only when this optimization is in place will
     * a {@code null converter} result in a successful invocation of this method.
     *
     * @param type      The type to convert this {@code Message's} payload too.
     * @param converter The converter to convert this {@code Message's} payload with.
     * @param <T>       The generic type to convert this {@code Message's} payload too.
     * @return The payload of this {@code Message}, converted to the given {@code type}.
     * @throws NullPointerException When {@link Converter#convert(Object, Class) conversion} is mandatory but no
     *                              {@code converter} is given.
     */
    <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter);

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
     * Returns a copy of this {@code Message} (implementation) with the given {@code metaData}.
     * <p>
     * All others fields, like for example the {@link #getPayload()}, remain unchanged.
     * <p/>
     * While the implementation returned may be different from the implementation of {@code this}, implementations must
     * take special care in returning the same type of {@code Message} to prevent errors further downstream.
     *
     * @param metaData The new metadata for the {@code Message}.
     * @return A copy of {@code this Message (implementation)} with the given {@code metaData}.
     */
    Message<P> withMetaData(@Nonnull Map<String, String> metaData);

    /**
     * Returns a copy of this {@code Message} (implementation) with its {@link Message#getMetaData() metadata} merged
     * with the given {@code metaData}.
     * <p>
     * All others fields, like for example the {@link #getPayload()}, remain unchanged.
     *
     * @param metaData The metadata to merge with.
     * @return A copy of {@code this Message (implementation)} with the given {@code metaData}.
     */
    Message<P> andMetaData(@Nonnull Map<String, String> metaData);

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
