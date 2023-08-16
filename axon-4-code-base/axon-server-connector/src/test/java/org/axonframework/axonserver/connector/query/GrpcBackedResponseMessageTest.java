/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GrpcBackedResponseMessageTest {

    private static final TestQueryResponse TEST_QUERY_RESPONSE = new TestQueryResponse("aggregateId", 42);
    private static final String REQUEST_MESSAGE_ID = "request-message-id";

    private final Serializer serializer = TestSerializer.xStreamSerializer();
    private final QuerySerializer querySerializer =
            new QuerySerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void getIdentifierReturnsTheSameIdentifierAsSpecifiedInTheQueryResponseMessage() {
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(TEST_QUERY_RESPONSE);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(testQueryResponse.getMessageIdentifier(), testSubject.getIdentifier());
    }

    @Test
    void getMetaDataReturnsTheSameMapAsWasInsertedInTheQueryResponseMessage() {
        MetaData expectedMetaData = MetaData.with("some-key", "some-value");
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.<TestQueryResponse>asResponseMessage(TEST_QUERY_RESPONSE)
                        .withMetaData(expectedMetaData);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(expectedMetaData, testSubject.getMetaData());
    }

    @Test
    void getPayloadReturnsAnIdenticalObjectAsInsertedThroughTheQueryResponseMessage() {
        TestQueryResponse expectedQuery = TEST_QUERY_RESPONSE;
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(expectedQuery);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(expectedQuery, testSubject.getPayload());
    }

    @Test
    void getPayloadThrowIllegalPayloadExceptionIfTheQueryResponseMessageDidNotContainAnyPayload() {
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(
                        TestQueryResponse.class, new IllegalArgumentException("some-exception")
                );
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertThrows(IllegalPayloadAccessException.class, testSubject::getPayload);
    }

    @Test
    void getPayloadTypeReturnsTheTypeOfTheInsertedQueryResponseMessage() {
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(TEST_QUERY_RESPONSE);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertEquals(TestQueryResponse.class, testSubject.getPayloadType());
    }

    @Test
    void getPayloadTypeReturnsNullIfTheQueryResponseMessageDidNotContainAnyPayload() {
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(
                        TestQueryResponse.class, new IllegalArgumentException("some-exception")
                );
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        assertNull(testSubject.getPayloadType());
    }

    @Test
    void isExceptionalReturnsTrueForAnExceptionalQueryResponseMessage() {
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(
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
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.asResponseMessage(TestQueryResponse.class, expectedException);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        Optional<Throwable> result = testSubject.optionalExceptionResult();
        assertTrue(result.isPresent());
        assertEquals(expectedException.getMessage(), result.get().getMessage());
    }

    @Test
    void withMetaDataCompletelyReplacesTheInitialMetaDataMap() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.<TestQueryResponse>asResponseMessage(TEST_QUERY_RESPONSE)
                        .withMetaData(testMetaData);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        MetaData replacementMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetaData(replacementMetaData);
        MetaData resultMetaData = testSubject.getMetaData();
        assertFalse(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertEquals(replacementMetaData, resultMetaData);
    }

    @Test
    void andMetaDataAppendsToTheExistingMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        QueryResponseMessage<TestQueryResponse> testQueryResponseMessage =
                GenericQueryResponseMessage.<TestQueryResponse>asResponseMessage(TEST_QUERY_RESPONSE)
                        .withMetaData(testMetaData);
        QueryResponse testQueryResponse =
                querySerializer.serializeResponse(testQueryResponseMessage, REQUEST_MESSAGE_ID);
        GrpcBackedResponseMessage<TestQueryResponse> testSubject =
                new GrpcBackedResponseMessage<>(testQueryResponse, serializer);

        MetaData additionalMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetaData(additionalMetaData);
        MetaData resultMetaData = testSubject.getMetaData();

        assertTrue(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertTrue(resultMetaData.containsKey(additionalMetaData.keySet().iterator().next()));
    }

    private static class TestQueryResponse {

        private final String queryModelId;
        private final int someFilterValue;

        private TestQueryResponse(String queryModelId, int someFilterValue) {
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