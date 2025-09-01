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

package org.axonframework.eventhandling.processors.streaming.segmenting;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.*;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.axonframework.eventhandling.EventTestUtils.handleEventsInUnitOfWork;
import static org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils.*;

/**
 * Class testing the {@link SequencingEventHandlingComponent} to ensure it correctly handles event sequencing.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SequencingEventHandlingComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(SequencingEventHandlingComponentTest.class);

    private RecordingEventHandlingComponent internal;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        internal = new RecordingEventHandlingComponent(new SimpleEventHandlingComponent());
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @RepeatedTest(5)
    void whenHandlersAreAsyncOrderingIsPreservedAmongEventsWithSameSequenceIdentifier() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();

        EventHandler eventHandler1_1 = asyncEventHandler();
        eventHandlingComponent.subscribe(new QualifiedName(TestPayload.class), eventHandler1_1);

        // when
        var event1 = testEvent("event-1_seq-A");
        var event2 = testEvent("event-2_seq-A");
        var event3 = testEvent("event-3_seq-B");
        var event4 = testEvent("event-4_seq-A");
        var event5 = testEvent("event-5_seq-A");
        var event6 = testEvent("event-6_seq-B");
        var event7 = testEvent("event-7_seq-A");
        var event8 = testEvent("event-8_seq-B");
        var event9 = testEvent("event-9_seq-B");
        var batch = List.of(event1, event2, event3, event4, event5, event6, event7, event8, event9);

        handleEventsInUnitOfWork(eventHandlingComponent, batch);

        // then
        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(100))
               .untilAsserted(() -> assertThat(internal.recorded()).hasSameSizeAs(batch));
        var handledEvents = internal.recorded();
        logger.info("Handled events: {}", handledEvents.stream().map(it -> it.payload().toString()).toList());
        var seqAEvents = payloadsOfSequence(handledEvents, "A");
        var seqBEvents = payloadsOfSequence(handledEvents, "B");

        assertThat(seqAEvents)
                .containsExactly("event-1_seq-A",
                                 "event-2_seq-A",
                                 "event-4_seq-A",
                                 "event-5_seq-A",
                                 "event-7_seq-A");
        assertThat(seqBEvents)
                .containsExactly("event-3_seq-B", "event-6_seq-B", "event-8_seq-B", "event-9_seq-B");
    }

    @RepeatedTest(5)
    void whenHandlersAreSyncOrderingIsPreservedInEntireBatch() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(TestPayload.class), (event, context) -> MessageStream.empty());

        // when
        var event1 = testEvent("event-1_seq-A");
        var event2 = testEvent("event-2_seq-A");
        var event3 = testEvent("event-3_seq-B");
        var event4 = testEvent("event-4_seq-A");
        var event5 = testEvent("event-5_seq-A");
        var event6 = testEvent("event-6_seq-B");
        var event7 = testEvent("event-7_seq-A");
        var event8 = testEvent("event-8_seq-B");
        var event9 = testEvent("event-9_seq-B");
        var batch = List.of(event1, event2, event3, event4, event5, event6, event7, event8, event9);

        handleEventsInUnitOfWork(eventHandlingComponent, batch);

        // then
        assertThat(internal.recorded())
                .hasSize(9)
                .extracting(EventMessage::payload)
                .extracting("value")
                .containsExactly(
                        "event-1_seq-A",
                        "event-2_seq-A",
                        "event-3_seq-B",
                        "event-4_seq-A",
                        "event-5_seq-A",
                        "event-6_seq-B",
                        "event-7_seq-A",
                        "event-8_seq-B",
                        "event-9_seq-B"
                );
    }

    @Test
    void shouldHandleEmptyMessageStreamFromHandler() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();
        EventHandler emptyHandler = (event, context) -> MessageStream.empty();
        QualifiedName eventType = new QualifiedName(TestPayload.class);
        eventHandlingComponent.subscribe(eventType, emptyHandler);

        // when & then - Should complete without issues
        assertThatNoException().isThrownBy(() -> {
            EventMessage event = testEvent("event-1_seq-A");
            var unitOfWork = aUnitOfWork();
            var result = unitOfWork.executeWithResult((ctx) -> eventHandlingComponent.handle(event, ctx)
                                                                                     .asCompletableFuture());

            assertThat(result).isNotNull();
            assertThat(result.isDone()).isTrue();
        });
    }

    @Test
    void shouldPropagateException() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();
        RuntimeException testException = new RuntimeException("Test exception");
        EventHandler failingHandler = (event, context) -> MessageStream.failed(testException);

        QualifiedName eventType = new QualifiedName(TestPayload.class);
        eventHandlingComponent.subscribe(eventType, failingHandler);

        // when
        EventMessage event = testEvent("event-1_seq-A");
        var unitOfWork = aUnitOfWork();
        var result = unitOfWork.executeWithResult((ctx) -> eventHandlingComponent.handle(event, ctx)
                                                                                 .asCompletableFuture());

        // then
        assertThat(result)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .isInstanceOf(ExecutionException.class)
                .havingCause()
                .isInstanceOf(RuntimeException.class)
                .withMessage("Test exception");
    }

    @Nonnull
    private static EventMessage testEvent(String payload) {
        return EventTestUtils.asEventMessage(new TestPayload(payload));
    }

    @Nonnull
    private EventHandlingComponent sequencingEventHandlingComponent() {
        return new SequencingEventHandlingComponent(
                new SequenceOverridingEventHandlingComponent(
                        (event, context) -> Optional.of(sequenceOf(event)),
                        internal
                )
        );
    }

    @Nonnull
    private List<String> payloadsOfSequence(List<EventMessage> handledEvents, String sequence) {
        return handledEvents.stream()
                            .filter(event -> event.payload().toString().contains("seq-" + sequence))
                            .map(Message::payload)
                            .map(Object::toString)
                            .toList();
    }

    private EventHandler asyncEventHandler() {
        return (event, context) -> {
            CountDownLatch processingBarrier = new CountDownLatch(1);
            CountDownLatch completionBarrier = new CountDownLatch(1);

            CompletableFuture<EventMessage> asyncPipeline = CompletableFuture
                    .runAsync(() -> {
                        try {
                            processingBarrier.await(100, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            completionBarrier.countDown();
                        }
                    }, executorService)
                    .thenCompose(v -> CompletableFuture.supplyAsync(() -> {
                        try {
                            completionBarrier.await(100, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return testEvent("sample");
                    }, executorService));

            return MessageStream.fromFuture(asyncPipeline).ignoreEntries().cast();
        };
    }

    /**
     * Extracts the sequence identifier from an event with string representation containing "_seq-" pattern. For
     * example, "event-1_seq-A" returns "A", "event-2_seq-B" returns "B".
     */
    private static String sequenceOf(EventMessage event) {
        var input = event.payload().toString();
        if (input == null) {
            return "";
        }

        int seqIndex = input.indexOf("_seq-");
        if (seqIndex == -1) {
            return "";
        }

        return input.substring(seqIndex + 5); // "_seq-" is 5 characters long
    }

    record TestPayload(String value) {

        @Nonnull
        @Override
        public String toString() {
            return value;
        }
    }
}