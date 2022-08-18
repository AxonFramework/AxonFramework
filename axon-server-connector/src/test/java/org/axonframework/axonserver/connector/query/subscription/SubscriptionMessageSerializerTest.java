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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.QueryUpdateCompleteExceptionally;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
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
    void testInitialResponse() {
        MetaData metadata = MetaData.with("firstKey", "firstValue")
                                    .mergedWith(MetaData.with("secondKey", "secondValue"));
        QueryResponseMessage<String> message = new GenericQueryResponseMessage<>(String.class, "Result", metadata);
        QueryProviderOutbound grpcMessage = testSubject.serialize(message, "subscriptionId");
        assertEquals("subscriptionId", grpcMessage.getSubscriptionQueryResponse().getSubscriptionIdentifier());
        QueryResponse initialResponse = grpcMessage.getSubscriptionQueryResponse().getInitialResult();
        QueryResponseMessage<Object> deserialized = testSubject.deserialize(initialResponse);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getPayload(), deserialized.getPayload());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
    }

    @Test
    void testExceptionalInitialResponse() {
        MetaData metadata = MetaData.with("firstKey", "firstValue")
                                    .mergedWith(MetaData.with("secondKey", "secondValue"));
        QueryResponseMessage<String> message = new GenericQueryResponseMessage<>(String.class,
                                                                                 new RuntimeException("oops"),
                                                                                 metadata);
        QueryProviderOutbound grpcMessage = testSubject.serialize(message, "subscriptionId");
        assertEquals("subscriptionId", grpcMessage.getSubscriptionQueryResponse().getSubscriptionIdentifier());
        QueryResponse initialResponse = grpcMessage.getSubscriptionQueryResponse().getInitialResult();
        QueryResponseMessage<Object> deserialized = testSubject.deserialize(initialResponse);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertTrue(deserialized.isExceptional());
        assertEquals("oops", deserialized.exceptionResult().getMessage());
    }

    @Test
    void testUpdate() {
        List<String> payload = new ArrayList<>();
        payload.add("A");
        payload.add("B");
        SubscriptionQueryUpdateMessage<List<String>> message = new GenericSubscriptionQueryUpdateMessage<>(payload);
        QueryUpdate result = testSubject.serialize(message);
        SubscriptionQueryUpdateMessage<Object> deserialized = testSubject.deserialize(result);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getPayload(), deserialized.getPayload());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
    }

    @Test
    void testExceptionalUpdate() {
        MetaData metaData = MetaData.with("k1", "v1");
        SubscriptionQueryUpdateMessage<String> message =
                new GenericSubscriptionQueryUpdateMessage<>(String.class, new RuntimeException("oops"), metaData);
        QueryUpdate result = testSubject.serialize(message);
        SubscriptionQueryUpdateMessage<Object> deserialized = testSubject.deserialize(result);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertTrue(deserialized.isExceptional());
        assertEquals("oops", deserialized.exceptionResult().getMessage());
    }

    @Test
    void testSubscriptionQueryMessage() {
        GenericSubscriptionQueryMessage<String, String, Integer> message = new GenericSubscriptionQueryMessage<>(
                "query",
                "MyQueryName",
                instanceOf(String.class),
                instanceOf(int.class));
        SubscriptionQuery grpcMessage = testSubject.serialize(message);
        SubscriptionQueryMessage<Object, Object, Object> deserialized = testSubject.deserialize(grpcMessage);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getPayload(), deserialized.getPayload());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertEquals(message.getQueryName(), deserialized.getQueryName());
        assertTrue(message.getResponseType().matches(deserialized.getResponseType().responseMessagePayloadType()));
        assertTrue(message.getUpdateResponseType()
                          .matches(deserialized.getUpdateResponseType().responseMessagePayloadType()));
    }

    @Test
    void testComplete() {
        QueryProviderOutbound grpcMessage = testSubject.serializeComplete("subscriptionId");
        assertEquals("subscriptionId", grpcMessage.getSubscriptionQueryResponse().getSubscriptionIdentifier());
    }

    @Test
    void testCompleteExceptionally() {
        QueryProviderOutbound grpcMessage = testSubject.serializeCompleteExceptionally("subscriptionId",
                                                                                       new RuntimeException("Error"));
        SubscriptionQueryResponse subscriptionQueryResponse = grpcMessage.getSubscriptionQueryResponse();
        assertEquals("subscriptionId", subscriptionQueryResponse.getSubscriptionIdentifier());
        QueryUpdateCompleteExceptionally completeExceptionally = subscriptionQueryResponse.getCompleteExceptionally();
        assertEquals("Error", completeExceptionally.getErrorMessage().getMessage());
    }
}
