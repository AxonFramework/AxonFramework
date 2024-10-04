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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Test class validating the {@code default} methods of the {@link EventSink}.
 *
 * @author Steven van Beelen
 */
class EventSinkTest {

    private static final String TEST_CONTEXT = "some-context";

    private AtomicReference<String> contextReference;
    private AtomicReference<List<EventMessage<?>>> publishedEventsReference;

    private EventSink testSubject;

    @BeforeEach
    void setUp() {
        contextReference = new AtomicReference<>();
        publishedEventsReference = new AtomicReference<>();

        testSubject = new DefaultEventSink(contextReference, publishedEventsReference);
    }

    @Test
    void publishWithContextInvokesPublishWithoutContextInPostInvocationPhase() {
        EventMessage<?> testEventZero = eventMessage(0);
        EventMessage<?> testEventOne = eventMessage(1);

        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.runOnPreInvocation(processingContext -> testSubject.publish(processingContext,
                                                                        TEST_CONTEXT,
                                                                        testEventZero, testEventOne))
           .runOnInvocation(processingContext -> assertNull(publishedEventsReference.get()))
           .runOnCommit(processingContext -> assertFalse(publishedEventsReference.get().isEmpty()));
        awaitCompletion(uow.execute());

        assertEquals(TEST_CONTEXT, contextReference.get());
        assertEquals(Arrays.asList(testEventZero, testEventOne), publishedEventsReference.get());
    }

    static class DefaultEventSink implements EventSink {

        private final AtomicReference<String> contextReference;
        private final AtomicReference<List<EventMessage<?>>> publishedEventsReference;

        public DefaultEventSink(AtomicReference<String> contextReference,
                                AtomicReference<List<EventMessage<?>>> publishedEventsReference) {
            this.contextReference = contextReference;
            this.publishedEventsReference = publishedEventsReference;
        }

        @Override
        public CompletableFuture<Void> publish(@Nonnull String context,
                                               @Nonnull List<EventMessage<?>> events) {
            contextReference.set(context);
            publishedEventsReference.set(events);
            return CompletableFuture.completedFuture(null);
        }
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    private static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertFalse(completion.isCompletedExceptionally(),
                                                () -> completion.exceptionNow().toString()));
        return completion.join();
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    private static EventMessage<?> eventMessage(int seq) {
        return GenericEventMessage.asEventMessage("Event[" + seq + "]");
    }
}