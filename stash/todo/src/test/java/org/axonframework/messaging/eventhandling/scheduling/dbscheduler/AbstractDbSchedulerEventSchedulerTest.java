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

package org.axonframework.messaging.eventhandling.scheduling.dbscheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import jakarta.annotation.Nullable;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventhandling.scheduling.ScheduleToken;
import org.axonframework.messaging.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.conversion.json.JacksonSerializer;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import jakarta.annotation.Nonnull;

import javax.sql.DataSource;

import static org.awaitility.Awaitility.await;
import static org.axonframework.common.util.DbSchedulerTestUtil.getScheduler;
import static org.axonframework.common.util.DbSchedulerTestUtil.reCreateTable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
abstract class AbstractDbSchedulerEventSchedulerTest {

    @Autowired
    protected DataSource dataSource;

    private List<EventMessage> publishedMessages;
    protected DbSchedulerEventScheduler eventScheduler;
    protected Scheduler scheduler;

    abstract Task<?> getTask(Supplier<DbSchedulerEventScheduler> eventSchedulerSupplier);

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
        DbSchedulerEventSchedulerSupplier supplier = new DbSchedulerEventSchedulerSupplier();
        scheduler = spy(getScheduler(dataSource, getTask(supplier)));
        eventScheduler = DbSchedulerEventScheduler
                .builder()
                .scheduler(scheduler)
                .serializer(JacksonSerializer.defaultSerializer())
                .eventBus(eventBus)
                .useBinaryPojo(useBinaryPojo())
                .build();
        supplier.set(eventScheduler);
        scheduler.start();
    }

    @Test
    void whenScheduleIsCalledThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ZERO, 1);
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();

        assertEquals(1, publishedMessage.payload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.timestamp()));
        assertTrue(publishedMessage.metadata().isEmpty());
    }

    @Test
    void whenScheduledInPastIsCalledThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ofSeconds(-10L), 1);

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();

        assertEquals(1, publishedMessage.payload());
    }

    @Test
    void whenScheduleIsCalledWithEventMessageMetadataShouldBePreserved() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("foo", "bar");
        EventMessage originalMessage =
                new GenericEventMessage(new MessageType("event"), 2, metadata);
        eventScheduler.schedule(Instant.now(), originalMessage);
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();

        assertEquals(2, publishedMessage.payload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.timestamp()));
        assertEquals(metadata, publishedMessage.metadata());
    }

    @Test
    void whenScheduleIsCalledAndThereIsARevisionThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ZERO, new PayloadWithRevision());
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();

        assertEquals(new PayloadWithRevision(), publishedMessage.payload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.timestamp()));
        assertTrue(publishedMessage.metadata().isEmpty());
    }

    @Test
    void whenScheduleIsCalledWithEventThatHasARevisionPayloadMessageMetadataShouldBePreserved() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("foo", "bar");
        EventMessage originalMessage = new GenericEventMessage(
                new MessageType("event"), new PayloadWithRevision(), metadata
        );
        eventScheduler.schedule(Instant.now(), originalMessage);
        Instant rightAfterSchedule = Instant.now();

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();

        assertEquals(new PayloadWithRevision(), publishedMessage.payload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.timestamp()));
        assertEquals(metadata, publishedMessage.metadata());
    }

    @Test
    void rescheduleWithDurationShouldWork() {
        ScheduleToken token = eventScheduler.schedule(Duration.ofMillis(100L), 3);
        eventScheduler.reschedule(token, Duration.ofMillis(100L), 4);

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();
        assertEquals(4, publishedMessage.payload());
    }

    @Test
    void rescheduleWithInstantShouldWork() {
        ScheduleToken token = eventScheduler.schedule(Instant.now().plusMillis(100L), 5);
        eventScheduler.reschedule(token, Instant.now().plusMillis(100L), 6);

        await().atMost(Duration.ofSeconds(2)).until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();
        assertEquals(6, publishedMessage.payload());
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

        private final List<EventMessage> publishedMessages;

        private InMemoryEventBus(List<EventMessage> publishedMessages) {
            this.publishedMessages = publishedMessages;
        }

        @Override
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context, EventMessage... events) {
            return publish(context, Arrays.asList(events));
        }

        @Override
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                               @Nonnull List<? extends EventMessage> events) {
            publishedMessages.addAll(events);
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public Registration subscribe(@Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            // not needed in the test
        }
    }

    @Event(version = "Foo")
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
