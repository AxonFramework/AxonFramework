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
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.event.AppendEventsTransaction;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link PersistentStreamMessageSource} with EventCriteria filtering.
 * <p>
 * These tests validate that events are properly filtered when using EventCriteria with:
 * <ul>
 *     <li>Type-based filtering</li>
 *     <li>Aggregate-based tag filtering</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Testcontainers
@Tags({
        @Tag("slow"),
})
class PersistentStreamMessageSourceIT {

    private static final String CONTEXT = "default";

    @SuppressWarnings("resource")
    private static final AxonServerContainer axonServerContainer = new AxonServerContainer()
            .withAxonServerHostname("localhost")
            .withDevMode(true);

    private static AxonServerConnection connection;
    private static JacksonConverter jacksonConverter;

    private ScheduledExecutorService scheduler;
    private Configuration configuration;

    @BeforeAll
    static void beforeAll() {
        axonServerContainer.start();
        connection = AxonServerConnectionFactory.forClient("PersistentStreamMessageSourceIT")
                                                .routingServers(new ServerAddress(axonServerContainer.getHost(),
                                                                                  axonServerContainer.getGrpcPort()))
                                                .build()
                                                .connect(CONTEXT);
        jacksonConverter = new JacksonConverter();
    }

    @AfterAll
    static void afterAll() {
        connection.disconnect();
        axonServerContainer.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Purge events before each test
        AxonServerContainerUtils.purgeEventsFromAxonServer(
                axonServerContainer.getHost(),
                axonServerContainer.getHttpPort(),
                CONTEXT,
                AxonServerContainerUtils.NO_DCB_CONTEXT
        );

        scheduler = Executors.newScheduledThreadPool(2);

        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(axonServerContainer.getAxonServerAddress());

        AxonServerConnectionManager connectionManager = AxonServerConnectionManager.builder()
                .routingServers(axonServerConfiguration.getServers())
                .axonServerConfiguration(axonServerConfiguration)
                .build();

        configuration = MessagingConfigurer.create()
                .componentRegistry(cr -> cr
                        .registerComponent(AxonServerConfiguration.class, c -> axonServerConfiguration)
                        .registerComponent(AxonServerConnectionManager.class, c -> connectionManager)
                        .registerComponent(Converter.class, c -> jacksonConverter)
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Nested
    class TypeBasedFiltering {

        @Test
        void filtersEventsByType() {
            // given
            String streamName = "type-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            EventCriteria criteria = EventCriteria.havingAnyTag()
                    .andBeingOneOfTypes(new QualifiedName("OrderCreated"));

            // when
            // Subscribe first, then publish events
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));

            // Give the subscription time to establish
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            // Publish events of different types
            publishEvent("OrderCreated", new OrderCreated("order-1"));
            publishEvent("OrderShipped", new OrderShipped("order-1"));
            publishEvent("OrderCreated", new OrderCreated("order-2"));
            publishEvent("CustomerRegistered", new CustomerRegistered("customer-1"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents)
                               .allMatch(e -> e.type().qualifiedName().equals(new QualifiedName("OrderCreated")));
                   });

            registration.cancel();
        }

        @Test
        void filtersEventsByMultipleTypes() {
            // given
            String streamName = "multi-type-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            EventCriteria criteria = EventCriteria.havingAnyTag()
                    .andBeingOneOfTypes(
                            new QualifiedName("OrderCreated"),
                            new QualifiedName("OrderShipped")
                    );

            // when
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            publishEvent("OrderCreated", new OrderCreated("order-1"));
            publishEvent("OrderShipped", new OrderShipped("order-1"));
            publishEvent("CustomerRegistered", new CustomerRegistered("customer-1"));
            publishEvent("OrderCreated", new OrderCreated("order-2"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(3);
                       assertThat(receivedEvents)
                               .allMatch(e -> {
                                   QualifiedName type = e.type().qualifiedName();
                                   return type.equals(new QualifiedName("OrderCreated"))
                                           || type.equals(new QualifiedName("OrderShipped"));
                               });
                   });

            registration.cancel();
        }
    }

    @Nested
    class AggregateBasedTagFiltering {

        @Test
        void filtersEventsByAggregateTag() {
            // given
            String streamName = "aggregate-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            // Filter by aggregate type "Order" and identifier "order-1"
            EventCriteria criteria = EventCriteria.havingTags("Order", "order-1");

            // when
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            // Publish aggregate-based events
            publishAggregateEvent("Order", "order-1", 0, "OrderCreated", new OrderCreated("order-1"));
            publishAggregateEvent("Order", "order-2", 0, "OrderCreated", new OrderCreated("order-2"));
            publishAggregateEvent("Order", "order-1", 1, "OrderShipped", new OrderShipped("order-1"));
            publishAggregateEvent("Customer", "customer-1", 0, "CustomerRegistered", new CustomerRegistered("customer-1"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       // All events should be for order-1
                       assertThat(receivedEvents)
                               .extracting(e -> e.payloadAs(Object.class, jacksonConverter))
                               .allMatch(payload -> {
                                   if (payload instanceof OrderCreated oc) {
                                       return "order-1".equals(oc.orderId());
                                   } else if (payload instanceof OrderShipped os) {
                                       return "order-1".equals(os.orderId());
                                   }
                                   return false;
                               });
                   });

            registration.cancel();
        }

        @Test
        void filtersEventsWithOrCriteriaForMultipleAggregates() {
            // given
            String streamName = "aggregate-type-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            // Filter by any Order aggregate (using type as key, any value matches)
            // Note: havingTags requires both key and value, so we test filtering by specific aggregate
            EventCriteria criteria = EventCriteria
                    .havingTags("Order", "order-1")
                    .or()
                    .havingTags("Order", "order-2");

            // when
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            publishAggregateEvent("Order", "order-1", 0, "OrderCreated", new OrderCreated("order-1"));
            publishAggregateEvent("Order", "order-2", 0, "OrderCreated", new OrderCreated("order-2"));
            publishAggregateEvent("Customer", "customer-1", 0, "CustomerRegistered", new CustomerRegistered("customer-1"));
            publishAggregateEvent("Order", "order-3", 0, "OrderCreated", new OrderCreated("order-3"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       // Should receive events for order-1 and order-2, not order-3 or customer-1
                       assertThat(receivedEvents).hasSize(2);
                   });

            registration.cancel();
        }
    }

    @Nested
    class CombinedFiltering {

        @Test
        void filtersEventsByTagAndType() {
            // given
            String streamName = "combined-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            // Filter by aggregate tag AND event type
            EventCriteria criteria = EventCriteria
                    .havingTags("Order", "order-1")
                    .andBeingOneOfTypes(new QualifiedName("OrderShipped"));

            // when
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            publishAggregateEvent("Order", "order-1", 0, "OrderCreated", new OrderCreated("order-1"));
            publishAggregateEvent("Order", "order-1", 1, "OrderShipped", new OrderShipped("order-1"));
            publishAggregateEvent("Order", "order-2", 0, "OrderCreated", new OrderCreated("order-2"));
            publishAggregateEvent("Order", "order-2", 1, "OrderShipped", new OrderShipped("order-2"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       // Should only receive OrderShipped for order-1
                       assertThat(receivedEvents).hasSize(1);
                       assertThat(receivedEvents.getFirst().type().qualifiedName())
                               .isEqualTo(new QualifiedName("OrderShipped"));
                   });

            registration.cancel();
        }

        @Test
        void filtersWithOrCriteria() {
            // given
            String streamName = "or-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            // Filter: (Order, order-1) OR (Customer, customer-1)
            EventCriteria criteria = EventCriteria
                    .havingTags("Order", "order-1")
                    .or()
                    .havingTags("Customer", "customer-1");

            // when
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            publishAggregateEvent("Order", "order-1", 0, "OrderCreated", new OrderCreated("order-1"));
            publishAggregateEvent("Order", "order-2", 0, "OrderCreated", new OrderCreated("order-2"));
            publishAggregateEvent("Customer", "customer-1", 0, "CustomerRegistered", new CustomerRegistered("customer-1"));
            publishAggregateEvent("Customer", "customer-2", 0, "CustomerRegistered", new CustomerRegistered("customer-2"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       // Should receive order-1 and customer-1 events
                       assertThat(receivedEvents).hasSize(2);
                   });

            registration.cancel();
        }
    }

    @Nested
    class NoFiltering {

        @Test
        void receivesAllEventsWithHavingAnyTag() {
            // given
            String streamName = "no-filter-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();
            EventCriteria criteria = EventCriteria.havingAnyTag();

            // when
            Registration registration = messageSource.subscribe(criteria, collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            publishEvent("OrderCreated", new OrderCreated("order-1"));
            publishEvent("OrderShipped", new OrderShipped("order-1"));
            publishEvent("CustomerRegistered", new CustomerRegistered("customer-1"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> assertThat(receivedEvents).hasSize(3));

            registration.cancel();
        }

        @Test
        void receivesAllEventsWithDefaultSubscribe() {
            // given
            String streamName = "default-subscribe-stream-" + UUID.randomUUID();
            PersistentStreamProperties properties = new PersistentStreamProperties(
                    streamName, 1, "Seq", Collections.emptyList(), "0", null
            );

            PersistentStreamEventConverter converter = new PersistentStreamEventConverter();
            PersistentStreamMessageSource messageSource = new PersistentStreamMessageSource(
                    streamName, configuration, properties, scheduler, 10, CONTEXT, converter
            );

            List<EventMessage> receivedEvents = new CopyOnWriteArrayList<>();

            // when
            // Using the default subscribe method (no criteria)
            Registration registration = messageSource.subscribe(collectEvents(receivedEvents));
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            publishEvent("OrderCreated", new OrderCreated("order-1"));
            publishEvent("OrderShipped", new OrderShipped("order-1"));
            publishEvent("CustomerRegistered", new CustomerRegistered("customer-1"));

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollInterval(Duration.ofMillis(100))
                   .untilAsserted(() -> assertThat(receivedEvents).hasSize(3));

            registration.cancel();
        }
    }

    // Helper methods

    private void publishEvent(String eventType, Object payload) {
        AppendEventsTransaction tx = connection.eventChannel().startAppendEventsTransaction();
        byte[] payloadBytes = jacksonConverter.convert(payload, byte[].class);
        Event event = Event.newBuilder()
                           .setMessageIdentifier(UUID.randomUUID().toString())
                           .setTimestamp(System.currentTimeMillis())
                           .setPayload(SerializedObject.newBuilder()
                                                       .setType(eventType)
                                                       .setRevision("1.0")
                                                       .setData(ByteString.copyFrom(payloadBytes))
                                                       .build())
                           .build();
        tx.appendEvent(event);
        tx.commit().join();
    }

    private void publishAggregateEvent(String aggregateType, String aggregateId, long sequenceNumber,
                                        String eventType, Object payload) {
        AppendEventsTransaction tx = connection.eventChannel().startAppendEventsTransaction();
        byte[] payloadBytes = jacksonConverter.convert(payload, byte[].class);
        Event event = Event.newBuilder()
                           .setMessageIdentifier(UUID.randomUUID().toString())
                           .setTimestamp(System.currentTimeMillis())
                           .setAggregateType(aggregateType)
                           .setAggregateIdentifier(aggregateId)
                           .setAggregateSequenceNumber(sequenceNumber)
                           .setPayload(SerializedObject.newBuilder()
                                                       .setType(eventType)
                                                       .setRevision("1.0")
                                                       .setData(ByteString.copyFrom(payloadBytes))
                                                       .build())
                           .build();
        tx.appendEvent(event);
        tx.commit().join();
    }

    private BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> collectEvents(
            List<EventMessage> collector
    ) {
        return (events, context) -> {
            collector.addAll(events);
            return CompletableFuture.completedFuture(null);
        };
    }

    // Test event classes

    public record OrderCreated(String orderId) {}

    public record OrderShipped(String orderId) {}

    public record CustomerRegistered(String customerId) {}
}
