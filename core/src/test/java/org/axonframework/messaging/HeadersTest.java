/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.messaging;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.serialization.FixedValueRevisionResolver;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.messaging.Headers.*;
import static org.axonframework.serialization.MessageSerializer.serializePayload;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Nakul Mishra
 * @since 3.0
 */
public class HeadersTest {

    private Serializer serializer;

    @Before
    public void setUp() throws Exception {
        serializer = new XStreamSerializer(new FixedValueRevisionResolver("stub-revision"));
    }

    @Test
    public void test_generatingDefaultMessagingHeaders() {
        EventMessage<Object> message = asEventMessage("foo");
        SerializedObject<byte[]> serializedObject = serializePayload(message, serializer, byte[].class);
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put(MESSAGE_ID, message.getIdentifier());
            put(MESSAGE_TYPE, serializedObject.getType().getName());
            put(MESSAGE_REVISION, serializedObject.getType().getRevision());
            put(MESSAGE_TIMESTAMP, message.getTimestamp());
        }};

        assertThat(Headers.defaultHeaders(message, serializedObject), is(expected));
    }

    @Test
    public void test_generatingDomainMessagingHeaders() {
        DomainEventMessage<String> message = domainMessage();
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put(AGGREGATE_ID, message.getAggregateIdentifier());
            put(AGGREGATE_SEQ, message.getSequenceNumber());
            put(AGGREGATE_TYPE, message.getType());
        }};

        assertThat(Headers.domainHeaders(message), is(expected));
    }

    private GenericDomainEventMessage<String> domainMessage() {
        return new GenericDomainEventMessage<>("Stub", "893612", 1L, "Payload");
    }
}