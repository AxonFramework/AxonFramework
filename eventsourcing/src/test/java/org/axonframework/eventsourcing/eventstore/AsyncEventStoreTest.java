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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@code default} methods of the {@link AsyncEventStore}.
 *
 * @author Steven van Beelen
 */
class AsyncEventStoreTest {

    private AtomicReference<ProcessingContext> processingContextReference;
    private AtomicReference<List<EventMessage<?>>> appendedEventsReference;

    private AsyncEventStore testSubject;

    @BeforeEach
    void setUp() {
        processingContextReference = new AtomicReference<>();
        appendedEventsReference = new AtomicReference<>();
        appendedEventsReference.set(new ArrayList<>());

        testSubject = new StubEventStore(processingContextReference, appendedEventsReference);
    }

    @Test
    void publishInvokesEventStoreTransactionMethod() {
        ProcessingContext testProcessingContext = ProcessingContext.NONE;
        EventMessage<?> testEventZero = eventMessage(0);
        EventMessage<?> testEventOne = eventMessage(1);
        EventMessage<?> testEventTwo = eventMessage(2);

        testSubject.publish(testProcessingContext, testEventZero, testEventOne, testEventTwo);

        assertEquals(testProcessingContext, processingContextReference.get());
        List<EventMessage<?>> testAppendedEvents = appendedEventsReference.get();
        assertTrue(testAppendedEvents.contains(testEventZero));
        assertTrue(testAppendedEvents.contains(testEventOne));
        assertTrue(testAppendedEvents.contains(testEventTwo));
    }

    static class StubEventStore implements AsyncEventStore {

        private final AtomicReference<ProcessingContext> processingContext;
        private final AtomicReference<List<EventMessage<?>>> appendedEvents;

        public StubEventStore(AtomicReference<ProcessingContext> processingContext,
                              AtomicReference<List<EventMessage<?>>> appendedEvents) {
            this.processingContext = processingContext;
            this.appendedEvents = appendedEvents;
        }

        @Override
        public CompletableFuture<Void> publish(@Nonnull List<EventMessage<?>> events) {
            throw new UnsupportedOperationException("We don't need this method to test the defaulted methods.");
        }

        @Override
        public EventStoreTransaction transaction(@NotNull ProcessingContext processingContext) {
            this.processingContext.set(processingContext);
            return new TestEventStoreTransaction(appendedEvents);
        }

        @Override
        public void describeTo(@NotNull ComponentDescriptor descriptor) {
            throw new UnsupportedOperationException("We don't need this method to test the defaulted methods.");
        }
    }

    static class TestEventStoreTransaction implements EventStoreTransaction {

        private final AtomicReference<List<EventMessage<?>>> appendedEvents;

        TestEventStoreTransaction(AtomicReference<List<EventMessage<?>>> appendedEvents) {
            this.appendedEvents = appendedEvents;
        }

        @Override
        public MessageStream<EventMessage<?>> source(@NotNull SourcingCondition condition) {
            throw new UnsupportedOperationException("We don't need this method to test the defaulted methods.");
        }

        @Override
        public void appendEvent(@NotNull EventMessage<?> eventMessage) {
            appendedEvents.get().add(eventMessage);
        }

        @Override
        public void onAppend(@NotNull Consumer<EventMessage<?>> callback) {
            throw new UnsupportedOperationException("We don't need this method to test the defaulted methods.");
        }

        @Override
        public ConsistencyMarker appendPosition() {
            throw new UnsupportedOperationException("We don't need this method to test the defaulted methods.");
        }
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    private static EventMessage<?> eventMessage(int seq) {
        return new GenericEventMessage<>(new MessageType("test", "event", "0.0.1"), "Event[" + seq + "]");
    }
}