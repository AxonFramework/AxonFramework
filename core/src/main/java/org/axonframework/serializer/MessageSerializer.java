/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.domain.Message;

/**
 * Wrapper around a serializer that provides {@link SerializationAware} support. This class can either be used as a
 * wrapper around a Serializer, or as a static utility class.
 * <p/>
 * The <code>serializePayload</code> and <code>serializeMetaData</code> methods on this class are aware of
 * SerializationAware messages. When a Message implements that interface, serialization is delegated to that message to
 * allow performance optimizations.
 * <p/>
 * Using this class to serialize the payload and meta-data of Message is preferred over the serialization using the
 * {@link Serializer} class.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MessageSerializer implements Serializer {

    private final Serializer serializer;

    /**
     * Initializes the MessageSerializer as a wrapper around the given serializer.
     *
     * @param serializer the serializer to serialize and deserialize objects with
     */
    public MessageSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Utility method that serializes the payload of the given <code>message</code> using given <code>serializer</code>
     * and <code>expectedRepresentation</code>. This method will verify if the <code>eventMessage</code> is {@link
     * SerializationAware}.
     *
     * @param message                The message containing the payload to serialize
     * @param serializer             The serializer to serialize the payload with
     * @param expectedRepresentation The data type to serialize to
     * @param <T>                    The data type to serialize to
     * @return a Serialized Object containing the serialized for of the message's payload
     */
    public static <T> SerializedObject<T> serializePayload(Message<?> message, Serializer serializer,
                                                           Class<T> expectedRepresentation) {
        if (message instanceof SerializationAware) {
            return ((SerializationAware) message).serializePayload(serializer, expectedRepresentation);
        }
        return serializer.serialize(message.getPayload(), expectedRepresentation);
    }

    /**
     * Utility method that serializes the meta data of the given <code>message</code> using given
     * <code>serializer</code> and <code>expectedRepresentation</code>. This method will verify if the
     * <code>eventMessage</code> is {@link SerializationAware}.
     *
     * @param message                The message containing the meta data to serialize
     * @param serializer             The serializer to serialize the meta data with
     * @param expectedRepresentation The data type to serialize to
     * @param <T>                    The data type to serialize to
     * @return a Serialized Object containing the serialized for of the message's meta data
     */
    public static <T> SerializedObject<T> serializeMetaData(Message<?> message, Serializer serializer,
                                                            Class<T> expectedRepresentation) {
        if (message instanceof SerializationAware) {
            return ((SerializationAware) message).serializeMetaData(serializer, expectedRepresentation);
        }
        return serializer.serialize(message.getMetaData(), expectedRepresentation);
    }

    /**
     * Serialize the payload of given <code>message</code> to the given <code>expectedRepresentation</code>.
     *
     * @param message                The message containing the payload to serialize
     * @param expectedRepresentation The representation of the serialized data
     * @param <T>                    The representation of the serialized data
     * @return A serialized object containing the serialized representation of the message's payload
     */
    public <T> SerializedObject<T> serializePayload(Message<?> message, Class<T> expectedRepresentation) {
        return serializePayload(message, serializer, expectedRepresentation);
    }

    /**
     * Serialize the meta data of given <code>message</code> to the given <code>expectedRepresentation</code>.
     *
     * @param message                The message containing the meta data to serialize
     * @param expectedRepresentation The representation of the serialized data
     * @param <T>                    The representation of the serialized data
     * @return A serialized object containing the serialized representation of the message's meta data
     */
    public <T> SerializedObject<T> serializeMetaData(Message<?> message, Class<T> expectedRepresentation) {
        return serializeMetaData(message, serializer, expectedRepresentation);
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedRepresentation) {
        return serializer.serialize(object, expectedRepresentation);
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return serializer.canSerializeTo(expectedRepresentation);
    }

    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        return serializer.deserialize(serializedObject);
    }

    @Override
    public Class classForType(SerializedType type) {
        return serializer.classForType(type);
    }

    @Override
    public SerializedType typeForClass(Class type) {
        return serializer.typeForClass(type);
    }

    @Override
    public ConverterFactory getConverterFactory() {
        return serializer.getConverterFactory();
    }
}
