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

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
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

    private static final String TEST_CONTEXT = "some-context";

    private AtomicReference<ProcessingContext> processingContextReference;
    private AtomicReference<String> contextReference;
    private AtomicReference<List<EventMessage<?>>> appendedEventsReference;

    private AsyncEventStore testSubject;

    @BeforeEach
    void setUp() {
        processingContextReference = new AtomicReference<>();
        contextReference = new AtomicReference<>();
        appendedEventsReference = new AtomicReference<>();
        appendedEventsReference.set(new ArrayList<>());

        testSubject = new DefaultEventStore(processingContextReference, contextReference, appendedEventsReference);
    }

    @Test
    void publishWithContextInvokesEventStoreTransactionMethod() {
        ProcessingContext testProcessingContext = ProcessingContext.NONE;
        EventMessage<?> testEventZero = eventMessage(0);
        EventMessage<?> testEventOne = eventMessage(1);
        EventMessage<?> testEventTwo = eventMessage(2);

        testSubject.publish(testProcessingContext, TEST_CONTEXT,
                            testEventZero, testEventOne, testEventTwo);

        assertEquals(testProcessingContext, processingContextReference.get());
        assertEquals(TEST_CONTEXT, contextReference.get());
        List<EventMessage<?>> testAppendedEvents = appendedEventsReference.get();
        assertTrue(testAppendedEvents.contains(testEventZero));
        assertTrue(testAppendedEvents.contains(testEventOne));
        assertTrue(testAppendedEvents.contains(testEventTwo));
    }

    static class DefaultEventStore implements AsyncEventStore {

        private final AtomicReference<ProcessingContext> processingContext;
        private final AtomicReference<String> context;
        private final AtomicReference<List<EventMessage<?>>> appendedEvents;

        public DefaultEventStore(AtomicReference<ProcessingContext> processingContext,
                                 AtomicReference<String> context,
                                 AtomicReference<List<EventMessage<?>>> appendedEvents) {
            this.processingContext = processingContext;
            this.context = context;
            this.appendedEvents = appendedEvents;
        }

        @Override
        public CompletableFuture<Void> publish(@NotNull String context, EventMessage<?>... events) {
            throw new UnsupportedOperationException("We don't need this method to test the defaulted methods.");
        }

        @Override
        public EventStoreTransaction transaction(@NotNull ProcessingContext processingContext,
                                                 @NotNull String context) {
            this.processingContext.set(processingContext);
            this.context.set(context);
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
        public MessageStream<EventMessage<?>> source(@NotNull SourcingCondition condition,
                                                     @NotNull ProcessingContext context) {
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
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    private static EventMessage<?> eventMessage(int seq) {
        return GenericEventMessage.asEventMessage("Event[" + seq + "]");
    }
}