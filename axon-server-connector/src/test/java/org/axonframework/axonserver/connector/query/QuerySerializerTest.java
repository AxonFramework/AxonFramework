/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link QuerySerializer}.
 *
 * @author Sara Pellegrini
 */
class QuerySerializerTest {

    private final Serializer xStreamSerializer = TestSerializer.xStreamSerializer();
    private final Serializer jacksonSerializer = JacksonSerializer.defaultSerializer();

    private final AxonServerConfiguration configuration = new AxonServerConfiguration() {{
        this.setClientId("client");
        this.setComponentName("component");
    }};

    private final QuerySerializer testSubject =
            new QuerySerializer(jacksonSerializer, xStreamSerializer, configuration);

    @Test
    void serializeRequest() {
        QueryMessage<String, Integer> message =
                new GenericQueryMessage<>(QualifiedNameUtils.fromDottedName("test.query"), "MyQueryName", "Test", instanceOf(int.class));
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
    void serializeResponse() {
        Map<String, ?> metadata = new HashMap<>() {{
            this.put("firstKey", "firstValue");
            this.put("secondKey", "secondValue");
        }};
        QueryResponseMessage<BigDecimal> message =
                new GenericQueryResponseMessage<>(QualifiedNameUtils.fromDottedName("test.query"), BigDecimal.ONE, metadata, BigDecimal.class);
        QueryResponse grpcMessage = testSubject.serializeResponse(message, "requestMessageId");
        QueryResponseMessage<BigDecimal> deserialized =
                testSubject.deserializeResponse(grpcMessage, instanceOf(BigDecimal.class));

        assertEquals(message.getIdentifier(), deserialized.getIdentifier());
        assertEquals(message.getMetaData(), deserialized.getMetaData());
        assertEquals(message.getPayloadType(), deserialized.getPayloadType());
        assertEquals(message.getPayload(), deserialized.getPayload());
    }

    @Test
    void serializeExceptionalResponse() {
        RuntimeException exception = new RuntimeException("oops");
        QueryResponseMessage<String> responseMessage = new GenericQueryResponseMessage<>(
                QualifiedNameUtils.fromDottedName("test.query"), exception, MetaData.with("test", "testValue"), String.class
        );

        QueryResponse outbound = testSubject.serializeResponse(responseMessage, "requestIdentifier");
        QueryResponseMessage<String> deserialize = testSubject.deserializeResponse(outbound, instanceOf(String.class));

        assertEquals(ErrorCode.QUERY_EXECUTION_ERROR.errorCode(), outbound.getErrorCode());
        assertEquals(responseMessage.getIdentifier(), deserialize.getIdentifier());
        assertEquals(responseMessage.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
        assertTrue(deserialize.exceptionResult().getCause() instanceof AxonServerRemoteQueryHandlingException);
    }

    @Test
    void serializeDeserializeNonTransientExceptionalResponse() {
        SerializationException exception = new SerializationException("oops");
        QueryResponseMessage<String> responseMessage = new GenericQueryResponseMessage<>(
                QualifiedNameUtils.fromDottedName("test.query"), exception, MetaData.with("test", "testValue"), String.class
        );

        QueryResponse outbound = testSubject.serializeResponse(responseMessage, "requestIdentifier");
        QueryResponseMessage<?> deserialize = testSubject.deserializeResponse(outbound, instanceOf(String.class));

        assertEquals(ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR.errorCode(), outbound.getErrorCode());
        assertEquals(responseMessage.getIdentifier(), deserialize.getIdentifier());
        assertEquals(responseMessage.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
        assertTrue(deserialize.exceptionResult()
                              .getCause() instanceof AxonServerNonTransientRemoteQueryHandlingException);
    }

    @Test
    void serializeExceptionalResponseWithDetails() {
        Exception exception = new QueryExecutionException("oops", null, "Details");
        QueryResponseMessage<String> responseMessage = new GenericQueryResponseMessage<>(
                QualifiedNameUtils.fromDottedName("test.query"), exception, MetaData.with("test", "testValue"), String.class
        );

        QueryResponse outbound = testSubject.serializeResponse(responseMessage, "requestIdentifier");
        QueryResponseMessage<?> deserialize = testSubject.deserializeResponse(outbound, instanceOf(String.class));

        assertEquals(responseMessage.getIdentifier(), deserialize.getIdentifier());
        assertEquals(responseMessage.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
        Throwable actual = deserialize.optionalExceptionResult().get();
        assertInstanceOf(QueryExecutionException.class, actual);
        assertEquals("Details", ((QueryExecutionException) actual).getDetails().orElse("None"));
    }
}
