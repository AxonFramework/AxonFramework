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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.*;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.queryhandling.*;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sara Pellegrini on 27/06/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscriptionMessageSerializerTest {

    private final Serializer xStreamSerializer = XStreamSerializer.builder().build();

    private final Serializer jacksonSerializer = JacksonSerializer.builder().build();

    private final AxonServerConfiguration configuration = new AxonServerConfiguration() {{
        this.setClientId("client");
        this.setComponentName("component");
    }};

    private final SubscriptionMessageSerializer testSubject = new SubscriptionMessageSerializer(configuration,
                                                                                                jacksonSerializer,
                                                                                                xStreamSerializer);


    @Test
    public void testInitialResponse() {
        Map<String, ?> metadata = new HashMap<String, Object>() {{
            this.put("firstKey", "firstValue");
            this.put("secondKey", "secondValue");
        }};
        QueryResponseMessage message = new GenericQueryResponseMessage<>(String.class, "Result", metadata);
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
    public void testUpdate() {
        List<String> payload = new ArrayList<>();
        payload.add("A");
        payload.add("B");
        SubscriptionQueryUpdateMessage message = new GenericSubscriptionQueryUpdateMessage<>(payload);
        QueryProviderOutbound grpcMessage = testSubject.serialize(message, "subscriptionId");
        assertEquals("subscriptionId", grpcMessage.getSubscriptionQueryResponse().getSubscriptionIdentifier());
        QueryUpdate update = grpcMessage.getSubscriptionQueryResponse().getUpdate();
        SubscriptionQueryUpdateMessage<Object> deserialized = testSubject.deserialize(update);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getPayload(), deserialized.getPayload());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
    }

    @Test
    public void testSubscriptionQueryMessage() {
        GenericSubscriptionQueryMessage<String, Integer, Integer> message = new GenericSubscriptionQueryMessage<>(
                "query",
                "MyQueryName",
                instanceOf(int.class),
                instanceOf(int.class));
        SubscriptionQuery grpcMessage = testSubject.serialize(message);
        SubscriptionQueryMessage<Object, Object, Object> deserialized = testSubject.deserialize(grpcMessage);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getPayload(), deserialized.getPayload());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertEquals(message.getQueryName(), deserialized.getQueryName());
        assertTrue(message.getResponseType().matches(deserialized.getResponseType().responseMessagePayloadType()));
        assertTrue(message.getUpdateResponseType().matches(deserialized.getUpdateResponseType().responseMessagePayloadType()));
    }

    @Test
    public void testComplete() {
        QueryProviderOutbound grpcMessage = testSubject.serializeComplete("subscriptionId");
        assertEquals("subscriptionId", grpcMessage.getSubscriptionQueryResponse().getSubscriptionIdentifier());
    }

    @Test
    public void testCompleteExceptionally() {
        QueryProviderOutbound grpcMessage = testSubject.serializeCompleteExceptionally("subscriptionId",
                                                                                       new RuntimeException("Error"));
        SubscriptionQueryResponse subscriptionQueryResponse = grpcMessage.getSubscriptionQueryResponse();
        assertEquals("subscriptionId", subscriptionQueryResponse.getSubscriptionIdentifier());
        QueryUpdateCompleteExceptionally completeExceptionally = subscriptionQueryResponse.getCompleteExceptionally();
        assertEquals("Error",completeExceptionally.getErrorMessage().getMessage());

    }
}
