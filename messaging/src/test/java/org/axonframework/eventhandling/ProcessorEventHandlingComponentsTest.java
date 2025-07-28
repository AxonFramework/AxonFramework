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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorEventHandlingComponentsTest {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorEventHandlingComponentsTest.class);

    @Test
    void test() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        var future1_1 = CompletableFuture.<EventMessage<?>>completedFuture(EventTestUtils.asEventMessage(new TestPayload1("response-1_1")));
        var future1_2 = CompletableFuture.<EventMessage<?>>completedFuture(EventTestUtils.asEventMessage(new TestPayload1("response-1_2")));
        var future2_1 = CompletableFuture.<EventMessage<?>>completedFuture(EventTestUtils.asEventMessage(new TestPayload1("response-2_1")));
        var future2_2 = CompletableFuture.<EventMessage<?>>completedFuture(EventTestUtils.asEventMessage(new TestPayload1("response-2_2")));

        EventHandlingComponent eventHandlingComponent1 = new SimpleEventHandlingComponent();
        var trackingHandler1_1 = new TrackingEventHandler("Handler 1_1", future1_1);
        eventHandlingComponent1.subscribe(new QualifiedName(TestPayload1.class), trackingHandler1_1);
        var trackingHandler1_2 = new TrackingEventHandler("Handler 1_2", future1_2);
        eventHandlingComponent1.subscribe(new QualifiedName(TestPayload2.class), trackingHandler1_2);

        EventHandlingComponent eventHandlingComponent2 = new SimpleEventHandlingComponent();
        var trackingHandler2_1 = new TrackingEventHandler("Handler 2_1", future2_1);
        eventHandlingComponent2.subscribe(new QualifiedName(TestPayload1.class), trackingHandler2_1);
        var trackingHandler2_2 = new TrackingEventHandler("Handler 2_2", future2_2);
        eventHandlingComponent2.subscribe(new QualifiedName(TestPayload2.class), trackingHandler2_2);

        var processorComponents = new ProcessorEventHandlingComponents(eventHandlingComponent1,
                                                                       eventHandlingComponent2);

        // when
        var event1 = EventTestUtils.asEventMessage(new TestPayload1("event-1"));
        var event2 = EventTestUtils.asEventMessage(new TestPayload2("event-2"));
        var batch = List.of(event1, event2);

        var unitOfWork = new SimpleUnitOfWorkFactory().create();
        unitOfWork.onInvocation(ctx -> processorComponents.handle(batch, ctx).whenComplete(() -> logger.info("Components completed")).asCompletableFuture());
        unitOfWork.execute().get(1, TimeUnit.SECONDS);

        // then
        assertThat(trackingHandler1_1.getHandledEvents())
                .hasSize(1)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-1");

        assertThat(trackingHandler1_2.getHandledEvents())
                .hasSize(1)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-2");

        assertThat(trackingHandler2_1.getHandledEvents())
                .hasSize(1)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-1");

        assertThat(trackingHandler2_2.getHandledEvents())
                .hasSize(1)
                .extracting(EventMessage::getPayload)
                .extracting("value")
                .containsExactly("event-2");
    }

    /**
     * Test implementation of EventHandler that tracks handled events.
     * When the internal future completes, the handled event is stored in an internal list.
     */
    static class TrackingEventHandler implements EventHandler {

        private static final Logger logger = LoggerFactory.getLogger(TrackingEventHandler.class);

        private final String name;
        private final CompletableFuture<EventMessage<?>> internalFuture;
        private final List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();

        TrackingEventHandler(String name, CompletableFuture<EventMessage<?>> internalFuture) {
            this.name = name;
            this.internalFuture = internalFuture;
        }

        @Nonnull
        @Override
        public MessageStream.Empty<org.axonframework.messaging.Message<Void>> handle(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
            return MessageStream.fromFuture(
                    internalFuture.thenApply(completedEvent -> {
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

    }

    record TestPayload2(String value) {

    }
}