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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.query.AxonServerNonTransientRemoteQueryHandlingException;
import org.axonframework.axonserver.connector.query.AxonServerRemoteQueryHandlingException;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SubscriptionMessageSerializer}.
 *
 * @author Sara Pellegrini
 */
class SubscriptionMessageSerializerTest {

    private final Serializer xStreamSerializer = TestSerializer.xStreamSerializer();
    private final Serializer jacksonSerializer = JacksonSerializer.defaultSerializer();

    private final AxonServerConfiguration configuration = new AxonServerConfiguration() {{
        this.setClientId("client");
        this.setComponentName("component");
    }};

    private final SubscriptionMessageSerializer testSubject =
            new SubscriptionMessageSerializer(jacksonSerializer, xStreamSerializer, configuration);

    @Test
    void update() {
        List<String> payload = new ArrayList<>();
        payload.add("A");
        payload.add("B");
        SubscriptionQueryUpdateMessage<List<String>> message =
                new GenericSubscriptionQueryUpdateMessage<>(new MessageType("query"), payload);
        QueryUpdate result = testSubject.serialize(message);
        SubscriptionQueryUpdateMessage<Object> deserialized = testSubject.deserialize(result);
        assertEquals(message.identifier(), deserialized.identifier());
        assertEquals(message.payload(), deserialized.payload());
        assertEquals(message.payloadType(), deserialized.payloadType());
        assertEquals(message.metaData(), deserialized.metaData());
    }

    @Test
    void exceptionalUpdate() {
        MetaData metaData = MetaData.with("k1", "v1");
        SubscriptionQueryUpdateMessage<String> message = new GenericSubscriptionQueryUpdateMessage<>(
                new MessageType("query"), new RuntimeException("oops"), metaData, String.class
        );
        QueryUpdate result = testSubject.serialize(message);
        SubscriptionQueryUpdateMessage<Object> deserialized = testSubject.deserialize(result);
        assertEquals(message.identifier(), deserialized.identifier());
        assertEquals(ErrorCode.QUERY_EXECUTION_ERROR.errorCode(), result.getErrorCode());
        assertEquals(message.metaData(), deserialized.metaData());
        assertTrue(deserialized.isExceptional());
        assertEquals("oops", deserialized.exceptionResult().getMessage());
        assertInstanceOf(AxonServerRemoteQueryHandlingException.class, deserialized.exceptionResult().getCause());
    }

    @Test
    void nonTransientExceptionalUpdate() {
        MetaData metaData = MetaData.with("k1", "v1");
        SubscriptionQueryUpdateMessage<String> message = new GenericSubscriptionQueryUpdateMessage<>(
                new MessageType("query"), new SerializationException("oops"), metaData, String.class
        );
        QueryUpdate result = testSubject.serialize(message);
        assertEquals(ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR.errorCode(), result.getErrorCode());
        SubscriptionQueryUpdateMessage<Object> deserialized = testSubject.deserialize(result);
        assertEquals(message.identifier(), deserialized.identifier());
        assertEquals(message.metaData(), deserialized.metaData());
        assertTrue(deserialized.isExceptional());
        assertEquals("oops", deserialized.exceptionResult().getMessage());
        assertInstanceOf(AxonServerNonTransientRemoteQueryHandlingException.class,
                         deserialized.exceptionResult().getCause());
    }
}
