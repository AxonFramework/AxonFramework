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

package org.axonframework.axonserver.connector.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GrpcBackedResponseMessageTest {

    private static final TestQueryResponse TEST_QUERY_RESPONSE = new TestQueryResponse("aggregateId", 42);
    private static final String REQUEST_MESSAGE_ID = "request-message-id";

    private final Serializer serializer = JacksonSerializer.defaultSerializer();
    private final QuerySerializer querySerializer =
            new QuerySerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void identifierAsSpecifiedInTheQueryResponseMessage() {
        QueryResponseMessage testQueryResponseMessage = asResponseMessage(TEST_QUERY_RESPONSE);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(testQueryResponse.getMessageIdentifier(), testSubject.identifier());
    }

    @Test
    void metadataReturnsTheSameMapAsWasInsertedInTheQueryResponseMessage() {
        Metadata expectedMetadata = Metadata.with("some-key", "some-value");
        QueryResponseMessage testQueryResponseMessage =
                GrpcBackedResponseMessageTest.<TestQueryResponse>asResponseMessage(TEST_QUERY_RESPONSE)
                        .withMetadata(expectedMetadata);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(expectedMetadata, testSubject.metadata());
    }

    @Test
    void payloadReturnsAnIdenticalObjectAsInsertedThroughTheQueryResponseMessage() {
        TestQueryResponse expectedQuery = TEST_QUERY_RESPONSE;
        QueryResponseMessage testQueryResponseMessage = asResponseMessage(expectedQuery);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(expectedQuery, testSubject.payload());
    }

    @Test
    void getPayloadThrowIllegalPayloadExceptionIfTheQueryResponseMessageDidNotContainAnyPayload() {
        QueryResponseMessage testQueryResponseMessage =
                asResponseMessage(
                        TestQueryResponse.class, new IllegalArgumentException("some-exception")
                );
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertThrows(IllegalPayloadAccessException.class, testSubject::payload);
    }

    @Test
    void payloadTypeReturnsTheTypeOfTheInsertedQueryResponseMessage() {
        QueryResponseMessage testQueryResponseMessage = asResponseMessage(TEST_QUERY_RESPONSE);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(TestQueryResponse.class, testSubject.payloadType());
    }

    @Test
    void getPayloadTypeReturnsNullIfTheQueryResponseMessageDidNotContainAnyPayload() {
        QueryResponseMessage testQueryResponseMessage =
                asResponseMessage(
                        TestQueryResponse.class, new IllegalArgumentException("some-exception")
                );
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertNull(testSubject.payloadType());
    }

    @Test
    void isExceptionalReturnsTrueForAnExceptionalQueryResponseMessage() {
        QueryResponseMessage testQueryResponseMessage =
                asResponseMessage(
                        TestQueryResponse.class, new IllegalArgumentException("some-exception")
                );
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertTrue(testSubject.isExceptional());
    }

    @Test
    void optionalExceptionResultReturnsTheExceptionAsAsInsertedThroughTheQueryResponseMessage() {
        IllegalArgumentException expectedException = new IllegalArgumentException("some-exception");
        QueryResponseMessage testQueryResponseMessage =
                asResponseMessage(TestQueryResponse.class, expectedException);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        Optional<Throwable> result = testSubject.optionalExceptionResult();
        assertTrue(result.isPresent());
        assertEquals(expectedException.getMessage(), result.get().getMessage());
    }

    @Test
    void withMetadataCompletelyReplacesTheInitialMetadataMap() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        QueryResponseMessage testQueryResponseMessage =
                GrpcBackedResponseMessageTest.<TestQueryResponse>asResponseMessage(TEST_QUERY_RESPONSE)
                        .withMetadata(testMetadata);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        Metadata replacementMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetadata(replacementMetadata);
        Metadata resultMetadata = testSubject.metadata();
        assertFalse(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertEquals(replacementMetadata, resultMetadata);
    }

    @Test
    void andMetadataAppendsToTheExistingMetadata() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        QueryResponseMessage testQueryResponseMessage =
                GrpcBackedResponseMessageTest.<TestQueryResponse>asResponseMessage(TEST_QUERY_RESPONSE)
                        .withMetadata(testMetadata);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        Metadata additionalMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetadata(additionalMetadata);
        Metadata resultMetadata = testSubject.metadata();

        assertTrue(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertTrue(resultMetadata.containsKey(additionalMetadata.keySet().iterator().next()));
    }

    private static <R> QueryResponseMessage asResponseMessage(Class<R> declaredType, Throwable exception) {
        return new GenericQueryResponseMessage(new MessageType(exception.getClass()),
                exception,
                declaredType);
    }

    @SuppressWarnings("unchecked")
    private static <R> QueryResponseMessage asResponseMessage(Object result) {
        if (result instanceof QueryResponseMessage) {
            return (QueryResponseMessage) result;
        } else if (result instanceof ResultMessage) {
            ResultMessage resultMessage = (ResultMessage) result;
            return new GenericQueryResponseMessage(
                    new MessageType(resultMessage.payload().getClass()),
                    resultMessage.payload(),
                    resultMessage.metadata()
            );
        } else if (result instanceof Message) {
            Message message = (Message) result;
            return new GenericQueryResponseMessage(new MessageType(message.payload().getClass()),
                                                     message.payload(),
                                                     message.metadata());
        } else {
            return new GenericQueryResponseMessage(new MessageType(result.getClass()), result);
        }
    }

    private static class TestQueryResponse {

        private final String queryModelId;
        private final int someFilterValue;

        private TestQueryResponse(@JsonProperty("queryModelId") String queryModelId,
                                  @JsonProperty("someFilterValue") int someFilterValue) {
            this.queryModelId = queryModelId;
            this.someFilterValue = someFilterValue;
        }

        @SuppressWarnings("unused")
        public String getQueryModelId() {
            return queryModelId;
        }

        @SuppressWarnings("unused")
        public int getSomeFilterValue() {
            return someFilterValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestQueryResponse that = (TestQueryResponse) o;
            return someFilterValue == that.someFilterValue &&
                    Objects.equals(queryModelId, that.queryModelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queryModelId, someFilterValue);
        }
    }
}