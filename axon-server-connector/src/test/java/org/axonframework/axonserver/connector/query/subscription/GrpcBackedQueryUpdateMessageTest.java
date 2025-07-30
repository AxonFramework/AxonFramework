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

import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all the functions provided on the {@link GrpcBackedQueryUpdateMessage}. The {@link QueryUpdate} to be passed to
 * a GrpcBackedQueryUpdateMessage is created by using the {@link SubscriptionMessageSerializer}.
 *
 * @author Steven van Beelen
 */
class GrpcBackedQueryUpdateMessageTest {

    private static final TestQueryUpdate TEST_QUERY_UPDATE = new TestQueryUpdate("aggregateId", 42);

    private final Serializer serializer = TestSerializer.xStreamSerializer();
    private final SubscriptionMessageSerializer subscriptionMessageSerializer =
            new SubscriptionMessageSerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void identifierAsSpecifiedInTheQueryUpdate() {
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(testQueryUpdate.getMessageIdentifier(), testSubject.identifier());
    }

    @Test
    void getMetaDataReturnsTheSameMapAsWasInsertedInTheQueryUpdate() {
        MetaData expectedMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE).withMetaData(expectedMetaData);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(expectedMetaData, testSubject.getMetaData());
    }

    @Test
    void payloadReturnsAnIdenticalObjectAsInsertedThroughTheQueryUpdate() {
        TestQueryUpdate expectedQueryUpdate = TEST_QUERY_UPDATE;
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(expectedQueryUpdate);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(expectedQueryUpdate, testSubject.payload());
    }

    @Test
    void payloadTypeReturnsTheTypeOfTheInsertedQueryUpdate() {
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertEquals(TestQueryUpdate.class, testSubject.payloadType());
    }

    @Test
    void getPayloadThrowsIllegalPayloadExceptionWhenUpdateIsExceptional() {
        SubscriptionQueryUpdateMessage<TestQueryUpdate> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TestQueryUpdate.class, new RuntimeException());
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage<TestQueryUpdate> testSubject =
                new GrpcBackedQueryUpdateMessage<>(testQueryUpdate, serializer);

        assertThrows(IllegalPayloadAccessException.class, testSubject::payload);
    }

    @Test
    void withMetaDataCompletelyReplacesTheInitialMetaDataMap() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE).withMetaData(testMetaData);
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
    void andMetaDataAppendsToTheExistingMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage<Object> testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE).withMetaData(testMetaData);
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

    private static <U> SubscriptionQueryUpdateMessage<U> asUpdateMessage(Class<U> declaredType, Throwable exception) {
        return new GenericSubscriptionQueryUpdateMessage<>(new MessageType(exception.getClass()),
                                                           exception,
                                                           MetaData.emptyInstance(),
                                                           declaredType);
    }

    @SuppressWarnings("unchecked")
    private static <U> SubscriptionQueryUpdateMessage<U> asUpdateMessage(Object payload) {
        return new GenericSubscriptionQueryUpdateMessage<>(new MessageType(payload.getClass()), (U) payload);
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