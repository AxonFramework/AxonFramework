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

package org.axonframework.messaging.eventhandling.scheduling.jobrunr;

import jakarta.annotation.Nonnull;
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
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.JobActivator;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.awaitility.Awaitility.await;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRunrEventSchedulerTest {

    private List<EventMessage> publishedMessages;
    private JobRunrEventScheduler eventScheduler;
    private BackgroundJobServer backgroundJobServer;
    private JobScheduler jobScheduler;

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


    @BeforeEach
    void prepare() {
        publishedMessages = new ArrayList<>();
        EventBus eventBus = new InMemoryEventBus(publishedMessages);
        StorageProvider storageProvider = new InMemoryStorageProvider();
        jobScheduler = spy(new JobScheduler(storageProvider));
        eventScheduler = JobRunrEventScheduler
                .builder()
                .jobScheduler(jobScheduler)
                .serializer(JacksonSerializer.defaultSerializer())
                .eventBus(eventBus)
                .build();
        JobRunr.configure()
               .useJobActivator(new SimpleActivator(eventScheduler))
               .useStorageProvider(storageProvider)
               .useBackgroundJobServer(
                       usingStandardBackgroundJobServerConfiguration().andPollInterval(Duration.ofMillis(200))
               )
               .initialize();
        backgroundJobServer = JobRunr.getBackgroundJobServer();
    }

    @Test
    void whenScheduleIsCalledThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ZERO, 1);
        Instant rightAfterSchedule = Instant.now();

        await().until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();

        assertEquals(1, publishedMessage.payload());
        assertTrue(rightAfterSchedule.isBefore(publishedMessage.timestamp()));
        assertTrue(publishedMessage.metadata().isEmpty());
    }

    @Test
    void whenScheduledInPastIsCalledThanShouldPublishEvent() {
        eventScheduler.schedule(Duration.ofSeconds(-10L), 1);

        await().until(() -> !publishedMessages.isEmpty());
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

        await().until(() -> !publishedMessages.isEmpty());
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

        await().until(() -> !publishedMessages.isEmpty());
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
                new MessageType("message"), new PayloadWithRevision(), metadata
        );
        eventScheduler.schedule(Instant.now(), originalMessage);
        Instant rightAfterSchedule = Instant.now();

        await().until(() -> !publishedMessages.isEmpty());
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

        await().until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();
        assertEquals(4, publishedMessage.payload());
    }

    @Test
    void rescheduleWithInstantShouldWork() {
        ScheduleToken token = eventScheduler.schedule(Instant.now().plusMillis(100L), 5);
        eventScheduler.reschedule(token, Instant.now().plusMillis(100L), 6);

        await().until(() -> !publishedMessages.isEmpty());
        assertEquals(1, publishedMessages.size());

        EventMessage publishedMessage = publishedMessages.getFirst();
        assertEquals(6, publishedMessage.payload());
    }

    @Test
    void shutdownCalledOnScheduler() {
        eventScheduler.shutdown();
        verify(jobScheduler, times(1)).shutdown();
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
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                               @Nonnull List<EventMessage> events) {
            publishedMessages.addAll(events);
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public Registration subscribe(
                @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }
    }

    public static class SimpleActivator implements JobActivator {

        private final JobRunrEventScheduler eventScheduler;

        SimpleActivator(JobRunrEventScheduler eventScheduler) {
            this.eventScheduler = eventScheduler;
        }

        @Override
        public <T> T activateJob(Class<T> type) {
            if (type.isAssignableFrom(JobRunrEventScheduler.class)) {
                return type.cast(eventScheduler);
            }
            return null;
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
}
