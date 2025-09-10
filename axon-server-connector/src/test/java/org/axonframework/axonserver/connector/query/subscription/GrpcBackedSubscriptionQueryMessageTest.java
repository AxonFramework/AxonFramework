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

package org.axonframework.axonserver.connector.query.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all the functions provided on the {@link GrpcBackedSubscriptionQueryMessage}. The {@link SubscriptionQuery} to
 * be passed to a GrpcBackedSubscriptionQueryMessage is created by using the {@link SubscriptionMessageSerializer}.
 *
 * @author Steven van Beelen
 */
class GrpcBackedSubscriptionQueryMessageTest {

    private static final TestQuery TEST_QUERY = new TestQuery("aggregateId", 42);
    private static final ResponseType<String> RESPONSE_TYPE = ResponseTypes.instanceOf(String.class);

    private final Serializer serializer = JacksonSerializer.defaultSerializer();
    private final SubscriptionMessageSerializer subscriptionMessageSerializer =
            new SubscriptionMessageSerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void updatesResponseTypeReturnsTheTypeAsSpecifiedInTheSubscriptionQuery() {
        ResponseType<String> expectedUpdateResponseType = RESPONSE_TYPE;
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, expectedUpdateResponseType
        );
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(
                expectedUpdateResponseType.getExpectedResponseType(),
                testSubject.responseType().getExpectedResponseType()
        );
    }

    @Test
    void getQueryNameReturnsTheNameOfTheQueryAsSpecifiedInTheSubscriptionQuery() {
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE
        );
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(testSubscriptionQuery.getQueryRequest().getQuery(), testSubject.type().name());
    }

    @Test
    void responseTypeReturnsTheTypeAsSpecifiedInTheSubscriptionQuery() {
        ResponseType<String> expectedResponseType = RESPONSE_TYPE;
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, expectedResponseType, RESPONSE_TYPE
        );
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(
                expectedResponseType.getExpectedResponseType(), testSubject.responseType().getExpectedResponseType()
        );
    }

    @Test
    void identifierAsSpecifiedInTheSubscriptionQuery() {
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE
        );
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(testSubscriptionQuery.getSubscriptionIdentifier(), testSubject.identifier());
    }

    @Test
    void metadataReturnsTheSameMapAsWasInsertedInTheSubscriptionQuery() {
        Metadata expectedMetadata = Metadata.with("some-key", "some-value");
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE
        ).withMetadata(expectedMetadata);
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(expectedMetadata, testSubject.metadata());
    }

    @Test
    void payloadReturnsAnIdenticalObjectAsInsertedThroughTheSubscriptionQuery() {
        TestQuery expectedQuery = TEST_QUERY;
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), expectedQuery, RESPONSE_TYPE, RESPONSE_TYPE
        );
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(expectedQuery, testSubject.payload());
    }

    @Test
    void payloadTypeReturnsTheTypeOfTheInsertedSubscriptionQuery() {
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE
        );
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(TestQuery.class, testSubject.payloadType());
    }

    @Test
    void withMetadataCompletelyReplacesTheInitialMetadataMap() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE
        ).withMetadata(testMetadata);
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        Metadata replacementMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetadata(replacementMetadata);
        Metadata resultMetadata = testSubject.metadata();
        assertFalse(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertEquals(replacementMetadata, resultMetadata);
    }

    @Test
    void andMetadataAppendsToTheExistingMetadata() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        SubscriptionQueryMessage<TestQuery, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE
        ).withMetadata(testMetadata);
        SubscriptionQuery testSubscriptionQuery =
                SubscriptionQuery.newBuilder()
                                 .setQueryRequest(subscriptionMessageSerializer.serializeQuery(testQuery))
                                 .setUpdateResponseType(subscriptionMessageSerializer.serializeUpdateType(testQuery))
                                 .build();

        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

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