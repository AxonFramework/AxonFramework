/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventhandling.scheduling.jobrunr;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.TestSerializer;
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
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static org.awaitility.Awaitility.await;
import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRunrEventSchedulerTest {

    private List<EventMessage<?>> publishedMessages;
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
                .serializer(TestSerializer.JACKSON.getSerializer())
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
        EventMessage<?> originalMessage = new GenericEventMessage<>(QualifiedNameUtils.fromDottedName("test.event"), 2, metadata);
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
        EventMessage<?> originalMessage = new GenericEventMessage<>(
                QualifiedNameUtils.fromDottedName("test.PayloadWithRevision"), new PayloadWithRevision(), metadata
        );
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
        verify(jobScheduler, times(1)).shutdown();
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
}
