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

import org.axonframework.messaging.ApplicationContext;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link SimpleEventBus}.
 *
 * @author Rene de Waele
 * @author Mateusz Nowak
 */
class AbstractEventBusTest {

    private static final MessageType TEST_EVENT_NAME = new MessageType("event");

    private UnitOfWorkFactory unitOfWorkFactory;
    private StubPublishingEventBus testSubject;

    @BeforeEach
    void setUp() {
        ApplicationContext appContext = EmptyApplicationContext.INSTANCE;
        unitOfWorkFactory = new SimpleUnitOfWorkFactory(appContext);
        testSubject = new StubPublishingEventBus(false);
    }

    @Nested
    class WithProcessingContext {

        @Test
        void eventsAreQueuedAndPublished() {
            // given
            EventMessage event = newEvent();
            UnitOfWork uow = unitOfWorkFactory.create();

            // when
            uow.runOnInvocation(ctx -> testSubject.publish(ctx, event));
            CompletableFuture<Void> result = uow.execute();

            // then
            assertThat(result).isDone();
            assertThat(result).isNotCompletedExceptionally();
            assertThat(testSubject.committedEvents).containsExactly(event);
        }

        @Test
        void multipleEventsArePublishedInOrder() {
            // given
            EventMessage eventA = newEvent();
            EventMessage eventB = newEvent();
            UnitOfWork uow = unitOfWorkFactory.create();

            // when
            uow.runOnInvocation(ctx -> {
                testSubject.publish(ctx, eventA);
                testSubject.publish(ctx, eventB);
            });
            uow.execute().join();

            // then
            assertThat(testSubject.committedEvents).containsExactly(eventA, eventB);
        }

        @Test
        void eventsPublishedDuringPrepareCommitAreAlsoProcessed() {
            // given
            UnitOfWork uow = unitOfWorkFactory.create();

            // when
            uow.runOnInvocation(ctx -> testSubject.publish(ctx, numberedEvent(2)));
            uow.execute().join();

            // then
            // Events should be: 2, 1, 0 (each prepareCommit publishes N-1)
            assertThat(testSubject.committedEvents).containsExactly(
                    numberedEvent(2), numberedEvent(1), numberedEvent(0)
            );
        }

        @Test
        void eventsPublishedWithoutContextAreProcessedImmediately() {
            // given
            EventMessage event = newEvent();

            // when
            testSubject.publish(null, event).join();

            // then
            assertThat(testSubject.committedEvents).containsExactly(event);
        }

        @Test
        void subsequentPublicationsInSameContextReuseHandlers() {
            // given
            EventMessage event1 = newEvent();
            EventMessage event2 = newEvent();
            UnitOfWork uow = unitOfWorkFactory.create();

            // when
            uow.runOnInvocation(ctx -> {
                testSubject.publish(ctx, event1);
                testSubject.publish(ctx, event2);
            });
            uow.execute().join();

            // then
            // Both events should be queued and published together
            assertThat(testSubject.committedEvents).containsExactly(event1, event2);
        }

        @Test
        void errorDuringPrepareCommitPreventsCommit() {
            // given
            StubPublishingEventBus failingBus = new StubPublishingEventBus(true);
            UnitOfWork uow = unitOfWorkFactory.create();

            // when
            uow.runOnInvocation(ctx -> failingBus.publish(ctx, newEvent()));
            CompletableFuture<Void> result = uow.execute();

            // then
            assertThat(result).isCompletedExceptionally();
            assertThat(failingBus.committedEvents).isEmpty();
        }

        @Test
        void publicationForbiddenDuringCommitPhase() {
            // given
            UnitOfWork uow = unitOfWorkFactory.create();

            // when - try to publish during commit phase
            uow.runOnCommit(ctx -> testSubject.publish(ctx, newEvent()));
            CompletableFuture<Void> result = uow.execute();

            // then
            assertThat(result).isDone();
            assertThat(result).isCompletedExceptionally();
        }

        @Test
        void publicationForbiddenDuringAfterCommitPhase() {
            // given
            UnitOfWork uow = unitOfWorkFactory.create();

            // when - try to publish during afterCommit phase
            uow.runOnAfterCommit(ctx -> testSubject.publish(ctx, newEvent()));
            CompletableFuture<Void> result = uow.execute();

            // then
            assertThat(result).isDone();
            assertThat(result).isCompletedExceptionally();
        }
    }

    private static EventMessage newEvent() {
        return new GenericEventMessage(TEST_EVENT_NAME, new Object());
    }

    private static EventMessage numberedEvent(final int number) {
        return new StubNumberedEvent(number);
    }

    private static class StubPublishingEventBus extends DelegatingEventBus {

        private final List<EventMessage> committedEvents = new ArrayList<>();
        private final boolean throwExceptionDuringPrepare;

        private StubPublishingEventBus(boolean throwExceptionDuringPrepare) {
            super(new SimpleEventBus());
            this.throwExceptionDuringPrepare = throwExceptionDuringPrepare;

            // Subscribe to receive events during processing
            super.subscribe((events, context) -> {
                handleEvents(events, context);
                return CompletableFuture.completedFuture(null);
            });
        }

        private void handleEvents(@Nonnull List<? extends EventMessage> events, @Nullable ProcessingContext context) {
            if (throwExceptionDuringPrepare) {
                throw new RuntimeException("Simulated failure during prepare commit");
            }

            // For numbered events, publish event with N-1 during prepareCommit
            for (EventMessage event : new ArrayList<>(events)) {
                Object payload = event.payload();
                if (payload instanceof Integer) {
                    int number = (int) payload;
                    if (number > 0) {
                        // Publish additional event using the provided context
                        // This event should be queued if context is active
                        publish(context, numberedEvent(number - 1));
                    }
                }
            }

            committedEvents.addAll(events);
        }
    }

    private static class StubNumberedEvent extends GenericEventMessage {

        private static final MessageType TYPE = new MessageType("StubNumberedEvent");

        StubNumberedEvent(Integer payload) {
            super(TYPE, payload);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StubNumberedEvent that = (StubNumberedEvent) o;
            return Objects.equals(payload(), that.payload());
        }

        @Override
        public int hashCode() {
            return Objects.hash(payload());
        }

        @Override
        public String toString() {
            return "StubNumberedEvent{" + payload() + "}";
        }
    }
}
