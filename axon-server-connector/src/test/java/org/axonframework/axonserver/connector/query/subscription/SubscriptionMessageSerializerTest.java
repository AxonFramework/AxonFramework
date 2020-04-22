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
import org.axonframework.axonserver.connector.utils.SerializerParameterResolver;
import org.axonframework.queryhandling.*;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sara Pellegrini
 */
@ExtendWith(SerializerParameterResolver.class)
class SubscriptionMessageSerializerTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionMessageSerializerTest.class);

    private final AxonServerConfiguration configuration = new AxonServerConfiguration() {{
        this.setClientId("client");
        this.setComponentName("component");
    }};

    private SubscriptionMessageSerializer testSubject;

    @BeforeEach
    public void setUp(Serializer serializer, TestInfo testInfo) {
        LOG.info("Running test {} with serializer {}.", testInfo.getDisplayName(), serializer.getClass());

        testSubject = new SubscriptionMessageSerializer(serializer, serializer, configuration);
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testInitialResponse() {
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

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testUpdate() {
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
        // Deserialization is generalization in terms of typing.
        assertTrue(deserialized.getPayloadType().isAssignableFrom(message.getPayloadType()));
        assertEquals(message.getMetaData(), deserialized.getMetaData());
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testSubscriptionQueryMessage() {
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
        assertTrue(message.getUpdateResponseType()
                .matches(deserialized.getUpdateResponseType().responseMessagePayloadType()));
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testComplete() {
        QueryProviderOutbound grpcMessage = testSubject.serializeComplete("subscriptionId");
        assertEquals("subscriptionId", grpcMessage.getSubscriptionQueryResponse().getSubscriptionIdentifier());
    }

    @RepeatedTest(SerializerParameterResolver.SERIALIZER_COUNT)
    void testCompleteExceptionally() {
        QueryProviderOutbound grpcMessage = testSubject.serializeCompleteExceptionally("subscriptionId",
                new RuntimeException("Error"));
        SubscriptionQueryResponse subscriptionQueryResponse = grpcMessage.getSubscriptionQueryResponse();
        assertEquals("subscriptionId", subscriptionQueryResponse.getSubscriptionIdentifier());
        QueryUpdateCompleteExceptionally completeExceptionally = subscriptionQueryResponse.getCompleteExceptionally();
        assertEquals("Error", completeExceptionally.getErrorMessage().getMessage());
    }
}
