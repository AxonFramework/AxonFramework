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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

import static org.awaitility.Awaitility.await;
import static org.axonframework.utils.DbSchedulerTestUtil.getAndStartScheduler;
import static org.axonframework.utils.DbSchedulerTestUtil.reCreateTable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
abstract class AbstractDbSchedulerEventSchedulerTest {

    @Autowired
    protected DataSource dataSource;

    private List<EventMessage<?>> publishedMessages;
    protected DbSchedulerEventScheduler eventScheduler;
    protected Scheduler scheduler;

    abstract Task<?> getTask();

    abstract boolean useBinaryPojo();

    @AfterEach
    void cleanUp() {
        if (!Objects.isNull(eventScheduler)) {
            eventScheduler.shutdown();
            eventScheduler = null;
        }
    }

    @BeforeEach
    void prepare() {
        reCreateTable(dataSource);
        publishedMessages = new ArrayList<>();
        EventBus eventBus = new InMemoryEventBus(publishedMessages);
        scheduler = spy(getAndStartScheduler(dataSource, getTask()));
        eventScheduler = DbSchedulerEventScheduler
                .builder()
                .scheduler(scheduler)
                .serializer(TestSerializer.JACKSON.getSerializer())
                .eventBus(eventBus)
                .useBinaryPojo(useBinaryPojo())
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
