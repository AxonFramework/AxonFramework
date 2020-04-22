/*
 * Copyright (c) 2010-2019. Axon Framework
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
import org.axonframework.axonserver.connector.utils.SerializerParameterResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by Sara Pellegrini on 28/06/2018.
 * sara.pellegrini@gmail.com
 */
class QuerySerializerTest {

    public static Stream<QuerySerializer> provideQuerySerializer() {
        AxonServerConfiguration configuration = new AxonServerConfiguration() {{
            this.setClientId("client");
            this.setComponentName("component");
        }};

        return SerializerParameterResolver.serializerStream()
                .map(serializer -> new QuerySerializer(serializer, serializer, configuration));
    }

    @ParameterizedTest
    @MethodSource("provideQuerySerializer")
    void testSerializeRequest(QuerySerializer testSubject) {
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

    @ParameterizedTest
    @MethodSource("provideQuerySerializer")
    void testSerializeResponse(QuerySerializer testSubject) {
        Map<String, ?> metadata = new HashMap<String, Object>() {{
            this.put("firstKey", "firstValue");
            this.put("secondKey", "secondValue");
        }};
        QueryResponseMessage message = new GenericQueryResponseMessage<>(BigDecimal.class, BigDecimal.ONE, metadata);
        QueryResponse grpcMessage = testSubject.serializeResponse(message, "requestMessageId");
        QueryResponseMessage<BigDecimal> deserialized = testSubject.deserializeResponse(grpcMessage, instanceOf(BigDecimal.class));

        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getPayload(), deserialized.getPayload());
    }

    @ParameterizedTest
    @MethodSource("provideQuerySerializer")
    void testSerializeExceptionalResponse(QuerySerializer testSubject) {
        RuntimeException exception = new RuntimeException("oops");
        GenericQueryResponseMessage responseMessage = new GenericQueryResponseMessage<>(
                String.class,
                exception,
                MetaData.with("test", "testValue"));

        QueryResponse outbound = testSubject.serializeResponse(responseMessage, "requestIdentifier");
        QueryResponseMessage deserialize = testSubject.deserializeResponse(outbound, instanceOf(String.class));

        assertEquals(responseMessage.getIdentifier(), deserialize.getIdentifier());
        assertEquals(responseMessage.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
    }

    @ParameterizedTest
    @MethodSource("provideQuerySerializer")
    void testSerializeExceptionalResponseWithDetails(QuerySerializer testSubject) {
        Exception exception = new QueryExecutionException("oops", null, "Details");
        GenericQueryResponseMessage responseMessage = new GenericQueryResponseMessage<>(
                String.class,
                exception,
                MetaData.with("test", "testValue"));

        QueryResponse outbound = testSubject.serializeResponse(responseMessage, "requestIdentifier");
        QueryResponseMessage<?> deserialize = testSubject.deserializeResponse(outbound, instanceOf(String.class));

        assertEquals(responseMessage.getIdentifier(), deserialize.getIdentifier());
        assertEquals(responseMessage.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
        Throwable actual = deserialize.optionalExceptionResult().get();
        assertTrue(actual instanceof QueryExecutionException);
        assertEquals("Details", ((QueryExecutionException) actual).getDetails().orElse("None"));
    }

}
