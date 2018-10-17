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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sara Pellegrini on 28/06/2018.
 * sara.pellegrini@gmail.com
 */
public class QuerySerializerTest {

    private final Serializer xStreamSerializer = XStreamSerializer.builder().build();

    private final Serializer jacksonSerializer = JacksonSerializer.builder().build();

    private final AxonServerConfiguration configuration = new AxonServerConfiguration() {{
        this.setClientId("client");
        this.setComponentName("component");
    }};

    private final QuerySerializer testSubject = new QuerySerializer(jacksonSerializer, xStreamSerializer, configuration);

    @Test
    public void testSerializeRequest() {
        QueryMessage<String, Integer> message = new GenericQueryMessage<>("Test", "MyQueryName", instanceOf(int.class));
        QueryRequest queryRequest = testSubject.serializeRequest(message, 5, 10, 1);
        QueryMessage<Object, Object> deserialized = testSubject.deserializeRequest(queryRequest);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getQueryName(), deserialized.getQueryName());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertTrue(message.getResponseType().matches(deserialized.getResponseType().responseMessagePayloadType()));
        assertEquals(message.getPayload(), deserialized.getPayload());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
    }

    @Test
    public void testSerializeResponse() {
        Map<String, ?> metadata = new HashMap<String, Object>() {{
            this.put("firstKey", "firstValue");
            this.put("secondKey", "secondValue");
        }};
        QueryResponseMessage message = new GenericQueryResponseMessage<>(BigDecimal.class, BigDecimal.ONE, metadata);
        QueryResponse grpcMessage = testSubject.serializeResponse(message, "requestMessageId");
        QueryResponseMessage<Object> deserialized = testSubject.deserializeResponse(grpcMessage);
        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getPayload(), deserialized.getPayload());
    }

    @Test
    public void testSerializeExceptionalResponse() {
        RuntimeException exception = new RuntimeException("oops");
        GenericQueryResponseMessage responseMessage = new GenericQueryResponseMessage<>(
                String.class,
                exception,
                MetaData.with("test", "testValue"));

        QueryResponse outbound = testSubject.serializeResponse(responseMessage, "requestIdentifier");
        QueryResponseMessage deserialize = testSubject.deserializeResponse(outbound);

        assertEquals(responseMessage.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
    }

}
