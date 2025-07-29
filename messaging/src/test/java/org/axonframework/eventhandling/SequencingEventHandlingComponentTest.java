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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Class testing the {@link SequencingEventHandlingComponent} to ensure it correctly handles event sequencing.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SequencingEventHandlingComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(SequencingEventHandlingComponentTest.class);

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
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
            EventMessage<?> event = testEvent("event-1_seq-A");
            var unitOfWork = new SimpleUnitOfWorkFactory().create();
            var result = unitOfWork.executeWithResult((ctx) -> eventHandlingComponent.handle(event, ctx).asCompletableFuture());

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
        EventMessage<?> event = testEvent("event-1_seq-A");
        var unitOfWork = new SimpleUnitOfWorkFactory().create();
        var result = unitOfWork.executeWithResult((ctx) -> eventHandlingComponent.handle(event, ctx).asCompletableFuture());

        // then
        assertThat(result)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .isInstanceOf(ExecutionException.class)
                .havingCause()
                .isInstanceOf(RuntimeException.class)
                .withMessage("Test exception");
    }

    @RepeatedTest(3)
    void whenHandlersAreSyncOrderingIsPreservedInEntireBatch() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();
        var eventHandler = new RecordingSyncEventHandler("Handler 1_1", () -> testEvent("response-1_1"));
        eventHandlingComponent.subscribe(new QualifiedName(TestPayload.class), eventHandler);

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

        handleInUnitOfWork(eventHandlingComponent, batch);

        // then
        assertThat(eventHandler.recordedEvents())
                .hasSize(9)
                .extracting(EventMessage::getPayload)
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
    void whenHandlerFailsThenErrorIsPropagated() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(TestPayload.class), (event, ctx) -> MessageStream.failed(new RuntimeException("Test exception")));

        // when
        var event1 = testEvent("event-1_seq-A");
        var batch = List.of(event1);

        // then
        assertThatThrownBy(() -> handleInUnitOfWork(eventHandlingComponent, batch))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");;
    }

    @Nonnull
    private static EventMessage<Object> testEvent(String payload) {
        return EventTestUtils.asEventMessage(new TestPayload(
                payload));
    }

    @RepeatedTest(3)
    void whenHandlersAreAsyncOrderingIsPreservedAmongEventsWithSameSequenceIdentifier() {
        // given
        EventHandlingComponent eventHandlingComponent = sequencingEventHandlingComponent();

        var eventHandler1_1 = new RecordingAsyncEventHandler("Handler 1_1",
                () -> EventTestUtils.asEventMessage("sample-response"));
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

        handleInUnitOfWork(eventHandlingComponent, batch);

        // then
        var handledEvents = eventHandler1_1.recordedEvents();
        logger.info("Handled events: {}", handledEvents.stream().map(it -> it.getPayload().toString()).toList());
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

    @Nonnull
    private SequencingEventHandlingComponent sequencingEventHandlingComponent() {
        return new SequencingEventHandlingComponent(
                new SequenceOverridingEventHandlingComponent(
                        (event) -> Optional.of(sequenceOf(event)),
                        new SimpleEventHandlingComponent()
                )
        );
    }

    private static void handleInUnitOfWork(EventHandlingComponent component, List<EventMessage<Object>> batch) {
        var unitOfWork = new SimpleUnitOfWorkFactory().create();
        unitOfWork.onInvocation(context -> {
            MessageStream<Message<Void>> batchResult = MessageStream.empty().cast();
            for (var event : batch) {
                var eventResult = component.handle(event, context);
                batchResult = batchResult.concatWith(eventResult.cast());
            }
            return batchResult.ignoreEntries().asCompletableFuture();
        });
        try {
            unitOfWork.execute().get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    private List<String> payloadsOfSequence(List<EventMessage<?>> handledEvents, String sequence) {
        return handledEvents.stream()
                .filter(event -> event.getPayload().toString().contains("seq-" + sequence))
                .map(Message::getPayload)
                .map(Object::toString)
                .toList();
    }


    private static class RecordingAsyncEventHandler implements EventHandler {

        private static final Logger logger = LoggerFactory.getLogger(RecordingAsyncEventHandler.class);
        private final List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();

        private final String name;
        private final Supplier<EventMessage<?>> handlingLogic;

        RecordingAsyncEventHandler(String name, Supplier<EventMessage<?>> handlingLogic) {
            this.name = name;
            this.handlingLogic = handlingLogic;
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                         @Nonnull ProcessingContext context) {
            return MessageStream.fromFuture(
                    CompletableFuture.supplyAsync(() -> {
                        var result = handlingLogic.get();
                        logger.debug("Handler {}, handled event {}", name, event.getPayload());
                        handledEvents.add(event);
                        return result;
                    })
            ).ignoreEntries().cast();
        }

        public List<EventMessage<?>> recordedEvents() {
            return handledEvents;
        }
    }

    /**
     * Extracts the sequence identifier from an event with string representation containing "_seq-" pattern. For
     * example, "event-1_seq-A" returns "A", "event-2_seq-B" returns "B".
     */
    private static String sequenceOf(EventMessage<?> event) {
        var input = event.getPayload().toString();
        if (input == null) {
            return "";
        }

        int seqIndex = input.indexOf("_seq-");
        if (seqIndex == -1) {
            return "";
        }

        return input.substring(seqIndex + 5); // "_seq-" is 5 characters long
    }

    private static class RecordingSyncEventHandler implements EventHandler {

        private static final Logger logger = LoggerFactory.getLogger(RecordingSyncEventHandler.class);
        private final List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();

        private final String name;
        private final Supplier<EventMessage<?>> handlingLogic;

        RecordingSyncEventHandler(String name, Supplier<EventMessage<?>> handlingLogic) {
            this.name = name;
            this.handlingLogic = handlingLogic;
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                         @Nonnull ProcessingContext context) {
            var result = handlingLogic.get();
            logger.debug("Handler {}, handled event {}", name, event.getPayload());
            handledEvents.add(event);
            return MessageStream.fromIterable(List.of(result)).ignoreEntries().cast();
        }

        public List<EventMessage<?>> recordedEvents() {
            return handledEvents;
        }
    }

    record TestPayload(String value) {

        @Nonnull
        @Override
        public String toString() {
            return value;
        }
    }
}