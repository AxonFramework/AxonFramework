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
import io.axoniq.axonserver.connector.event.DcbEventChannel;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import io.axoniq.axonserver.grpc.event.dcb.ConsistencyCondition;
import io.axoniq.axonserver.grpc.event.dcb.Event;
import io.axoniq.axonserver.grpc.event.dcb.Tag;
import io.axoniq.axonserver.grpc.event.dcb.TaggedEvent;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
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
 * Integration tests for {@link PersistentStreamMessageSource} using Axon Server testcontainers.
 *
 * @author Mateusz Nowak
 */
@Testcontainers
class PersistentStreamMessageSourceIT {

    private static final String CONTEXT = "default";
    private static final String STREAM_NAME = "test-persistent-stream";

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer axonServerContainer =
            new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
                    .withDevMode(true)
                    .withDcbContext(true);

    private static AxonServerConnection connection;
    private static Configuration configuration;

    private ScheduledExecutorService scheduler;
    private PersistentStreamMessageSource testSubject;
    private Registration registration;

    @BeforeAll
    static void beforeAll() {
        axonServerContainer.start();
        ServerAddress address = new ServerAddress(axonServerContainer.getHost(), axonServerContainer.getGrpcPort());
        connection = AxonServerConnectionFactory.forClient("PersistentStreamMessageSourceIT")
                                                .routingServers(address)
                                                .build()
                                                .connect(CONTEXT);

        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(axonServerContainer.getHost() + ":" + axonServerContainer.getGrpcPort());

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

    @BeforeEach
    void setUp() throws IOException {
        AxonServerContainerUtils.purgeEventsFromAxonServer(
                axonServerContainer.getHost(),
                axonServerContainer.getHttpPort(),
                CONTEXT,
                AxonServerContainerUtils.DCB_CONTEXT
        );

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

    @AfterAll
    static void afterAll() {
        connection.disconnect();
        axonServerContainer.stop();
    }

    @Nested
    class BasicSubscription {

        @Test
        void receivesAllPublishedEvents() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> consumer =
                    (events, ctx) -> {
                        receivedEvents.addAll(events);
                        return CompletableFuture.completedFuture(null);
                    };

            String streamId = "basic-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // when
            registration = testSubject.subscribe(consumer);

            // Publish events after subscribing
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
    class EventCriteriaFilteringByType {

        @Test
        void filtersEventsByTypeCriteria() throws Exception {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> consumer =
                    (events, ctx) -> {
                        receivedEvents.addAll(events);
                        return CompletableFuture.completedFuture(null);
                    };

            String streamId = "type-filter-test-" + UUID.randomUUID();
            testSubject = createMessageSource(streamId);

            // Only receive OrderCreated events
            EventCriteria criteria = EventCriteria.beingOneOfTypes(new QualifiedName("OrderCreated"));

            // when
            registration = testSubject.subscribe(criteria, consumer);

            // Publish events of different types
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
    }

    // Helper methods

    private PersistentStreamMessageSource createMessageSource(String streamId) {
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
                100,  // batch size
                new PersistentStreamEventConverter()
        );
    }

    private void publishEvent(String aggregateId, String aggregateType, String eventType, long sequenceNumber) throws Exception {
        DcbEventChannel dcbChannel = connection.dcbEventChannel();
        DcbEventChannel.AppendEventsTransaction tx = dcbChannel.startTransaction(
                ConsistencyCondition.getDefaultInstance()
        );

        Event event = Event.newBuilder()
                           .setIdentifier(UUID.randomUUID().toString())
                           .setTimestamp(System.currentTimeMillis())
                           .setName(eventType)
                           .setVersion("1.0")
                           .setPayload(ByteString.copyFromUtf8("{}"))
                           .build();

        // Create a tag for the aggregate (key=aggregateType, value=aggregateId)
        Tag aggregateTag = Tag.newBuilder()
                              .setKey(ByteString.copyFromUtf8(aggregateType))
                              .setValue(ByteString.copyFromUtf8(aggregateId))
                              .build();

        TaggedEvent taggedEvent = TaggedEvent.newBuilder()
                                             .setEvent(event)
                                             .addTag(aggregateTag)
                                             .build();

        tx.append(taggedEvent);
        tx.commit().get();
    }
}
