/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.query.subscription;

import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QueryUpdate;
import io.axoniq.axonhub.QueryUpdateCompleteExceptionally;
import io.axoniq.axonhub.SubscriptionQuery;
import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.queryhandling.responsetypes.ResponseTypes.instanceOf;
import static org.junit.Assert.*;

/**
 * Created by Sara Pellegrini on 27/06/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscriptionMessageSerializerTest {

    private final Serializer xStreamSerializer = new XStreamSerializer();

    private final Serializer jacksonSerializer = new JacksonSerializer();

    private final AxonHubConfiguration configuration = new AxonHubConfiguration() {{
        this.setClientName("client");
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
        QueryResponse initialResponse = grpcMessage.getSubscriptionQueryResponse().getInitialResponse();
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
        assertEquals("Error",completeExceptionally.getMessage().getMessage());

    }
}