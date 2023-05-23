/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.scheduling.dbscheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import com.github.kagkarlsson.scheduler.task.Task;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.jobrunr.server.BackgroundJobServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
class DbSchedulerEventSchedulerTest {

    @Autowired
    private DataSource dataSource;

    private List<EventMessage<?>> publishedMessages;
    private DbSchedulerEventScheduler eventScheduler;
    private BackgroundJobServer backgroundJobServer;
    private Scheduler scheduler;

    @AfterEach
    void cleanUp() {
        if (eventScheduler != null) {
            eventScheduler.shutdown();
        }
        if (!Objects.isNull(backgroundJobServer)) {
            backgroundJobServer.stop();
            backgroundJobServer = null;
        }
    }

    private void reCreateTable() {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement statement = connection.prepareStatement("drop table if exists scheduled_tasks;")) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "create table scheduled_tasks (\n"
                                     + "  task_name varchar(40) not null,\n"
                                     + "  task_instance varchar(40) not null,\n"
                                     + "  task_data blob,\n"
                                     + "  execution_time timestamp(6) not null,\n"
                                     + "  picked BOOLEAN not null,\n"
                                     + "  picked_by varchar(50),\n"
                                     + "  last_success timestamp(6) null,\n"
                                     + "  last_failure timestamp(6) null,\n"
                                     + "  consecutive_failures INT,\n"
                                     + "  last_heartbeat timestamp(6) null,\n"
                                     + "  version BIGINT not null,\n"
                                     + "  PRIMARY KEY (task_name, task_instance),\n"
                                     + ")")) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    @BeforeEach
    void prepare() {
        reCreateTable();
        publishedMessages = new ArrayList<>();
        EventBus eventBus = new InMemoryEventBus(publishedMessages);
        List<Task<?>> taskList = Collections.singletonList(DbSchedulerEventScheduler.task());
        scheduler = spy(new SchedulerBuilder(dataSource, taskList)
                                .threads(2)
                                .pollingInterval(Duration.ofMillis(50L))
                                .build());
        eventScheduler = DbSchedulerEventScheduler
                .builder()
                .scheduler(scheduler)
                .serializer(TestSerializer.JACKSON.getSerializer())
                .eventBus(eventBus)
                .build();
        scheduler.start();
    }

    @Test
    void whenScheduleIsCalledThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ZERO, 1);
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);

        assertEquals(1, publishedMessage.getPayload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.getTimestamp()));
        assertTrue(publishedMessage.getMetaData().isEmpty());
    }

    @Test
    void whenScheduledInPastIsCalledThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ofSeconds(-10L), 1);

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);

        assertEquals(1, publishedMessage.getPayload());
    }

    @Test
    void whenScheduleIsCalledWithEventMessageMetadataShouldBePreserved() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("foo", "bar");
        EventMessage<?> originalMessage = new GenericEventMessage<>(2, metadata);
        eventScheduler.schedule(Instant.now(), originalMessage);
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);

        assertEquals(2, publishedMessage.getPayload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.getTimestamp()));
        assertEquals(metadata, publishedMessage.getMetaData());
    }

    @Test
    void whenScheduleIsCalledAndThereIsARevisionThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ZERO, new PayloadWithRevision());
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);

        assertEquals(new PayloadWithRevision(), publishedMessage.getPayload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.getTimestamp()));
        assertTrue(publishedMessage.getMetaData().isEmpty());
    }

    @Test
    void whenScheduleIsCalledWithEventThatHasARevisionPayloadMessageMetadataShouldBePreserved() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("foo", "bar");
        EventMessage<?> originalMessage = new GenericEventMessage<>(new PayloadWithRevision(), metadata);
        eventScheduler.schedule(Instant.now(), originalMessage);
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);

        assertEquals(new PayloadWithRevision(), publishedMessage.getPayload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.getTimestamp()));
        assertEquals(metadata, publishedMessage.getMetaData());
    }

    @Test
    void rescheduleWithDurationShouldWork() {
        ScheduleToken token = eventScheduler.schedule(Duration.ofMillis(100L), 3);
        eventScheduler.reschedule(token, Duration.ofMillis(100L), 4);

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);
        assertEquals(4, publishedMessage.getPayload());
    }

    @Test
    void rescheduleWithInstantShouldWork() {
        ScheduleToken token = eventScheduler.schedule(Instant.now().plusMillis(100L), 5);
        eventScheduler.reschedule(token, Instant.now().plusMillis(100L), 6);

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage<?> publishedMessage = publishedMessages.get(0);
        assertEquals(6, publishedMessage.getPayload());
    }

    @Test
    void shutdownCalledOnScheduler() {
        eventScheduler.shutdown();
        verify(scheduler, times(1)).stop();
    }

    @Test
    void incorrectTokenClassShouldThrow() {
        ScheduleToken token = new SimpleScheduleToken("ff");
        assertThrows(IllegalArgumentException.class, () -> eventScheduler.cancelSchedule(token));
    }

    private static class InMemoryEventBus implements EventBus {

        private final List<EventMessage<?>> publishedMessages;

        private InMemoryEventBus(List<EventMessage<?>> publishedMessages) {
            this.publishedMessages = publishedMessages;
        }

        @Override
        public void publish(@Nonnull List<? extends EventMessage<?>> events) {
            publishedMessages.addAll(events);
        }

        @Override
        public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration registerDispatchInterceptor(
                @Nonnull MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
            throw new UnsupportedOperationException();
        }
    }

    @Revision("Foo")
    private static class PayloadWithRevision {

        PayloadWithRevision() {
            //nothing needs to happen here
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PayloadWithRevision;
        }
    }

    @Configuration
    public static class Context {

        @SuppressWarnings("Duplicates")
        @Bean
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:testdb");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
