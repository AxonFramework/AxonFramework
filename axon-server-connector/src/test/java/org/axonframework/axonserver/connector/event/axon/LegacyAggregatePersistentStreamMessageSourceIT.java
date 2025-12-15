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

package org.axonframework.axonserver.connector.event.axon;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.event.AppendEventsTransaction;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link PersistentStreamMessageSource} using a legacy aggregate-based (non-DCB) context.
 * <p>
 * This test class extends {@link AbstractPersistentStreamMessageSourceIT} and provides legacy aggregate-specific
 * event publishing using {@link io.axoniq.axonserver.connector.event.EventChannel EventChannel}.
 * <p>
 * In addition to the shared tests from the base class, this class includes aggregate tag-based filtering tests
 * which are only supported with legacy aggregate events (not DCB). Legacy events include:
 * <ul>
 *     <li>{@code aggregateIdentifier} - the aggregate identifier</li>
 *     <li>{@code aggregateType} - the aggregate type</li>
 *     <li>{@code aggregateSequenceNumber} - the sequence number within the aggregate</li>
 * </ul>
 * These fields are exposed through the {@link org.axonframework.messaging.core.LegacyResources LegacyResources}
 * in the event's {@link org.axonframework.messaging.core.Context Context}, enabling client-side filtering.
 *
 * @author Mateusz Nowak
 * @see AbstractPersistentStreamMessageSourceIT
 * @see DcbPersistentStreamMessageSourceIT
 */
@Testcontainers
class LegacyAggregatePersistentStreamMessageSourceIT extends AbstractPersistentStreamMessageSourceIT {

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer axonServerContainer =
            new AxonServerContainer()
                    .withAxonServerHostname("localhost")
                    .withDevMode(true);
    // Note: No .withDcbContext(true) - this is a legacy aggregate-based context

    @BeforeAll
    static void beforeAll() {
        initializeConnection(axonServerContainer, "LegacyAggregatePersistentStreamMessageSourceIT");
    }

    @AfterAll
    static void afterAll() {
        cleanupConnection(axonServerContainer);
    }

    @Override
    protected AxonServerContainer getContainer() {
        return axonServerContainer;
    }

    @Override
    protected void purgeEvents() throws Exception {
        AxonServerContainerUtils.purgeEventsFromAxonServer(
                axonServerContainer.getHost(),
                axonServerContainer.getHttpPort(),
                CONTEXT,
                AxonServerContainerUtils.NO_DCB_CONTEXT
        );
    }

    @Override
    protected void publishEvent(String aggregateId, String aggregateType, String eventType, long sequenceNumber)
            throws Exception {
        AppendEventsTransaction tx = connection.eventChannel().startAppendEventsTransaction();

        Event event = Event.newBuilder()
                           .setMessageIdentifier(UUID.randomUUID().toString())
                           .setTimestamp(System.currentTimeMillis())
                           .setAggregateIdentifier(aggregateId)
                           .setAggregateType(aggregateType)
                           .setAggregateSequenceNumber(sequenceNumber)
                           .setPayload(SerializedObject.newBuilder()
                                                       .setType(eventType)
                                                       .setRevision("1.0")
                                                       .setData(ByteString.copyFromUtf8("{}"))
                                                       .build())
                           .build();

        tx.appendEvent(event);
        tx.commit().get();
    }

    // ========== Legacy Aggregate-Specific Test Cases ==========

    @Nested
    class EventCriteriaFilteringByAggregateTags {

        @Test
        void filtersEventsByAggregateTagCriteria() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "aggregate-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // Only receive events from OrderAggregate with id "order-123"
            // Tag key = aggregateType, Tag value = aggregateId
            EventCriteria criteria = EventCriteria.havingTags("OrderAggregate", "order-123");

            // when
            registration = testSubject.subscribe(criteria, collectingConsumer(receivedEvents));

            publishEvent("order-123", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-456", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-123", "OrderAggregate", "OrderShipped", 1);
            publishEvent("customer-1", "CustomerAggregate", "CustomerCreated", 0);

            // then - should only receive events from order-123
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents.stream().map(e -> e.type().name()).toList())
                               .containsExactlyInAnyOrder("OrderCreated", "OrderShipped");
                   });
        }

        @Test
        void filtersEventsByDifferentAggregateType() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "aggregate-type-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // Only receive events from CustomerAggregate with id "customer-1"
            EventCriteria criteria = EventCriteria.havingTags("CustomerAggregate", "customer-1");

            // when
            registration = testSubject.subscribe(criteria, collectingConsumer(receivedEvents));

            publishEvent("order-123", "OrderAggregate", "OrderCreated", 0);
            publishEvent("customer-1", "CustomerAggregate", "CustomerCreated", 0);
            publishEvent("customer-2", "CustomerAggregate", "CustomerCreated", 0);
            publishEvent("customer-1", "CustomerAggregate", "CustomerUpdated", 1);

            // then - should only receive events from customer-1
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents.stream().map(e -> e.type().name()).toList())
                               .containsExactlyInAnyOrder("CustomerCreated", "CustomerUpdated");
                   });
        }
    }

    @Nested
    class EventCriteriaCombinedFiltering {

        @Test
        void filtersEventsByBothTypeAndAggregateTag() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "combined-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // Only receive OrderCreated events from OrderAggregate with id "order-123"
            EventCriteria criteria = EventCriteria
                    .havingTags("OrderAggregate", "order-123")
                    .andBeingOneOfTypes(new QualifiedName("OrderCreated"));

            // when
            registration = testSubject.subscribe(criteria, collectingConsumer(receivedEvents));

            publishEvent("order-123", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-123", "OrderAggregate", "OrderShipped", 1);
            publishEvent("order-456", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-123", "OrderAggregate", "OrderCreated", 2);

            // then - should only receive OrderCreated events from order-123
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents).allMatch(e -> e.type().name().equals("OrderCreated"));
                   });
        }

        @Test
        void filtersEventsByMultipleTypesAndAggregateTag() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "combined-multi-type-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // Only receive OrderCreated or OrderShipped events from order-123
            EventCriteria criteria = EventCriteria
                    .havingTags("OrderAggregate", "order-123")
                    .andBeingOneOfTypes(
                            new QualifiedName("OrderCreated"),
                            new QualifiedName("OrderShipped")
                    );

            // when
            registration = testSubject.subscribe(criteria, collectingConsumer(receivedEvents));

            publishEvent("order-123", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-123", "OrderAggregate", "OrderShipped", 1);
            publishEvent("order-123", "OrderAggregate", "OrderCancelled", 2);
            publishEvent("order-456", "OrderAggregate", "OrderCreated", 0);

            // then - should only receive OrderCreated and OrderShipped events from order-123
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents.stream().map(e -> e.type().name()).toList())
                               .containsExactlyInAnyOrder("OrderCreated", "OrderShipped");
                   });
        }
    }
}
