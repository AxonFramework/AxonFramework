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

import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test all the functions provided on the {@link GrpcBackedQueryUpdateMessage}. The {@link QueryUpdate} to be passed to
 * a GrpcBackedQueryUpdateMessage is created by using the {@link SubscriptionMessageSerializer}.
 *
 * @author Steven van Beelen
 */
class GrpcBackedQueryUpdateMessageTest {

    private static final TestQueryUpdate TEST_QUERY_UPDATE = new TestQueryUpdate("aggregateId", 42);
    private static final String SUBSCRIPTION_ID = "subscription-id";

    private final Serializer serializer = XStreamSerializer.defaultSerializer();
    private final SubscriptionMessageSerializer subscriptionMessageSerializer =
            new SubscriptionMessageSerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void testGetIdentifierReturnsTheSameIdentifierAsSpecifiedInTheQueryUpdate() {
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(TEST_QUERY_UPDATE);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(testQueryUpdate.getMessageIdentifier(), testSubject.getIdentifier());
    }

    @Test
    void testGetMetaDataReturnsTheSameMapAsWasInsertedInTheQueryUpdate() {
        MetaData expectedMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(TEST_QUERY_UPDATE).withMetaData(expectedMetaData);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(expectedMetaData, testSubject.getMetaData());
    }

    @Test
    void testGetPayloadReturnsAnIdenticalObjectAsInsertedThroughTheQueryUpdate() {
        TestQueryUpdate expectedQueryUpdate = TEST_QUERY_UPDATE;
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(expectedQueryUpdate);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(expectedQueryUpdate, testSubject.getPayload());
    }

    @Test
    void testGetPayloadTypeReturnsTheTypeOfTheInsertedQueryUpdate() {
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(TEST_QUERY_UPDATE);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(TestQueryUpdate.class, testSubject.getPayloadType());
    }

    @Test
    void testWithMetaDataCompletelyReplacesTheInitialMetaDataMap() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(TEST_QUERY_UPDATE).withMetaData(testMetaData);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        MetaData replacementMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetaData(replacementMetaData);
        MetaData resultMetaData = testSubject.getMetaData();
        assertFalse(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertEquals(replacementMetaData, resultMetaData);
    }

    @Test
    void testAndMetaDataAppendsToTheExistingMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(TEST_QUERY_UPDATE).withMetaData(testMetaData);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        MetaData additionalMetaData = MetaData.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetaData(additionalMetaData);
        MetaData resultMetaData = testSubject.getMetaData();

        assertTrue(resultMetaData.containsKey(testMetaData.keySet().iterator().next()));
        assertTrue(resultMetaData.containsKey(additionalMetaData.keySet().iterator().next()));
    }

    private static class TestQueryUpdate {

        private final String queryModelId;
        private final int someFilterValue;

        private TestQueryUpdate(String queryModelId, int someFilterValue) {
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
            TestQueryUpdate that = (TestQueryUpdate) o;
            return someFilterValue == that.someFilterValue &&
                    Objects.equals(queryModelId, that.queryModelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queryModelId, someFilterValue);
        }
    }
}