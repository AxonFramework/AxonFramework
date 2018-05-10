/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.deadline;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.config.SagaConfiguration;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.SagaLifecycle;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;
import org.mockito.*;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests deadline manager functionality.
 *
 * @author Milan Savic
 */
@RunWith(Parameterized.class)
public class DeadlineTest {

    private static final int DEADLINE_TIMEOUT = 1000;
    private static final int CHILD_ENTITY_DEADLINE_TIMEOUT = 500;

    private final Function<Configuration, DeadlineManager> deadlineManagerBuilder;
    private Configuration configuration;

    public DeadlineTest(Function<Configuration, DeadlineManager> deadlineManagerBuilder) {
        this.deadlineManagerBuilder = deadlineManagerBuilder;
    }

    @Parameterized.Parameters
    public static Collection deadlineManagers() {
        ArrayList<Function<Configuration, DeadlineManager>> deadlineManagers = new ArrayList<>();
        deadlineManagers.add(DeadlineTest::simpleDeadlineManager);
        deadlineManagers.add(DeadlineTest::quartzDeadlineManager);
        return deadlineManagers;
    }

    private static DeadlineManager simpleDeadlineManager(Configuration c) {
        return new SimpleDeadlineManager(new DefaultDeadlineTargetLoader(c::repository, c::sagaRepository));
    }

    private static DeadlineManager quartzDeadlineManager(Configuration c) {
        try {
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            DefaultDeadlineTargetLoader deadlineTargetLoader = new DefaultDeadlineTargetLoader(c::repository,
                                                                                               c::sagaRepository);
            QuartzDeadlineManager quartzDeadlineManager = new QuartzDeadlineManager(scheduler, deadlineTargetLoader);
            scheduler.start();
            return quartzDeadlineManager;
        } catch (SchedulerException e) {
            throw new AxonConfigurationException("Unable to configure quartz scheduler", e);
        }
    }

    @Before
    public void setUp() {
        EventStore eventStore = spy(new EmbeddedEventStore(new InMemoryEventStorageEngine()));
        configuration = DefaultConfigurer.defaultConfiguration()
                                         .configureEventStore(c -> eventStore)
                                         .configureAggregate(MyAggregate.class)
                                         .registerModule(SagaConfiguration.subscribingSagaManager(MySaga.class))
                                         .registerComponent(DeadlineManager.class, deadlineManagerBuilder)
                                         .start();
    }

    @After
    public void tearDown() {
        configuration.shutdown();
    }

    @Test
    public void testDeadlineOnAggregate() throws InterruptedException {
        final String id = "id";
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(id));
        Thread.sleep(DEADLINE_TIMEOUT + 100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(configuration.eventStore(), times(2)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent(id), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new DeadlineOccurredEvent(new DeadlineInfo(id)), eventCaptor.getAllValues().get(1).getPayload());
    }

    @Test
    public void testDeadlineCancellationOnAggregate() throws InterruptedException {
        final String id = "id";
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(id, true));
        Thread.sleep(DEADLINE_TIMEOUT + 100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(configuration.eventStore(), times(1)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent(id), eventCaptor.getAllValues().get(0).getPayload());
    }

    @Test
    public void testDeadlineOnChildEntity() throws InterruptedException {
        final String id = "Id";
        configuration.commandGateway().sendAndWait(new CreateMyAggregateCommand(id));
        configuration.commandGateway().sendAndWait(new TriggerDeadlineInChildEntityCommand(id));
        Thread.sleep(DEADLINE_TIMEOUT + 100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(configuration.eventStore(), times(3)).publish(eventCaptor.capture());
        assertEquals(new MyAggregateCreatedEvent(id), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new DeadlineOccurredInChildEvent(new ChildDeadlineInfo("entity" + id)),
                     eventCaptor.getAllValues().get(1).getPayload());
        assertEquals(new DeadlineOccurredEvent(new DeadlineInfo(id)), eventCaptor.getAllValues().get(2).getPayload());
    }

    @Test
    public void testDeadlineOnSaga() throws InterruptedException {
        final String id = "Id";
        final boolean cancelBeforeDeadline = false;
        configuration.eventStore().publish(asEventMessage(new SagaStartingEvent(id, cancelBeforeDeadline)));
        Thread.sleep(DEADLINE_TIMEOUT + 100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(configuration.eventStore(), times(2)).publish(eventCaptor.capture());
        assertEquals(new SagaStartingEvent(id, cancelBeforeDeadline), eventCaptor.getAllValues().get(0).getPayload());
        assertEquals(new DeadlineOccurredEvent(new DeadlineInfo(id)), eventCaptor.getAllValues().get(1).getPayload());
    }

    @Test
    public void testDeadlineCancellationOnSaga() throws InterruptedException {
        final String id = "Id";
        final boolean cancelBeforeDeadline = true;
        configuration.eventStore().publish(asEventMessage(new SagaStartingEvent(id, cancelBeforeDeadline)));
        Thread.sleep(DEADLINE_TIMEOUT + 100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        verify(configuration.eventStore(), times(1)).publish(eventCaptor.capture());
        assertEquals(new SagaStartingEvent(id, cancelBeforeDeadline), eventCaptor.getAllValues().get(0).getPayload());
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
            return cancelBeforeDeadline == that.cancelBeforeDeadline &&
                    Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, cancelBeforeDeadline);
        }
    }

    private static class DeadlineInfo {

        private final String id;

        private DeadlineInfo(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DeadlineInfo that = (DeadlineInfo) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class ChildDeadlineInfo {

        private final String id;

        private ChildDeadlineInfo(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ChildDeadlineInfo that = (ChildDeadlineInfo) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class DeadlineOccurredEvent {

        private final DeadlineInfo deadlineInfo;

        private DeadlineOccurredEvent(DeadlineInfo deadlineInfo) {
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
            DeadlineOccurredEvent that = (DeadlineOccurredEvent) o;
            return Objects.equals(deadlineInfo, that.deadlineInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deadlineInfo);
        }
    }

    private static class DeadlineOccurredInChildEvent {

        private final ChildDeadlineInfo deadlineInfo;

        private DeadlineOccurredInChildEvent(ChildDeadlineInfo deadlineInfo) {
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

    @SuppressWarnings("unused")
    public static class MySaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void on(SagaStartingEvent sagaStartingEvent) {
            ScheduleToken scheduleToken = SagaLifecycle.scheduleDeadline(Duration.ofMillis(DEADLINE_TIMEOUT),
                                                                         new DeadlineInfo(sagaStartingEvent.id));
            if (sagaStartingEvent.isCancelBeforeDeadline()) {
                SagaLifecycle.cancelDeadline(scheduleToken);
            }
        }

        @DeadlineHandler
        public void on(DeadlineInfo deadlineInfo, @Timestamp Instant timestamp, EventStore eventStore) {
            assertNotNull(timestamp);
            eventStore.publish(asEventMessage(new DeadlineOccurredEvent(deadlineInfo)));
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregate {

        @AggregateIdentifier
        private String id;
        @AggregateMember
        private MyEntity myEntity;

        public MyAggregate() {
            // empty constructor
        }

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand command) {
            apply(new MyAggregateCreatedEvent(command.id));
            ScheduleToken scheduleToken = AggregateLifecycle.scheduleDeadline(Duration.ofMillis(DEADLINE_TIMEOUT),
                                                                              new DeadlineInfo(command.id));
            if (command.cancelBeforeDeadline) {
                AggregateLifecycle.cancelDeadline(scheduleToken);
            }
        }

        @EventSourcingHandler
        public void on(MyAggregateCreatedEvent event) {
            this.id = event.id;
            this.myEntity = new MyEntity(id);
        }

        @DeadlineHandler
        public void on(DeadlineInfo deadlineInfo, @Timestamp Instant timestamp) {
            assertNotNull(timestamp);
            apply(new DeadlineOccurredEvent(deadlineInfo));
        }

        @CommandHandler
        public void handle(TriggerDeadlineInChildEntityCommand command) {
            AggregateLifecycle.scheduleDeadline(Duration.ofMillis(CHILD_ENTITY_DEADLINE_TIMEOUT),
                                                new ChildDeadlineInfo("entity" + command.id));
        }
    }

    private static class MyEntity {

        private final String id;

        private MyEntity(String id) {
            this.id = id;
        }

        @DeadlineHandler
        public void on(ChildDeadlineInfo deadlineInfo, @Timestamp Instant timestamp) {
            assertNotNull(timestamp);
            apply(new DeadlineOccurredInChildEvent(deadlineInfo));
        }
    }
}
