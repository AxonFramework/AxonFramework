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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.impl.ServerAddress;
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

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Abstract base class for {@link PersistentStreamMessageSource} integration tests.
 * <p>
 * This class provides common test infrastructure and shared test cases for both DCB and
 * legacy aggregate-based contexts. Subclasses provide context-specific setup and event publishing.
 * <p>
 * Shared tests include:
 * <ul>
 *     <li>Basic subscription - receives all published events</li>
 *     <li>EventCriteria filtering by event type</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @see DcbPersistentStreamMessageSourceIT
 * @see LegacyAggregatePersistentStreamMessageSourceIT
 */
abstract class AbstractPersistentStreamMessageSourceIT {

    protected static final String CONTEXT = "default";

    protected static AxonServerConnection connection;
    protected static Configuration configuration;

    protected ScheduledExecutorService scheduler;
    protected PersistentStreamMessageSource testSubject;
    protected Registration registration;

    /**
     * Returns the Axon Server container for this test class.
     */
    protected abstract AxonServerContainer getContainer();

    /**
     * Purges events from Axon Server before each test.
     * Implementations should call {@link AxonServerContainerUtils#purgeEventsFromAxonServer}
     * with the appropriate context type.
     */
    protected abstract void purgeEvents() throws Exception;

    /**
     * Publishes an event to the event store.
     *
     * @param aggregateId   The aggregate identifier.
     * @param aggregateType The aggregate type.
     * @param eventType     The event type name.
     * @param sequenceNumber The sequence number within the aggregate.
     */
    protected abstract void publishEvent(String aggregateId, String aggregateType, String eventType, long sequenceNumber) throws Exception;

    /**
     * Sets up the connection and configuration. Called once per test class.
     */
    protected static void initializeConnection(AxonServerContainer container, String clientName) {
        container.start();
        ServerAddress address = new ServerAddress(container.getHost(), container.getGrpcPort());
        connection = AxonServerConnectionFactory.forClient(clientName)
                                                .routingServers(address)
                                                .build()
                                                .connect(CONTEXT);

        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(container.getHost() + ":" + container.getGrpcPort());

        AxonServerConnectionManager connectionManager = AxonServerConnectionManager.builder()
                                                                                   .routingServers(axonServerConfiguration.getServers())
                                                                                   .axonServerConfiguration(axonServerConfiguration)
                                                                                   .build();

        configuration = MessagingConfigurer.create()
                                           .componentRegistry(cr -> cr
                                                   .registerComponent(AxonServerConfiguration.class, c -> axonServerConfiguration)
                                                   .registerComponent(AxonServerConnectionManager.class, c -> connectionManager)
                                                   .registerComponent(Converter.class, c -> new JacksonConverter())
                                           )
                                           .build();
    }

    /**
     * Cleans up the connection. Called once per test class.
     */
    protected static void cleanupConnection(AxonServerContainer container) {
        if (connection != null) {
            connection.disconnect();
        }
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        purgeEvents();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        if (registration != null) {
            registration.cancel();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Creates a {@link PersistentStreamMessageSource} with the given stream ID.
     * Uses the default {@link org.axonframework.axonserver.connector.event.AggregateEventConverter} for message conversion.
     */
    protected PersistentStreamMessageSource createMessageSource(String streamId) {
        PersistentStreamProperties properties = new PersistentStreamProperties(
                streamId,
                1,  // segments
                "SequentialPerAggregatePolicy",  // sequencing policy
                Collections.emptyList(),
                "0",  // initial position - start from beginning
                null
        );

        return new PersistentStreamMessageSource(
                streamId,
                configuration,
                properties,
                scheduler,
                100  // batch size
        );
    }

    /**
     * Creates a consumer that collects events into the provided list.
     */
    protected BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> collectingConsumer(
            List<EventMessage> receivedEvents) {
        return (events, ctx) -> {
            receivedEvents.addAll(events);
            return CompletableFuture.completedFuture(null);
        };
    }

    // ========== Shared Test Cases ==========

    @Nested
    class BasicSubscription {

        @Test
        void receivesAllPublishedEvents() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "basic-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // when
            registration = testSubject.subscribe(collectingConsumer(receivedEvents));

            publishEvent("order-1", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-1", "OrderAggregate", "OrderShipped", 1);
            publishEvent("customer-1", "CustomerAggregate", "CustomerCreated", 0);

            // then
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(3);
                       assertThat(receivedEvents.stream().map(e -> e.type().name()).toList())
                               .containsExactlyInAnyOrder("OrderCreated", "OrderShipped", "CustomerCreated");
                   });
        }
    }

    @Nested
    class EmptyEventStream {

        @Test
        void subscriptionRemainsActiveWhenNoEventsPublished() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "empty-stream-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // when - subscribe but don't publish any events
            registration = testSubject.subscribe(collectingConsumer(receivedEvents));

            // then - verify no events received during the wait period, subscription still active
            await().during(Duration.ofMillis(500))
                   .atMost(Duration.ofSeconds(2))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).isEmpty();
                       assertThat(registration).isNotNull();
                   });
        }

        @Test
        void subscriptionCanBeCancelledWhenNoEventsReceived() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "cancel-empty-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // when - subscribe and verify empty during wait period
            registration = testSubject.subscribe(collectingConsumer(receivedEvents));
            await().during(Duration.ofMillis(300))
                   .atMost(Duration.ofSeconds(2))
                   .untilAsserted(() -> assertThat(receivedEvents).isEmpty());

            // then - cancellation should succeed
            boolean cancelled = registration.cancel();
            assertThat(cancelled).isTrue();
            assertThat(receivedEvents).isEmpty();
        }

        @Test
        void receivesEventsAfterPeriodOfNoEvents() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "delayed-events-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // when - subscribe and verify no events during initial period
            registration = testSubject.subscribe(collectingConsumer(receivedEvents));
            await().during(Duration.ofMillis(500))
                   .atMost(Duration.ofSeconds(2))
                   .untilAsserted(() -> assertThat(receivedEvents).isEmpty());

            // publish events after delay
            publishEvent("order-1", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-1", "OrderAggregate", "OrderShipped", 1);

            // then - should receive the delayed events
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents.stream().map(e -> e.type().name()).toList())
                               .containsExactlyInAnyOrder("OrderCreated", "OrderShipped");
                   });
        }
    }

    @Nested
    class EventCriteriaFilteringByType {

        @Test
        void filtersEventsByTypeCriteria() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "type-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            EventCriteria criteria = EventCriteria.havingAnyTag()
                                                  .andBeingOneOfTypes(new QualifiedName("OrderCreated"));

            // when
            registration = testSubject.subscribe(criteria, collectingConsumer(receivedEvents));

            publishEvent("order-1", "OrderAggregate", "OrderCreated", 0);
            publishEvent("customer-1", "CustomerAggregate", "CustomerCreated", 0);
            publishEvent("order-2", "OrderAggregate", "OrderCreated", 0);
            publishEvent("order-1", "OrderAggregate", "OrderShipped", 1);

            // then - should only receive OrderCreated events
            await().atMost(Duration.ofSeconds(10))
                   .pollDelay(Duration.ofMillis(100))
                   .untilAsserted(() -> {
                       assertThat(receivedEvents).hasSize(2);
                       assertThat(receivedEvents.stream().map(e -> e.type().name()).toList())
                               .containsOnly("OrderCreated");
                   });
        }

        @Test
        void filtersEventsByMultipleTypes() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            String streamId = "multi-type-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            EventCriteria criteria = EventCriteria.havingAnyTag()
                                                  .andBeingOneOfTypes(
                                                          new QualifiedName("OrderCreated"),
                                                          new QualifiedName("OrderShipped")
                                                  );

            // when
            registration = testSubject.subscribe(criteria, collectingConsumer(receivedEvents));

            publishEvent("order-1", "OrderAggregate", "OrderCreated", 0);
            publishEvent("customer-1", "CustomerAggregate", "CustomerCreated", 0);
            publishEvent("order-1", "OrderAggregate", "OrderShipped", 1);
            publishEvent("order-1", "OrderAggregate", "OrderCancelled", 2);

            // then - should receive OrderCreated and OrderShipped events
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
