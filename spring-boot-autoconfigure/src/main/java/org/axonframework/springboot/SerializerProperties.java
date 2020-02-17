/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A group of properties that allows easy configuration of the serializers used for different types of objects that
 * Axon needs to serialize.
 * <p>
 * The Event serializer, if specified, is used to serialize the payload and meta-data of EventMessages as they are
 * stored in the event store and published. If no Event Serializer is specified, it defaults to the Message Serializer.
 * <p>
 * The Message Serializer is used to serialize the payload and meta-data of all messages and, where relevant, their
 * return values. Commands, Queries and Events, but also the Command Result and Query Responses are serialized using
 * this serializer. If no Message Serializer is specified, it defaults to the General Serializer.
 * <p>
 * The General Serializer is used to serialize the rest of the objects, such as TrackingTokens, Saga state,
 * Snapshot Events and all other data that needs to be serialized, but is not expected to be shared with other systems.
 * If no General Serializer is specified, it defaults to an XStream based serializer (see
 * {@link org.axonframework.serialization.xml.XStreamSerializer}).
 */
@ConfigurationProperties("axon.serializer")
public class SerializerProperties {

    /**
     * The type of serializer to use to serialize any type of object, for which no more specific serializer is
     * configured. Defaults to an XStream based serializer.
     */
    private SerializerType general = SerializerType.DEFAULT;

    /**
     * The type of serializer to use to serialize the payload and meta data of Event Messages. Defaults to the Message
     * Serializer, or otherwise the General Serializer.
     */
    private SerializerType events = SerializerType.DEFAULT;

    /**
     * The type of serializer to use to serialize the payload and meta data of any type of Message as well as their
     * responses. Defaults to the General Serializer.
     */
    private SerializerType messages = SerializerType.DEFAULT;

    /**
     * The type of serializer to use to serialize any type of object, for which no more specific serializer is
     * configured. Defaults to an XStream based serializer.
     *
     * @return The serializer to use for serialization of all kinds of objects
     */
    public SerializerType getGeneral() {
        return general;
    }

    /**
     * Sets the type of serializer to use to serialize any type of object, for which no more specific serializer is
     * configured. Defaults to an XStream based serializer.
     *
     * @param serializerType The serializer to use for serialization of all kinds of objects.
     */
    public void setGeneral(SerializerType serializerType) {
        this.general = serializerType;
    }

    /**
     * The type of serializer to use to serialize the payload and meta data of Event Messages. Defaults to the Message
     * Serializer, or otherwise the General Serializer.
     *
     * @return The type of serializer to use for Event Messages.
     */
    public SerializerType getEvents() {
        return events;
    }

    /**
     * The type of serializer to use to serialize the payload and meta data of Event Messages. Defaults to the Message
     * Serializer, or otherwise the General Serializer.
     *
     * @param serializerType The type of serializer to use for Event Messages.
     */
    public void setEvents(SerializerType serializerType) {
        this.events = serializerType;
    }

    /**
     * The type of serializer to use to serialize the payload and meta data of Messages. Defaults to the General
     * Serializer.
     *
     * @return The type of serializer to use for Messages.
     */
    public SerializerType getMessages() {
        return messages;
    }

    /**
     * The type of serializer to use to serialize the payload and meta data of Messages. Defaults to the Message
     * Serializer, or otherwise the General Serializer.
     *
     * @param serializerType The type of serializer to use for Messages.
     */
    public void setMessages(SerializerType serializerType) {
        this.messages = serializerType;
    }

    /**
     * Enumerates different possible standard serializers, available in Axon.
     */
    public enum SerializerType {
        /**
         * Uses the XStream based serializer, which is the default serializer. This serializer will serialize an object
         * into XML. The serialized format is not highly interoperable, but the XStream based serializer is capable
         * of serializing just about any object. This makes it a very suitable implementation to use as the "general
         * serializer".
         */
        XSTREAM,
        /**
         * Uses Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper} to serialize objects into JSON. Provides
         * highly interoperable JSON output, but does require the objects to adhere to a certain structure. The Jackson
         * based serializer is generally suitable as a Message Serializer.
         */
        JACKSON,
        /**
         * Uses the Java Serialization API (see {@link java.io.ObjectOutputStream}) to write objects. The Java
         * serializer's output is not interoperable and should really be only used in very specific cases. It is not
         * recommended to use this serializer as an Event Serializer, as it could cause Events to be stored in format
         * that is difficult to upcast when the structure of Events changes.
         */
        JAVA,
        /**
         * Defines that the default serializer should be used. For "general serializer", this means an XStream based
         * serializer. For the "message serializer", this means the "general serializer" is used. Similarly, the "event
         * serializer" will default to the "message serializer" (or the "general serializer").
         */
        DEFAULT
    }

}
