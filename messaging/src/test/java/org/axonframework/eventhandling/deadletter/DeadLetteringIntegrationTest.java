/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.annotation.MessageIdentifier;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.axonframework.messaging.deadletter.InMemoryDeadLetterQueue;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.utils.InMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the combination of an {@link org.axonframework.eventhandling.EventProcessor} containing a
 * {@link DeadLetteringEventHandlerInvoker} (a specific type of Processing Group). This test validates that:
 * <ul>
 *     <li>Handled {@link EventMessage EventMessages} are enqueued in a {@link DeadLetterQueue} if event handling fails.</li>
 *     <li>Handled {@link EventMessage EventMessages} are enqueued in a {@link DeadLetterQueue} if a previous event in that sequence was enqueued.</li>
 *     <li>Enqueued {@link EventMessage EventMessages} are successfully evaluated and removed from a {@link DeadLetterQueue}.</li>
 *     <li>Enqueued {@link EventMessage EventMessages} are unsuccessfully evaluated and enqueued in the {@link DeadLetterQueue} again.</li>
 * </ul>
 *
 * @author Steven van Beelen
 */
class DeadLetteringIntegrationTest {

    private static final String PROCESSING_GROUP = "problematicProcessingGroup";
    private static final boolean SUCCEED = true;
    private static final boolean SUCCEED_RETRY = true;
    private static final boolean FAIL = false;
    private static final boolean FAIL_RETRY = false;

    private ProblematicEventHandlingComponent eventHandlingComponent;
    private DeadLetterQueue<EventMessage<?>> deadLetterQueue;
    private DeadLetteringEventHandlerInvoker deadLetteringInvoker;
    private InMemoryStreamableEventSource eventSource;
    private StreamingEventProcessor streamingProcessor;

    @BeforeEach
    void setUp() {
        TransactionManager transactionManager = NoTransactionManager.instance();

        eventHandlingComponent = new ProblematicEventHandlingComponent();
        deadLetterQueue = InMemoryDeadLetterQueue.<EventMessage<?>>builder()
                                                 .expireThreshold(Duration.ofMillis(50))
                                                 .scheduledExecutorService(Executors.newSingleThreadScheduledExecutor())
                                                 .build();
        deadLetteringInvoker = DeadLetteringEventHandlerInvoker.builder()
                                                               .eventHandlers(eventHandlingComponent)
                                                               .sequencingPolicy(event -> ((DeadLetterableEvent) event.getPayload()).getAggregateIdentifier())
                                                               .queue(deadLetterQueue)
                                                               .processingGroup(PROCESSING_GROUP)
                                                               .transactionManager(transactionManager)
                                                               .build();

        eventSource = new InMemoryStreamableEventSource();
        streamingProcessor = PooledStreamingEventProcessor.builder()
                                                          .name(PROCESSING_GROUP)
                                                          .eventHandlerInvoker(deadLetteringInvoker)
                                                          .rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
                                                          .messageSource(eventSource)
                                                          .tokenStore(new InMemoryTokenStore())
                                                          .transactionManager(transactionManager)
                                                          .coordinatorExecutor(Executors.newSingleThreadScheduledExecutor())
                                                          .workerExecutor(Executors.newSingleThreadScheduledExecutor())
                                                          .initialSegmentCount(1)
                                                          .claimExtensionThreshold(1000)
                                                          .build();
    }

    @AfterEach
    void tearDown() {
        CompletableFuture<Void> queueShutdown = deadLetterQueue.shutdown();
        CompletableFuture<Void> processorShutdown = streamingProcessor.shutdownAsync();
        try {
            CompletableFuture.allOf(queueShutdown, processorShutdown).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    void startProcessingEvent() {
        streamingProcessor.start();
    }

    void startDeadLetterEvaluation() {
        deadLetteringInvoker.start();
    }

    @Test
    void testFailedEventHandlingEnqueuesTheEvent() {
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent("success", SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent("failure", FAIL)));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 2)
        );

        assertTrue(eventHandlingComponent.successfullyHandled("success"));
        assertTrue(eventHandlingComponent.unsuccessfullyHandled("failure"));

        assertFalse(deadLetterQueue.isEmpty());
        assertTrue(deadLetterQueue.contains(new EventHandlingQueueIdentifier("failure", PROCESSING_GROUP)));
        assertFalse(deadLetterQueue.contains(new EventHandlingQueueIdentifier("success", PROCESSING_GROUP)));
    }

    @Test
    void testEventsInTheSameSequenceAreAllEnqueuedIfOneOfThemFails() {
        int expectedSuccessfulHandlingCount = 3;
        String aggregateId = UUID.randomUUID().toString();
        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail
        DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(firstDeadLetter));
        DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(secondDeadLetter));
        DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(thirdDeadLetter));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandled(aggregateId));
        assertEquals(expectedSuccessfulHandlingCount, eventHandlingComponent.successfulHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandled(aggregateId));
        assertEquals(1, eventHandlingComponent.unsuccessfulHandlingCount(aggregateId));

        assertFalse(deadLetterQueue.isEmpty());
        assertTrue(deadLetterQueue.contains(new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP)));
        Optional<DeadLetterEntry<EventMessage<?>>> first = deadLetterQueue.take(PROCESSING_GROUP);
        assertTrue(first.isPresent());
        assertEquals(firstDeadLetter, first.get().message().getPayload());
        // Acknowledging removes the letter from the queue, allowing us to check the following entry
        first.get().acknowledge();
        Optional<DeadLetterEntry<EventMessage<?>>> second = deadLetterQueue.take(PROCESSING_GROUP);
        assertTrue(second.isPresent());
        assertEquals(secondDeadLetter, second.get().message().getPayload());
        second.get().acknowledge();
        Optional<DeadLetterEntry<EventMessage<?>>> third = deadLetterQueue.take(PROCESSING_GROUP);
        assertTrue(third.isPresent());
        assertEquals(thirdDeadLetter, third.get().message().getPayload());
        third.get().acknowledge();
        assertTrue(deadLetterQueue.isEmpty());
    }

    @Test
    void testSuccessfulEvaluationRemovesTheDeadLetterFromTheQueue() {
        int expectedSuccessfulHandlingCount = 3;
        int expectedSuccessfulHandlingCountAfterEvaluation = 6;
        String aggregateId = UUID.randomUUID().toString();
        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail, but succeed on a retry
        DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(firstDeadLetter));
        DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(secondDeadLetter));
        DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(thirdDeadLetter));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandled(aggregateId));
        assertEquals(expectedSuccessfulHandlingCount, eventHandlingComponent.successfulHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandled(aggregateId));
        assertEquals(1, eventHandlingComponent.unsuccessfulHandlingCount(aggregateId));

        assertFalse(deadLetterQueue.isEmpty());
        assertTrue(deadLetterQueue.contains(new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP)));

        startDeadLetterEvaluation();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulHandlingCountAfterEvaluation,
                eventHandlingComponent.successfulHandlingCount(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(deadLetterQueue.isEmpty()));
    }

    @Test
    void testUnsuccessfulEvaluationRequeuesTheDeadLetterInTheQueue() {
        int expectedSuccessfulHandlingCount = 3;
        int expectedSuccessfulHandlingCountAfterEvaluation = 5;
        String aggregateId = UUID.randomUUID().toString();
        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail, but...
        DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(firstDeadLetter));
        DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(secondDeadLetter));
        // ...the last retry fails.
        DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY);
        eventSource.publishMessage(GenericEventMessage.asEventMessage(thirdDeadLetter));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandled(aggregateId));
        assertEquals(expectedSuccessfulHandlingCount, eventHandlingComponent.successfulHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandled(aggregateId));
        assertEquals(1, eventHandlingComponent.unsuccessfulHandlingCount(aggregateId));

        assertFalse(deadLetterQueue.isEmpty());
        assertTrue(deadLetterQueue.contains(new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP)));

        startDeadLetterEvaluation();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulHandlingCountAfterEvaluation,
                eventHandlingComponent.successfulHandlingCount(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertEquals(2, eventHandlingComponent.unsuccessfulHandlingCount(aggregateId)));
        assertFalse(deadLetterQueue.isEmpty());
        assertWithin(1, TimeUnit.SECONDS, () ->{
            Optional<DeadLetterEntry<EventMessage<?>>> requeuedLetter = deadLetterQueue.take(PROCESSING_GROUP);
            assertTrue(requeuedLetter.isPresent());
            DeadLetterEntry<EventMessage<?>> result = requeuedLetter.get();
            assertEquals(thirdDeadLetter, result.message().getPayload());
            assertEquals(1, result.numberOfRetries());
        });
    }

    private static class ProblematicEventHandlingComponent {

        private final Map<String, Integer> successfullyHandled = new HashMap<>();
        private final Map<String, Integer> unsuccessfullyHandled = new HashMap<>();
        private final Set<String> shouldSucceedOnEvaluation = new HashSet<>();

        @EventHandler
        public void on(DeadLetterableEvent event, @MessageIdentifier String eventIdentifier) {
            String aggregateIdentifier = event.getAggregateIdentifier();
            if ((event.shouldSucceed() && event.shouldSucceedOnEvaluation())
                    || shouldSucceedOnEvaluation.contains(eventIdentifier)) {
                successfullyHandled.compute(aggregateIdentifier, (id, count) -> count == null ? 1 : ++count);
            } else {
                if (event.shouldSucceedOnEvaluation()) {
                    shouldSucceedOnEvaluation.add(eventIdentifier);
                }
                unsuccessfullyHandled.compute(aggregateIdentifier, (id, count) -> count == null ? 1 : ++count);
                throw new RuntimeException("Let's dead-letter event [" + aggregateIdentifier + "]");
            }
        }

        public boolean successfullyHandled(String aggregateIdentifier) {
            return successfullyHandled.containsKey(aggregateIdentifier);
        }

        public int successfulHandlingCount(String aggregateIdentifier) {
            return successfullyHandled(aggregateIdentifier) ? successfullyHandled.get(aggregateIdentifier) : 0;
        }

        public boolean unsuccessfullyHandled(String aggregateIdentifier) {
            return unsuccessfullyHandled.containsKey(aggregateIdentifier);
        }

        public int unsuccessfulHandlingCount(String aggregateIdentifier) {
            return unsuccessfullyHandled(aggregateIdentifier) ? unsuccessfullyHandled.get(aggregateIdentifier) : 0;
        }
    }

    private static class DeadLetterableEvent {

        private final String aggregateIdentifier;
        private final boolean shouldSucceed;
        private final boolean shouldSucceedOnEvaluation;

        private DeadLetterableEvent(String aggregateIdentifier,
                                    boolean shouldSucceed) {
            this(aggregateIdentifier, shouldSucceed, true);
        }

        private DeadLetterableEvent(String aggregateIdentifier,
                                    boolean shouldSucceed,
                                    boolean shouldSucceedOnEvaluation) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.shouldSucceed = shouldSucceed;
            this.shouldSucceedOnEvaluation = shouldSucceedOnEvaluation;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public boolean shouldSucceed() {
            return shouldSucceed;
        }

        public boolean shouldSucceedOnEvaluation() {
            return shouldSucceedOnEvaluation;
        }
    }
}
