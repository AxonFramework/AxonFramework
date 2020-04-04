/*
 * Copyright (c) 2010-2019. Axon Framework
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

import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.defaults.DefaultSerializerSupplier;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.Test;

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

    private final Serializer serializer = DefaultSerializerSupplier.DEFAULT_SERIALIZER.get();
    private final SubscriptionMessageSerializer subscriptionMessageSerializer =
            new SubscriptionMessageSerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void testGetUpdateResponseTypeReturnsTheTypeAsSpecifiedInTheSubscriptionQuery() {
        ResponseType<String> expectedUpdateResponseType = RESPONSE_TYPE;
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, expectedUpdateResponseType);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(
                expectedUpdateResponseType.getExpectedResponseType(),
                testSubject.getResponseType().getExpectedResponseType()
        );
    }

    @Test
    void testGetQueryNameReturnsTheNameOfTheQueryAsSpecifiedInTheSubscriptionQuery() {
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(testSubscriptionQuery.getQueryRequest().getQuery(), testSubject.getQueryName());
    }

    @Test
    void testGetResponseTypeReturnsTheTypeAsSpecifiedInTheSubscriptionQuery() {
        ResponseType<String> expectedResponseType = RESPONSE_TYPE;
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, expectedResponseType, RESPONSE_TYPE);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(
                expectedResponseType.getExpectedResponseType(), testSubject.getResponseType().getExpectedResponseType()
        );
    }

    @Test
    void testGetIdentifierReturnsTheSameIdentifierAsSpecifiedInTheSubscriptionQuery() {
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(testSubscriptionQuery.getSubscriptionIdentifier(), testSubject.getIdentifier());
    }

    @Test
    void testGetMetaDataReturnsTheSameMapAsWasInsertedInTheSubscriptionQuery() {
        MetaData expectedMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE)
                        .withMetaData(expectedMetaData);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(expectedMetaData, testSubject.getMetaData());
    }

    @Test
    void testGetPayloadReturnsAnIdenticalObjectAsInsertedThroughTheSubscriptionQuery() {
        TestQuery expectedQuery = TEST_QUERY;
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(expectedQuery, RESPONSE_TYPE, RESPONSE_TYPE);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(expectedQuery, testSubject.getPayload());
    }

    @Test
    void testGetPayloadTypeReturnsTheTypeOfTheInsertedSubscriptionQuery() {
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        assertEquals(TestQuery.class, testSubject.getPayloadType());
    }

    @Test
    void testWithMetaDataCompletelyReplacesTheInitialMetaDataMap() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE)
                        .withMetaData(testMetaData);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        MetaData replacementMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetaData(replacementMetaData);
        MetaData resultMetaData = testSubject.getMetaData();
        assertFalse(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertEquals(replacementMetaData, resultMetaData);
    }

    @Test
    void testAndMetaDataAppendsToTheExistingMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryMessage testSubscriptionQueryMessage =
                new GenericSubscriptionQueryMessage<>(TEST_QUERY, RESPONSE_TYPE, RESPONSE_TYPE)
                        .withMetaData(testMetaData);
        SubscriptionQuery testSubscriptionQuery = subscriptionMessageSerializer.serialize(testSubscriptionQueryMessage);
        GrpcBackedSubscriptionQueryMessage<TestQuery, String, String> testSubject =
                new GrpcBackedSubscriptionQueryMessage<>(testSubscriptionQuery, serializer, serializer);

        MetaData additionalMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetaData(additionalMetaData);
        MetaData resultMetaData = testSubject.getMetaData();

        assertTrue(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertTrue(resultMetaData.containsKey(additionalMetaData.keySet().iterator().next()));
    }

    private static class TestQuery {

        private final String queryModelId;
        private final int someFilterValue;

        private TestQuery(String queryModelId, int someFilterValue) {
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