/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.integrationtests.deadline;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.integrationtests.utils.AssertUtils.assertWithin;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.spy;

/**
 * Tests whether a {@link DeadlineManager} implementations functions as expected.
 * This is an abstract (integration) test class, whose tests cases should work for any DeadlineManager implementation.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public abstract class AbstractDeadlineManagerTestSuite {

    private static final int DEADLINE_TIMEOUT = 100;
    private static final int DEADLINE_WAIT_THRESHOLD = 10 * DEADLINE_TIMEOUT;
    private static final int CHILD_ENTITY_DEADLINE_TIMEOUT = 50;
    private static final String IDENTIFIER = "id";
    private static final boolean CANCEL_BEFORE_DEADLINE = true;
    private static final boolean DO_NOT_CANCEL_BEFORE_DEADLINE = false;

    protected Configuration configuration;
    private List<Object> published;

    @Before
    public void setUp() {
        EventStore eventStore = spy(EmbeddedEventStore.builder()
                                                      .storageEngine(new InMemoryEventStorageEngine())
                                                      .build());
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .usingSubscribingEventProcessors()
                  .registerSaga(MySaga.class);
        configuration = configurer.configureEventStore(c -> eventStore)
                                  .configureAggregate(MyAggregate.class)
                                  .registerComponent(DeadlineManager.class, this::buildDeadlineManager)
                                  .start();

        published = new CopyOnWriteArrayList<>();
        configuration.eventBus().subscribe(msgs -> msgs.forEach(msg -> published.add(msg.getPayload())));
    }

    @After
    public void tearDown() {
        configuration.shutdown();
    }

    /**
     * Build the {@link DeadlineManager} to be tested.
     *
     * @param configuration the {@link Configuration} used to set up this test class
     * @return a {@link DeadlineManager} to be tested
     */
    public abstract DeadlineManager buildDeadlineManager(Configuration configuration);

    @Test
    public void testDeadlineOnAggregate() {
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER),
                              new DeadlineOccurredEvent(new DeadlinePayload(IDENTIFIER)));
    }

    @Test
    public void testDeadlineCancellationOnAggregate() {
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER, CANCEL_BEFORE_DEADLINE));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER));
    }

    @Test
    public void testDeadlineOnChildEntity() {
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER));
        configuration.commandGateway().sendAndWait(new TriggerDeadlineInChildEntityCommand(IDENTIFIER));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER),
                              new DeadlineOccurredInChildEvent(new EntityDeadlinePayload("entity" + IDENTIFIER)),
                              new DeadlineOccurredEvent(new DeadlinePayload(IDENTIFIER)));
    }

    @Test
    public void testDeadlineWithSpecifiedDeadlineName() {
        String expectedDeadlinePayload = "deadlinePayload";

        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER, CANCEL_BEFORE_DEADLINE));
        configuration.commandGateway().sendAndWait(new ScheduleSpecificDeadline(IDENTIFIER, expectedDeadlinePayload));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER),
                              new SpecificDeadlineOccurredEvent(expectedDeadlinePayload));
    }

    @Test
    public void testDeadlineWithoutPayload() {
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER, CANCEL_BEFORE_DEADLINE));
        configuration.commandGateway().sendAndWait(new ScheduleSpecificDeadline(IDENTIFIER, null));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER),
                              new SpecificDeadlineOccurredEvent(null));
    }

    @Test
    public void testHandlerInterceptorOnAggregate() {
        configuration.deadlineManager().registerHandlerInterceptor((uow, chain) -> {
            uow.transformMessage(deadlineMessage -> GenericDeadlineMessage
                    .asDeadlineMessage(deadlineMessage.getDeadlineName(), new DeadlinePayload("fakeId")));
            return chain.proceed();
        });
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER),
                              new DeadlineOccurredEvent(new DeadlinePayload("fakeId")));
    }

    @Test
    public void testDispatchInterceptorOnAggregate() {
        configuration.deadlineManager().registerDispatchInterceptor(messages -> (i, m) ->
                GenericDeadlineMessage.asDeadlineMessage(m.getDeadlineName(), new DeadlinePayload("fakeId")));
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(IDENTIFIER));

        assertPublishedEvents(new MyAggregateCreatedEvent(IDENTIFIER),
                              new DeadlineOccurredEvent(new DeadlinePayload("fakeId")));
    }

    @Test
    public void testDeadlineOnSaga() {
        EventMessage<Object> testEventMessage =
                asEventMessage(new SagaStartingEvent(IDENTIFIER, DO_NOT_CANCEL_BEFORE_DEADLINE));
        configuration.eventStore().publish(testEventMessage);

        assertPublishedEvents(new SagaStartingEvent(IDENTIFIER, DO_NOT_CANCEL_BEFORE_DEADLINE),
                              new DeadlineOccurredEvent(new DeadlinePayload(IDENTIFIER)));
    }

    @Test
    public void testDeadlineCancellationOnSaga() {
        configuration.eventStore().publish(asEventMessage(new SagaStartingEvent(IDENTIFIER, CANCEL_BEFORE_DEADLINE)));

        assertPublishedEvents(new SagaStartingEvent(IDENTIFIER, CANCEL_BEFORE_DEADLINE));
    }

    @Test
    public void testDeadlineWithSpecifiedDeadlineNameOnSaga() {
        String expectedDeadlinePayload = "deadlinePayload";

        configuration.eventStore().publish(asEventMessage(new SagaStartingEvent(IDENTIFIER, CANCEL_BEFORE_DEADLINE)));
        configuration.eventStore().publish(asEventMessage(
                new ScheduleSpecificDeadline(IDENTIFIER, expectedDeadlinePayload))
        );

        assertPublishedEvents(new SagaStartingEvent(IDENTIFIER, CANCEL_BEFORE_DEADLINE),
                              new ScheduleSpecificDeadline(IDENTIFIER, expectedDeadlinePayload),
                              new SpecificDeadlineOccurredEvent(expectedDeadlinePayload));
    }

    @Test
    public void testDeadlineWithoutPayloadOnSaga() {
        configuration.eventStore().publish(asEventMessage(new SagaStartingEvent(IDENTIFIER, CANCEL_BEFORE_DEADLINE)));
        configuration.eventStore().publish(asEventMessage(new ScheduleSpecificDeadline(IDENTIFIER, null)));

        assertPublishedEvents(new SagaStartingEvent(IDENTIFIER, CANCEL_BEFORE_DEADLINE),
                              new ScheduleSpecificDeadline(IDENTIFIER, null),
                              new SpecificDeadlineOccurredEvent(null));
    }

    @Test
    public void testHandlerInterceptorOnSaga() {
        EventMessage<Object> testEventMessage =
                asEventMessage(new SagaStartingEvent(IDENTIFIER, DO_NOT_CANCEL_BEFORE_DEADLINE));
        configuration.deadlineManager().registerHandlerInterceptor((uow, chain) -> {
            uow.transformMessage(deadlineMessage -> GenericDeadlineMessage
                    .asDeadlineMessage(deadlineMessage.getDeadlineName(), new DeadlinePayload("fakeId")));
            return chain.proceed();
        });
        configuration.eventStore().publish(testEventMessage);

        assertPublishedEvents(new SagaStartingEvent(IDENTIFIER, DO_NOT_CANCEL_BEFORE_DEADLINE),
                              new DeadlineOccurredEvent(new DeadlinePayload("fakeId")));
    }

    @Test
    public void testDispatchInterceptorOnSaga() {
        EventMessage<Object> testEventMessage =
                asEventMessage(new SagaStartingEvent(IDENTIFIER, DO_NOT_CANCEL_BEFORE_DEADLINE));
        configuration.deadlineManager().registerDispatchInterceptor(messages -> (i, m) ->
                GenericDeadlineMessage.asDeadlineMessage(m.getDeadlineName(), new DeadlinePayload("fakeId")));
        configuration.eventStore().publish(testEventMessage);

        assertPublishedEvents(new SagaStartingEvent(IDENTIFIER, DO_NOT_CANCEL_BEFORE_DEADLINE),
                              new DeadlineOccurredEvent(new DeadlinePayload("fakeId")));
    }

    private void assertPublishedEvents(Object... expectedEvents) {
        assertWithin(DEADLINE_WAIT_THRESHOLD,
                     TimeUnit.MILLISECONDS,
                     () -> assertEquals(asList(expectedEvents), published));
    }

    private static class CreateMyAggregateCommand {

        private final String id;
        private final boolean cancelBeforeDeadline;

        private CreateMyAggregateCommand(String id) {
            this(id, false);
        }

        private CreateMyAggregateCommand(String id, boolean cancelBeforeDeadline) {
            this.id = id;
            this.cancelBeforeDeadline = cancelBeforeDeadline;
        }
    }

    private static class TriggerDeadlineInChildEntityCommand {

        @TargetAggregateIdentifier
        private final String id;

        private TriggerDeadlineInChildEntityCommand(String id) {
            this.id = id;
        }
    }

    private static class ScheduleSpecificDeadline {

        @TargetAggregateIdentifier
        private final String id;
        private final Object payload;

        private ScheduleSpecificDeadline(String id, Object payload) {
            this.id = id;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, payload);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final ScheduleSpecificDeadline other = (ScheduleSpecificDeadline) obj;
            return Objects.equals(this.id, other.id)
                    && Objects.equals(this.payload, other.payload);
        }
    }

    private static class MyAggregateCreatedEvent {

        private final String id;

        private MyAggregateCreatedEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyAggregateCreatedEvent that = (MyAggregateCreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class SagaStartingEvent {

        private final String id;
        private final boolean cancelBeforeDeadline;

        private SagaStartingEvent(String id, boolean cancelBeforeDeadline) {
            this.id = id;
            this.cancelBeforeDeadline = cancelBeforeDeadline;
        }

        public String getId() {
            return id;
        }

        public boolean isCancelBeforeDeadline() {
            return cancelBeforeDeadline;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SagaStartingEvent that = (SagaStartingEvent) o;
            return cancelBeforeDeadline == that.cancelBeforeDeadline && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, cancelBeforeDeadline);
        }
    }

    private static class DeadlinePayload {

        private final String id;

        // No-arg constructor used for Jackson Serialization
        @SuppressWarnings("unused")
        private DeadlinePayload() {
            this("some-id");
        }

        private DeadlinePayload(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DeadlinePayload that = (DeadlinePayload) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class EntityDeadlinePayload {

        private final String id;

        // No-arg constructor used for Jackson Serialization
        @SuppressWarnings("unused")
        private EntityDeadlinePayload() {
            this("some-id");
        }

        private EntityDeadlinePayload(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EntityDeadlinePayload that = (EntityDeadlinePayload) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class DeadlineOccurredEvent {

        private final DeadlinePayload deadlinePayload;

        private DeadlineOccurredEvent(DeadlinePayload deadlinePayload) {
            this.deadlinePayload = deadlinePayload;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DeadlineOccurredEvent that = (DeadlineOccurredEvent) o;
            return Objects.equals(deadlinePayload, that.deadlinePayload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deadlinePayload);
        }
    }

    private static class DeadlineOccurredInChildEvent {

        private final EntityDeadlinePayload deadlineInfo;

        private DeadlineOccurredInChildEvent(EntityDeadlinePayload deadlineInfo) {
            this.deadlineInfo = deadlineInfo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DeadlineOccurredInChildEvent that = (DeadlineOccurredInChildEvent) o;
            return Objects.equals(deadlineInfo, that.deadlineInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deadlineInfo);
        }
    }

    private static class SpecificDeadlineOccurredEvent {

        private final Object payload;

        private SpecificDeadlineOccurredEvent(Object payload) {
            this.payload = payload;
        }

        @Override
        public int hashCode() {
            return Objects.hash(payload);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final SpecificDeadlineOccurredEvent other = (SpecificDeadlineOccurredEvent) obj;
            return Objects.equals(this.payload, other.payload);
        }
    }

    @SuppressWarnings("unused")
    public static class MySaga {

        @Autowired
        private transient EventStore eventStore;

        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void on(SagaStartingEvent sagaStartingEvent, DeadlineManager deadlineManager) {
            String deadlineName = "deadlineName";
            String deadlineId = deadlineManager.schedule(
                    Duration.ofMillis(DEADLINE_TIMEOUT), deadlineName, new DeadlinePayload(sagaStartingEvent.id)
            );

            if (sagaStartingEvent.isCancelBeforeDeadline()) {
                deadlineManager.cancelSchedule(deadlineName, deadlineId);
            }
        }

        @SagaEventHandler(associationProperty = "id")
        public void on(ScheduleSpecificDeadline message, DeadlineManager deadlineManager) {
            Object deadlinePayload = message.payload;
            if (deadlinePayload != null) {
                deadlineManager.schedule(Duration.ofMillis(DEADLINE_TIMEOUT), "specificDeadlineName", deadlinePayload);
            } else {
                deadlineManager.schedule(Duration.ofMillis(DEADLINE_TIMEOUT), "payloadlessDeadline");
            }
        }

        @DeadlineHandler
        public void on(DeadlinePayload deadlinePayload, @Timestamp Instant timestamp) {
            assertNotNull(timestamp);
            eventStore.publish(asEventMessage(new DeadlineOccurredEvent(deadlinePayload)));
        }

        @DeadlineHandler(deadlineName = "specificDeadlineName")
        public void on(Object deadlinePayload, @Timestamp Instant timestamp) {
            assertNotNull(timestamp);
            eventStore.publish(asEventMessage(new SpecificDeadlineOccurredEvent(deadlinePayload)));
        }

        @DeadlineHandler(deadlineName = "payloadlessDeadline")
        public void on() {
            eventStore.publish(asEventMessage(new SpecificDeadlineOccurredEvent(null)));
        }
    }

    @SuppressWarnings("unused")
    public static class MyAggregate {

        @AggregateIdentifier
        private String id;
        @AggregateMember
        private MyEntity myEntity;

        public MyAggregate() {
            // empty constructor
        }

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand command, DeadlineManager deadlineManager) {
            apply(new MyAggregateCreatedEvent(command.id));

            String deadlineName = "deadlineName";
            String deadlineId = deadlineManager.schedule(
                    Duration.ofMillis(DEADLINE_TIMEOUT), deadlineName, new DeadlinePayload(command.id)
            );

            if (command.cancelBeforeDeadline) {
                deadlineManager.cancelSchedule(deadlineName, deadlineId);
            }
        }

        @CommandHandler
        public void on(ScheduleSpecificDeadline message, DeadlineManager deadlineManager) {
            Object deadlinePayload = message.payload;
            if (deadlinePayload != null) {
                deadlineManager.schedule(Duration.ofMillis(DEADLINE_TIMEOUT), "specificDeadlineName", deadlinePayload);
            } else {
                deadlineManager.schedule(Duration.ofMillis(DEADLINE_TIMEOUT), "payloadlessDeadline");
            }
        }

        @EventSourcingHandler
        public void on(MyAggregateCreatedEvent event) {
            this.id = event.id;
            this.myEntity = new MyEntity(id);
        }

        @DeadlineHandler
        public void on(DeadlinePayload deadlinePayload, @Timestamp Instant timestamp) {
            assertNotNull(timestamp);
            apply(new DeadlineOccurredEvent(deadlinePayload));
        }

        @DeadlineHandler(deadlineName = "specificDeadlineName")
        public void on(Object deadlinePayload) {
            apply(new SpecificDeadlineOccurredEvent(deadlinePayload));
        }

        @DeadlineHandler(deadlineName = "payloadlessDeadline")
        public void on() {
            apply(new SpecificDeadlineOccurredEvent(null));
        }

        @CommandHandler
        public void handle(TriggerDeadlineInChildEntityCommand command, DeadlineManager deadlineManager) {
            deadlineManager.schedule(
                    Duration.ofMillis(CHILD_ENTITY_DEADLINE_TIMEOUT),
                    "deadlineName",
                    new EntityDeadlinePayload("entity" + command.id)
            );
        }
    }

    public static class MyEntity {

        @EntityId
        private final String id;

        private MyEntity(String id) {
            this.id = id;
        }

        @DeadlineHandler
        public void on(EntityDeadlinePayload deadlineInfo, @Timestamp Instant timestamp) {
            assertNotNull(timestamp);
            apply(new DeadlineOccurredInChildEvent(deadlineInfo));
        }
    }
}
