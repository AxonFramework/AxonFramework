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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorEventHandlingComponentsTest {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorEventHandlingComponentsTest.class);

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        var threadNumber = new AtomicInteger(1);
        executorService = Executors.newScheduledThreadPool(20, (runnable) -> {
            Thread thread = new Thread(runnable);
            thread.setName("Test-Executor-" + threadNumber.getAndIncrement());
            return thread;
        });
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @Test
    void test3() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        var future1_1 = CompletableFuture.<EventMessage<?>>completedFuture(EventTestUtils.asEventMessage(new TestPayload1(
                "response-1_1")));

        EventHandlingComponent eventHandlingComponent1 = new SequencingEventHandlingComponent(
                new SequenceOverridingEventHandlingComponent(
                        (event) -> Optional.of(sequenceOf(event)),
                        new SimpleEventHandlingComponent()
                )
        );
        var trackingHandler1_1 = new RecordingEventHandler("Handler 1_1", future1_1);
        eventHandlingComponent1.subscribe(new QualifiedName(TestPayload1.class), trackingHandler1_1);

        var processorComponents = new ProcessorEventHandlingComponents(eventHandlingComponent1);

        // when
        var event1 = EventTestUtils.asEventMessage(new TestPayload1("event-1_seq-A"));
        var event2 = EventTestUtils.asEventMessage(new TestPayload1("event-2_seq-A"));
        var event3 = EventTestUtils.asEventMessage(new TestPayload1("event-3_seq-B"));
        var event4 = EventTestUtils.asEventMessage(new TestPayload1("event-4_seq-A"));
        var event5 = EventTestUtils.asEventMessage(new TestPayload1("event-5_seq-A"));
        var event6 = EventTestUtils.asEventMessage(new TestPayload1("event-6_seq-B"));
        var event7 = EventTestUtils.asEventMessage(new TestPayload1("event-7_seq-A"));
        var batch = List.of(event1, event2, event3, event4, event5, event6, event7);

        var unitOfWork = new SimpleUnitOfWorkFactory().create();
        unitOfWork.onInvocation(ctx -> processorComponents.handle(batch, ctx).asCompletableFuture());
        unitOfWork.execute().get(1, TimeUnit.SECONDS);

        // then
        assertThat(trackingHandler1_1.getHandledEvents())
                .hasSize(7)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-1_seq-A", "event-2_seq-A", "event-3_seq-A", "event-4_seq-A");
    }

    @RepeatedTest(3)
    void test6_async() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        var service = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<EventMessage<?>> future1_1 = CompletableFuture.supplyAsync(() -> EventTestUtils.asEventMessage("sample-response"), service);

        EventHandlingComponent eventHandlingComponent1 = new SequenceOverridingEventHandlingComponent(
                (event) -> Optional.of(sequenceOf(event)),
                new SimpleEventHandlingComponent()
        );
        var trackingHandler1_1 = new RecordingEventHandler("Handler 1_1", () -> future1_1);
        eventHandlingComponent1.subscribe(new QualifiedName(TestPayload1.class), trackingHandler1_1);

        var processorComponents = new ProcessorEventHandlingComponents(eventHandlingComponent1);

        // when
        var event1 = EventTestUtils.asEventMessage(new TestPayload1("event-1_seq-A"));
        var event2 = EventTestUtils.asEventMessage(new TestPayload1("event-2_seq-A"));
        var event3 = EventTestUtils.asEventMessage(new TestPayload1("event-3_seq-B"));
        var event4 = EventTestUtils.asEventMessage(new TestPayload1("event-4_seq-A"));
        var event5 = EventTestUtils.asEventMessage(new TestPayload1("event-5_seq-A"));
        var event6 = EventTestUtils.asEventMessage(new TestPayload1("event-6_seq-B"));
        var event7 = EventTestUtils.asEventMessage(new TestPayload1("event-7_seq-A"));
        var batch = List.of(event1, event2, event3, event4, event5, event6, event7);

        var unitOfWork = new SimpleUnitOfWorkFactory().create();
        unitOfWork.onInvocation(ctx -> processorComponents.handle(batch, ctx).asCompletableFuture());
        unitOfWork.execute().get(1, TimeUnit.SECONDS);

        // then
        var handledEvents = trackingHandler1_1.getHandledEvents();
        logger.info("Handled events: {}", handledEvents.stream().map(it -> it.getPayload().toString()).toList());
        var seqAEvents = handledEvents.stream()
                                      .filter(event -> event.getPayload().toString().contains("seq-A")).toList();

        var seqBEvents = handledEvents.stream()
                                      .filter(event -> event.getPayload().toString().contains("seq-B")).toList();

        assertThat(seqAEvents)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-1_seq-A", "event-2_seq-A", "event-4_seq-A", "event-5_seq-A", "event-7_seq-A");
        assertThat(seqBEvents)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-3_seq-B", "event-6_seq-B");
    }

    /**
     * Test implementation of EventHandler that tracks handled events. When the internal future completes, the handled
     * event is stored in an internal list.
     */
    static class RecordingEventHandler implements EventHandler {

        private static final Logger logger = LoggerFactory.getLogger(RecordingEventHandler.class);

        private final String name;
        private final Supplier<CompletableFuture<EventMessage<?>>> handlingLogic;
        private final List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();

        RecordingEventHandler(String name, CompletableFuture<EventMessage<?>> handlingLogic) {
            this.name = name;
            this.handlingLogic = () -> handlingLogic;
        }

        RecordingEventHandler(String name, Supplier<CompletableFuture<EventMessage<?>>> handlingLogic) {
            this.name = name;
            this.handlingLogic = handlingLogic;
        }


        @Nonnull
        @Override
        public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                         @Nonnull ProcessingContext context) {
            return MessageStream.fromFuture(
                    handlingLogic.get().thenApplyAsync(completedEvent -> {
                        logger.info("Handler {}, handled event {}", name, event.getPayload());
                        handledEvents.add(event);
                        return completedEvent;
                    })
            ).ignoreEntries().cast();
        }

        public List<EventMessage<?>> getHandledEvents() {
            return List.copyOf(handledEvents);
        }
    }

    record TestPayload1(String value) {

        @Override
        public String toString() {
            return value;
        }
    }

    record TestPayload2(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Extracts the sequence identifier from an event with string representation containing "_seq-" pattern. For example, "event-1_seq-A" returns
     * "A", "event-2_seq-B" returns "B".
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

}