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

package org.axonframework.springboot;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A group of properties that allows easy configuration of the
 * {@link org.axonframework.serialization.Converter Converters} used for different types of objects that Axon needs to
 * convert.
 * <p>
 * The <b>general</b> {@code Converter} forms the baseline and is used to convert all objects that do not fall into more
 * specific categories. Typical examples include snapshots and other internal data that is not expected to be shared
 * with other systems. If no general {@code Converter} is specified, Axon defaults to the
 * {@link org.axonframework.serialization.json.JacksonConverter}.
 * <p>
 * The <b>messages</b> {@code Converter}, if specified, takes precedence over the general converter for all
 * {@link org.axonframework.messaging.Message Messages} and, where relevant, their return values. This includes
 * commands, queries, and events, as well as
 * {@link org.axonframework.commandhandling.CommandResultMessage CommandResultMessages} and
 * {@link org.axonframework.queryhandling.QueryResponseMessage QueryResponseMessages}. To ensure the messages
 * {@code Converter} only applies to messages, it is wrapped in a
 * {@link org.axonframework.messaging.conversion.DelegatingMessageConverter DelegatingMessageConverter}. When no message
 * {@code Converter} is specified, it defaults back to the general converter.
 * <p>
 * The <b>events</b> {@code Converter}, if specified, takes precedence over both the messages and general converters,
 * but applies <i>only</i> to the {@link org.axonframework.eventhandling.EventMessage EventMessages} stored in the event
 * store and published on the event bus. To ensure it only applies to event messages, it is wrapped in a
 * {@link org.axonframework.eventhandling.conversion.DelegatingEventConverter DelegatingEventConverter}. When no event
 * {@code Converter} is specified, it defaults back to the <b>messages</b> converter.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.2.0
 */
@ConfigurationProperties("axon.converter")
public class ConverterProperties {

    /**
     * The type of {@link org.axonframework.serialization.Converter} to use to convert any type for which no more
     * specific {@code Converter} is configured.
     * <p>
     * Defaults to the {@link ConverterType#DEFAULT default ConverterType}.
     */
    private ConverterType general = ConverterType.DEFAULT;

    /**
     * The type of {@link org.axonframework.serialization.Converter} to use to convert the {@link Message#payload()} of
     * any type of {@link org.axonframework.messaging.Message}.
     * <p>
     * The constructed {@code Converter} will <b>always</b> be wrapped in a {@link DelegatingMessageConverter}, ensuring
     * the {@code Converter}
     * <b>only</b> deals with {@code Messages}.
     * <p>
     * Defaults to the <b>general</b> {@code Converter}.
     */
    private ConverterType messages = ConverterType.DEFAULT;

    /**
     * The type of {@link org.axonframework.serialization.Converter} to use to convert the
     * {@link EventMessage#payload()} of {@link org.axonframework.eventhandling.EventMessage EventMessages}.
     * <p>
     * The constructed {@code Converter} will <b>always</b> be wrapped in a {@link DelegatingEventConverter}, ensuring
     * the {@code Converter}
     * <b>only</b> deals with {@code EventMessages}.
     * <p>
     * Defaults to the <b>messages</b> {@code Converter} when set, or otherwise the <b>general</b> {@code Converter}.
     */
    private ConverterType events = ConverterType.DEFAULT;

    /**
     * The type of {@link org.axonframework.serialization.Converter} to use to convert any type for which no more
     * specific {@code Converter} is configured.
     * <p>
     * Defaults to the {@link ConverterType#DEFAULT default ConverterType}.
     *
     * @return The {@link org.axonframework.serialization.Converter} type to use for serialization of all kinds of
     * objects
     */
    @Nonnull
    public ConverterType getGeneral() {
        return general;
    }

    /**
     * Sets the type of {@link org.axonframework.serialization.Converter} to use to convert any type for which no more
     * specific {@code Converter} is configured.
     * <p>
     * Defaults to the {@link ConverterType#DEFAULT default ConverterType}.
     *
     * @param converterType The converter type to use for converting any object.
     */
    public void setGeneral(@Nonnull ConverterType converterType) {
        this.general = converterType;
    }

    /**
     * The type of {@link org.axonframework.serialization.Converter} to use to convert the {@link Message#payload()} of
     * any type of {@link org.axonframework.messaging.Message}.
     * <p>
     * The constructed {@code Converter} will <b>always</b> be wrapped in a {@link DelegatingMessageConverter}, ensuring
     * the {@code Converter}
     * <b>only</b> deals with {@code Messages}.
     * <p>
     * Defaults to the {@link #getGeneral() <b>general</b> Converter}.
     *
     * @return The type of {@link org.axonframework.serialization.Converter} to use for
     * {@link Message#payload() Message payloads}.
     */
    @Nonnull
    public ConverterType getMessages() {
        return messages;
    }

    /**
     * Sets the type of {@link org.axonframework.serialization.Converter} to use to convert the
     * {@link Message#payload()} of any type of {@link org.axonframework.messaging.Message}.
     * <p>
     * The constructed {@code Converter} will <b>always</b> be wrapped in a {@link DelegatingMessageConverter}, ensuring
     * the {@code Converter}
     * <b>only</b> deals with {@code Messages}.
     * <p>
     * Defaults to the <b>general</b> {@code Converter}.
     *
     * @param converterType The converter type to use for converting any {@link org.axonframework.messaging.Message}
     *                      {@link Message#payload()}.
     */
    public void setMessages(@Nonnull ConverterType converterType) {
        this.messages = converterType;
    }

    /**
     * The type of {@link org.axonframework.serialization.Converter} to use to convert the
     * {@link EventMessage#payload()} of {@link org.axonframework.eventhandling.EventMessage EventMessages}.
     * <p>
     * The constructed {@code Converter} will <b>always</b> be wrapped in a {@link DelegatingEventConverter}, ensuring
     * the {@code Converter}
     * <b>only</b> deals with {@code EventMessages}.
     * <p>
     * Defaults to the <b>messages</b> {@code Converter} when set, or otherwise the <b>general</b> {@code Converter}.
     *
     * @return The type of {@link org.axonframework.serialization.Converter} to use for
     * {@link EventMessage#payload() EventMessage payloads}.
     */
    @Nonnull
    public ConverterType getEvents() {
        return events;
    }

    /**
     * Sets the type of {@link org.axonframework.serialization.Converter} to use to convert the
     * {@link EventMessage#payload()} of {@link org.axonframework.eventhandling.EventMessage EventMessages}.
     * <p>
     * The constructed {@code Converter} will <b>always</b> be wrapped in a {@link DelegatingEventConverter}, ensuring
     * the {@code Converter}
     * <b>only</b> deals with {@code EventMessages}.
     * <p>
     * Defaults to the <b>messages</b> {@code Converter} when set, or otherwise the <b>general</b> {@code Converter}.
     *
     * @param converterType The converter type to use for converting any
     *                      {@link org.axonframework.eventhandling.EventMessage} {@link EventMessage#payload()}.
     */
    public void setEvents(@Nonnull ConverterType converterType) {
        this.events = converterType;
    }

    /**
     * Enumerates different possible standard {@link org.axonframework.serialization.Converter Converters} available in
     * Axon Framework.
     */
    public enum ConverterType {
        /**
         * Uses Avro-based {@link org.axonframework.serialization.Converter} to convert objects into bytes as specified
         * by Avro Specification using single object encoding.
         */
        AVRO,
        /**
         * Uses Jackson's {@link com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper} to convert objects into
         * CBOR.
         * <p>
         * This format is not human-readable, but can save on the size of (e.g.) {@link Message Messages}. When using
         * this {@link org.axonframework.serialization.Converter}, make sure your tables are BLOBs, not CLOBs, as
         * converting the {@code byte[]} to a {@code String} will corrupt the data.
         */
        CBOR,
        /**
         * Uses Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper} to convert objects into JSON.
         * <p>
         * Provides highly interoperable JSON output, but does require the objects to adhere to a certain structure. The
         * Jackson based serializer is generally suitable as a <b>messages</b>
         * {@link org.axonframework.serialization.Converter}.
         */
        JACKSON,
        /**
         * Defines that the default serializer should be used.
         * <p>
         * For the <b>general</b> {@link org.axonframework.serialization.Converter}, this means the
         * {@link org.axonframework.serialization.json.JacksonConverter} is used. For the <b>messages</b>
         * {@code Converter}, this means the <b>general</b> {@code Converter} is used. Similarly, the <b>events</b>
         * {@code Converter} will default to the <b>messages</b> {@code Converter} (or the <b>general</b>
         * {@code Converter} if the <b>messages</b> {@code Converter} has not been specified).
         */
        DEFAULT
    }
}
