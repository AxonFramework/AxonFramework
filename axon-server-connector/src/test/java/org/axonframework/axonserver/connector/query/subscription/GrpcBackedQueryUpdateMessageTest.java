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
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
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

    private final Serializer serializer = JacksonSerializer.defaultSerializer();
    private final SubscriptionMessageSerializer subscriptionMessageSerializer =
            new SubscriptionMessageSerializer(serializer, serializer, new AxonServerConfiguration());

    @Test
    void identifierAsSpecifiedInTheQueryUpdate() {
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        assertEquals(testQueryUpdate.getMessageIdentifier(), testSubject.identifier());
    }

    @Test
    void metadataReturnsTheSameMapAsWasInsertedInTheQueryUpdate() {
        Metadata expectedMetadata = Metadata.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE).withMetadata(expectedMetadata);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        assertEquals(expectedMetadata, testSubject.metadata());
    }

    @Test
    void payloadReturnsAnIdenticalObjectAsInsertedThroughTheQueryUpdate() {
        TestQueryUpdate expectedQueryUpdate = TEST_QUERY_UPDATE;
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(expectedQueryUpdate);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        assertEquals(expectedQueryUpdate, testSubject.payload());
    }

    @Test
    void payloadTypeReturnsTheTypeOfTheInsertedQueryUpdate() {
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        assertEquals(TestQueryUpdate.class, testSubject.payloadType());
    }

    @Test
    void getPayloadThrowsIllegalPayloadExceptionWhenUpdateIsExceptional() {
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TestQueryUpdate.class, new RuntimeException());
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        assertThrows(IllegalPayloadAccessException.class, testSubject::payload);
    }

    @Test
    void withMetadataCompletelyReplacesTheInitialMetadataMap() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE).withMetadata(testMetadata);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        Metadata replacementMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.withMetadata(replacementMetadata);
        Metadata resultMetadata = testSubject.metadata();
        assertFalse(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertEquals(replacementMetadata, resultMetadata);
    }

    @Test
    void andMetadataAppendsToTheExistingMetadata() {
        Metadata testMetadata = Metadata.with("some-key", "some-value");
        SubscriptionQueryUpdateMessage testSubscriptionQueryUpdateMessage =
                asUpdateMessage(TEST_QUERY_UPDATE).withMetadata(testMetadata);
        QueryUpdate testQueryUpdate =
                subscriptionMessageSerializer.serialize(testSubscriptionQueryUpdateMessage);
        GrpcBackedQueryUpdateMessage testSubject =
                new GrpcBackedQueryUpdateMessage(testQueryUpdate, serializer);

        Metadata additionalMetadata = Metadata.with("some-other-key", "some-other-value");

        testSubject = testSubject.andMetadata(additionalMetadata);
        Metadata resultMetadata = testSubject.metadata();

        assertTrue(resultMetadata.containsKey(testMetadata.keySet().iterator().next()));
        assertTrue(resultMetadata.containsKey(additionalMetadata.keySet().iterator().next()));
    }

    private static SubscriptionQueryUpdateMessage asUpdateMessage(Class<?> declaredType, Throwable exception) {
        return new GenericSubscriptionQueryUpdateMessage(new MessageType(exception.getClass()),
                                                         exception,
                                                         declaredType,
                                                         Metadata.emptyInstance());
    }

    private static SubscriptionQueryUpdateMessage asUpdateMessage(Object payload) {
        return new GenericSubscriptionQueryUpdateMessage(new MessageType(payload.getClass()), payload);
    }

    private static class TestQueryUpdate {

        private final String queryModelId;
        private final int someFilterValue;

        private TestQueryUpdate(@JsonProperty("queryModelId") String queryModelId,
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