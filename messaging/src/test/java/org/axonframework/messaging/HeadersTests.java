/*
 * Copyright (c) 2010-2021. Axon Framework
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

import com.thoughtworks.xstream.XStream;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.serialization.FixedValueRevisionResolver;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.messaging.Headers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Headers}
 *
 * @author Nakul Mishra
 */
class HeadersTests {

    private Serializer serializer;

    @BeforeEach
    void setUp() {
        serializer = XStreamSerializer.builder()
                                      .xStream(new XStream(new CompactDriver()))
                                      .revisionResolver(new FixedValueRevisionResolver("stub-revision"))
                                      .build();
    }

    @Test
    void messageIdText() {
        assertThat(MESSAGE_ID, is("axon-message-id"));
    }

    @Test
    void messageTypeText() {
        assertThat(MESSAGE_TYPE, is("axon-message-type"));
    }

    @Test
    void messageRevisionText() {
        assertThat(MESSAGE_REVISION, is("axon-message-revision"));
    }

    @Test
    void messageTimeStampText() {
        assertThat(MESSAGE_TIMESTAMP, is("axon-message-timestamp"));
    }

    @Test
    void messageAggregateIdText() {
        assertThat(AGGREGATE_ID, is("axon-message-aggregate-id"));
    }

    @Test
    void messageAggregateSeqText() {
        assertThat(AGGREGATE_SEQ, is("axon-message-aggregate-seq"));
    }

    @Test
    void messageAggregateTypeText() {
        assertThat(AGGREGATE_TYPE, is("axon-message-aggregate-type"));
    }

    @Test
    void messageMetadataText() {
        assertThat(MESSAGE_METADATA, is("axon-metadata"));
    }

    @Test
    void generatingDefaultMessagingHeaders() {
        EventMessage<Object> message = asEventMessage("foo");
        SerializedObject<byte[]> serializedObject = message.serializePayload(serializer, byte[].class);
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put(MESSAGE_ID, message.getIdentifier());
            put(MESSAGE_TYPE, serializedObject.getType().getName());
            put(MESSAGE_REVISION, serializedObject.getType().getRevision());
            put(MESSAGE_TIMESTAMP, message.getTimestamp());
        }};

        assertThat(defaultHeaders(message, serializedObject), is(expected));
    }

    @Test
    void generatingDefaultMessagingHeaders_InvalidSerializedObject() {
        EventMessage<Object> message = asEventMessage("foo");
        assertThrows(IllegalArgumentException.class, () -> defaultHeaders(message, null));
    }

    @Test
    void generatingDomainMessagingHeaders() {
        DomainEventMessage<String> message = domainMessage();
        SerializedObject<byte[]> serializedObject = message.serializePayload(serializer, byte[].class);

        Map<String, Object> expected = new HashMap<String, Object>() {{
            put(MESSAGE_ID, message.getIdentifier());
            put(MESSAGE_TYPE, serializedObject.getType().getName());
            put(MESSAGE_REVISION, serializedObject.getType().getRevision());
            put(MESSAGE_TIMESTAMP, message.getTimestamp());
            put(AGGREGATE_ID, message.getAggregateIdentifier());
            put(AGGREGATE_SEQ, message.getSequenceNumber());
            put(AGGREGATE_TYPE, message.getType());
        }};

        assertThat(defaultHeaders(message, serializedObject), is(expected));
    }

    private GenericDomainEventMessage<String> domainMessage() {
        return new GenericDomainEventMessage<>("Stub", "893612", 1L, "Payload");
    }
}