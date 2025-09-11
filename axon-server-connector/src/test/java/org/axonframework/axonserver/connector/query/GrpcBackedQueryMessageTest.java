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
import io.axoniq.axonserver.grpc.query.QueryRequest;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all the functions provided on the {@link GrpcBackedQueryMessage}. The {@link QueryRequest} to be passed to a
 * GrpcBackedQueryMessage is created by using the {@link QuerySerializer}.
 *
 * @author Steven van Beelen
 */
class GrpcBackedQueryMessageTest {

    private static final TestQuery TEST_QUERY = new TestQuery("aggregateId", 42);
    private static final ResponseType<String> RESPONSE_TYPE = ResponseTypes.instanceOf(String.class);
    private static final int NUMBER_OF_RESULTS = 42;
    private static final int TIMEOUT = 1000;
    private static final int PRIORITY = 1;

    private final Serializer serializer = JacksonSerializer.defaultSerializer();
    private final QuerySerializer querySerializer =
            new QuerySerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void getQueryNameReturnsTheNameOfTheQueryAsSpecifiedInTheQueryRequest() {
        QueryMessage testQueryMessage =
                new GenericQueryMessage(new MessageType("query"), TEST_QUERY, RESPONSE_TYPE);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        assertEquals(testQueryRequest.getQuery(), testSubject.type().name());
    }

    @Test
    void responseTypeReturnsTheTypeAsSpecifiedInTheQueryRequest() {
        ResponseType<String> expectedResponseType = RESPONSE_TYPE;
        QueryMessage testQueryMessage = new GenericQueryMessage(
                new MessageType("query"), TEST_QUERY, expectedResponseType
        );
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        assertEquals(
                expectedResponseType.getExpectedResponseType(), testSubject.responseType().getExpectedResponseType()
        );
    }

    @Test
    void identifierAsSpecifiedInTheQueryRequest() {
        QueryMessage testQueryMessage =
                new GenericQueryMessage(new MessageType("query"), TEST_QUERY, RESPONSE_TYPE);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        assertEquals(testQueryRequest.getMessageIdentifier(), testSubject.identifier());
    }

    @Test
    void metadataReturnsTheSameMapAsWasInsertedInTheQueryRequest() {
        Metadata expectedMetadata = Metadata.with("some-key", "some-value");
        QueryMessage testQueryMessage = new GenericQueryMessage(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE
        ).withMetadata(expectedMetadata);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        assertEquals(expectedMetadata, testSubject.metadata());
    }

    @Test
    void payloadReturnsAnIdenticalObjectAsInsertedThroughTheQueryRequest() {
        TestQuery expectedQuery = TEST_QUERY;
        QueryMessage testQueryMessage =
                new GenericQueryMessage(new MessageType("query"), expectedQuery, RESPONSE_TYPE);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        assertEquals(expectedQuery, testSubject.payload());
    }

    @Test
    void payloadTypeReturnsTheTypeOfTheInsertedQueryRequest() {
        QueryMessage testQueryMessage =
                new GenericQueryMessage(new MessageType("query"), TEST_QUERY, RESPONSE_TYPE);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        assertEquals(TestQuery.class, testSubject.payloadType());
    }

    @Test
    void withMetadataCompletelyReplacesTheInitialMetadataMap() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        QueryMessage testQueryMessage = new GenericQueryMessage(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE
        ).withMetadata(testMetadata);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        Metadata replacementMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetadata(replacementMetadata);
        Metadata resultMetadata = testSubject.metadata();
        assertFalse(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertEquals(replacementMetadata, resultMetadata);
    }

    @Test
    void andMetadataAppendsToTheExistingMetadata() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        QueryMessage testQueryMessage = new GenericQueryMessage(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE
        ).withMetadata(testMetadata);
        QueryRequest testQueryRequest =
                querySerializer.serializeRequest(testQueryMessage, NUMBER_OF_RESULTS, TIMEOUT, PRIORITY);
        GrpcBackedQueryMessage<TestQuery, String> testSubject =
                new GrpcBackedQueryMessage<>(testQueryRequest, serializer, serializer);

        Metadata additionalMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetadata(additionalMetadata);
        Metadata resultMetadata = testSubject.metadata();

        assertTrue(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertTrue(resultMetadata.containsKey(additionalMetadata.keySet().iterator().next()));
    }

    private static class TestQuery {

        private final String queryModelId;
        private final int someFilterValue;

        private TestQuery(@JsonProperty("queryModelId") String queryModelId,
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
            TestQuery that = (TestQuery) o;
            return someFilterValue == that.someFilterValue &&
                    Objects.equals(queryModelId, that.queryModelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queryModelId, someFilterValue);
        }
    }
}