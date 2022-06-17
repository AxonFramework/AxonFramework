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
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.annotation.MessageIdentifier;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.axonframework.messaging.deadletter.QueueIdentifier;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.utils.InMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the combination of an {@link org.axonframework.eventhandling.EventProcessor} containing a
 * {@link DeadLetteringEventHandlerInvoker} (a specific type of Processing Group). This test validates that:
 * <ul>
 *     <li>Handled {@link EventMessage EventMessages} are enqueued in a {@link DeadLetterQueue} if event handling fails.</li>
 *     <li>Handled {@code EventMessage EventMessages} are enqueued in a {@code DeadLetterQueue} if a previous event in that sequence was enqueued.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are successfully evaluated and removed from a {@code DeadLetterQueue}.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are unsuccessfully evaluated and enqueued in the {@code DeadLetterQueue} again.</li>
 *     <li>Concurrently publish events that succeed and/or fail and validate the evaluation by the {@code DeadLetterQueue}.</li>
 *     <li>Concurrently publish events in bulk that succeed and/or fail and validate the evaluation by the {@code DeadLetterQueue}.</li>
 * </ul>
 *
 * @author Steven van Beelen
 */
public abstract class DeadLetteringEventIntegrationTest {

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

    /**
     * Constructs the {@link DeadLetterQueue} implementation used during the integration test.
     *
     * @return A {@link DeadLetterQueue} implementation used during the integration test.
     */
    abstract DeadLetterQueue<EventMessage<?>> buildDeadLetterQueue();

    @BeforeEach
    void setUp() {
        TransactionManager transactionManager = NoTransactionManager.instance();

        eventHandlingComponent = new ProblematicEventHandlingComponent();
        deadLetterQueue = buildDeadLetterQueue();
        deadLetteringInvoker =
                DeadLetteringEventHandlerInvoker.builder()
                                                .eventHandlers(eventHandlingComponent)
                                                .sequencingPolicy(event -> ((DeadLetterableEvent) event.getPayload()).getAggregateIdentifier())
                                                .queue(deadLetterQueue)
                                                .processingGroup(PROCESSING_GROUP)
                                                .transactionManager(transactionManager)
                                                .build();

        eventSource = new InMemoryStreamableEventSource();
        streamingProcessor =
                PooledStreamingEventProcessor.builder()
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
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent("success", SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent("failure", FAIL)));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 2)
        );

        assertTrue(eventHandlingComponent.successfullyHandledOnFirstTry("success"));
        assertTrue(eventHandlingComponent.unsuccessfullyHandledOnFirstTry("failure"));

        assertTrue(deadLetterQueue.contains(new EventHandlingQueueIdentifier("failure", PROCESSING_GROUP)));
        assertFalse(deadLetterQueue.contains(new EventHandlingQueueIdentifier("success", PROCESSING_GROUP)));
    }

    @Test
    void testEventsInTheSameSequenceAreAllEnqueuedIfOneOfThemFails() {
        int expectedSuccessfulHandlingCount = 3;
        String aggregateId = UUID.randomUUID().toString();
        QueueIdentifier queueId = new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP);
        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail
        DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL);
        eventSource.publishMessage(asEventMessage(firstDeadLetter));
        DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED);
        eventSource.publishMessage(asEventMessage(secondDeadLetter));
        DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED);
        eventSource.publishMessage(asEventMessage(thirdDeadLetter));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedSuccessfulHandlingCount,
                     eventHandlingComponent.successfulHandlingCountOnFirstTry(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandledOnFirstTry(aggregateId));
        assertEquals(1, eventHandlingComponent.unsuccessfulHandlingCountOnFirstTry(aggregateId));

        assertTrue(deadLetterQueue.contains(queueId));

        // Release all entries so that they may be taken.
        deadLetterQueue.release();

        Optional<DeadLetter<EventMessage<?>>> first = deadLetterQueue.take(PROCESSING_GROUP);
        assertTrue(first.isPresent());
        assertEquals(firstDeadLetter, first.get().message().getPayload());
        // Acknowledging removes the letter from the queue, allowing us to check the following letter
        first.get().acknowledge();
        Optional<DeadLetter<EventMessage<?>>> second = deadLetterQueue.take(PROCESSING_GROUP);
        assertTrue(second.isPresent());
        assertEquals(secondDeadLetter, second.get().message().getPayload());
        second.get().acknowledge();
        Optional<DeadLetter<EventMessage<?>>> third = deadLetterQueue.take(PROCESSING_GROUP);
        assertTrue(third.isPresent());
        assertEquals(thirdDeadLetter, third.get().message().getPayload());
        third.get().acknowledge();
        assertFalse(deadLetterQueue.contains(queueId));
    }

    @Test
    void testSuccessfulEvaluationRemovesTheDeadLetterFromTheQueue() {
        int expectedSuccessfulHandlingCountOnFirstTry = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulHandlingCountOnFirstTry = 1;
        int expectedSuccessfulHandlingCountOnEvaluation = 3;
        int expectedUnsuccessfulHandlingCountOnEvaluation = 0;

        String aggregateId = UUID.randomUUID().toString();
        QueueIdentifier queueId = new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP);

        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail, but succeed on a retry
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedSuccessfulHandlingCountOnFirstTry,
                     eventHandlingComponent.successfulHandlingCountOnFirstTry(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedUnsuccessfulHandlingCountOnFirstTry,
                     eventHandlingComponent.unsuccessfulHandlingCountOnFirstTry(aggregateId));

        assertTrue(deadLetterQueue.contains(queueId));

        startDeadLetterEvaluation();

        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.successfullyHandledOnEvaluation(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulHandlingCountOnEvaluation,
                eventHandlingComponent.successfulHandlingCountOnEvaluation(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertFalse(eventHandlingComponent.unsuccessfullyHandledOnEvaluation(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedUnsuccessfulHandlingCountOnEvaluation,
                eventHandlingComponent.unsuccessfulHandlingCountOnEvaluation(aggregateId)
        ));

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(deadLetterQueue.contains(queueId)));
    }

    @Test
    void testUnsuccessfulEvaluationRequeuesTheDeadLetterInTheQueue() {
        int expectedSuccessfulHandlingCountOnFirstTry = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulHandlingCountOnFirstTry = 1;
        int expectedSuccessfulHandlingCountOnEvaluation = 2;
        int expectedUnsuccessfulHandlingCountOnEvaluation = 1;

        String aggregateId = UUID.randomUUID().toString();
        QueueIdentifier queueId = new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP);

        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail, but...
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));
        // ...the last retry fails.
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY)));

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedSuccessfulHandlingCountOnFirstTry,
                     eventHandlingComponent.successfulHandlingCountOnFirstTry(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedUnsuccessfulHandlingCountOnFirstTry,
                     eventHandlingComponent.unsuccessfulHandlingCountOnFirstTry(aggregateId));

        assertTrue(deadLetterQueue.contains(queueId));

        startDeadLetterEvaluation();

        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.successfullyHandledOnEvaluation(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulHandlingCountOnEvaluation,
                eventHandlingComponent.successfulHandlingCountOnEvaluation(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.unsuccessfullyHandledOnEvaluation(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedUnsuccessfulHandlingCountOnEvaluation,
                eventHandlingComponent.unsuccessfulHandlingCountOnEvaluation(aggregateId)
        ));

        assertWithin(1, TimeUnit.SECONDS, () -> {
            Optional<DeadLetter<EventMessage<?>>> requeuedLetter = deadLetterQueue.take(PROCESSING_GROUP);
            assertTrue(requeuedLetter.isPresent());
            DeadLetter<EventMessage<?>> result = requeuedLetter.get();
            assertEquals(new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY), result.message().getPayload());
            assertEquals(1, result.numberOfRetries());
        });
    }

    @Test
    void testPublishAndEvaluateEventsConcurrently() {
        int expectedSuccessfulHandlingCountOnFirstTry = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulHandlingCountOnFirstTry = 1;
        int expectedSuccessfulHandlingCountOnEvaluation = 2;
        int expectedUnsuccessfulHandlingCountOnEvaluation = 1;

        String aggregateId = UUID.randomUUID().toString();
        QueueIdentifier queueId = new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP);

        // Starting both is sufficient since both Processor and DeadLettering Invoker have their own thread pool.
        startProcessingEvent();
        startDeadLetterEvaluation();

        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail, but...
        DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY);
        eventSource.publishMessage(asEventMessage(firstDeadLetter));
        DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY);
        eventSource.publishMessage(asEventMessage(secondDeadLetter));
        // ...the last retry fails.
        DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY);
        eventSource.publishMessage(asEventMessage(thirdDeadLetter));


        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6)
        );

        assertTrue(eventHandlingComponent.successfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedSuccessfulHandlingCountOnFirstTry,
                     eventHandlingComponent.successfulHandlingCountOnFirstTry(aggregateId));
        assertTrue(eventHandlingComponent.unsuccessfullyHandledOnFirstTry(aggregateId));
        assertEquals(expectedUnsuccessfulHandlingCountOnFirstTry,
                     eventHandlingComponent.unsuccessfulHandlingCountOnFirstTry(aggregateId));

        assertTrue(deadLetterQueue.contains(queueId));

        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.successfullyHandledOnEvaluation(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulHandlingCountOnEvaluation,
                eventHandlingComponent.successfulHandlingCountOnEvaluation(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.unsuccessfullyHandledOnEvaluation(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedUnsuccessfulHandlingCountOnEvaluation,
                eventHandlingComponent.unsuccessfulHandlingCountOnEvaluation(aggregateId)
        ));

        assertWithin(1, TimeUnit.SECONDS, () -> {
            Optional<DeadLetter<EventMessage<?>>> requeuedLetter = deadLetterQueue.take(PROCESSING_GROUP);
            assertTrue(requeuedLetter.isPresent());
            DeadLetter<EventMessage<?>> result = requeuedLetter.get();
            assertEquals(new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY), result.message().getPayload());
            assertEquals(1, result.numberOfRetries());
        });
    }

    @Test
    @Timeout(10)
    void testPublishAndEvaluateEventsConcurrentlyInBulk() throws InterruptedException {
        int immediateSuccessesPerAggregate = 5;
        int failFirstAndThenSucceedPerAggregate = 4;
        int persistentFailingPerAggregate = 1;
        int expectedSuccessfulHandlingAfterEvaluation =
                failFirstAndThenSucceedPerAggregate - persistentFailingPerAggregate;
        int expectedSuccessfulHandlingCountAfterEvaluation =
                immediateSuccessesPerAggregate + expectedSuccessfulHandlingAfterEvaluation;

        int publishingRuns = 100;
        int totalNumberOfEvents =
                (immediateSuccessesPerAggregate + failFirstAndThenSucceedPerAggregate) * publishingRuns;

        Set<String> aggregateIds = new HashSet<>();
        Thread publishingThread = new Thread(() -> {
            for (int i = 0; i < publishingRuns; i++) {
                String aggregateId = Integer.toString(i);
                publishEventsFor(aggregateId,
                                 immediateSuccessesPerAggregate,
                                 failFirstAndThenSucceedPerAggregate,
                                 persistentFailingPerAggregate);
                aggregateIds.add(aggregateId);
            }
        });

        startProcessingEvent();
        startDeadLetterEvaluation();

        publishingThread.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        assertWithin(2, TimeUnit.SECONDS, () -> {
            OptionalLong optionalPosition = streamingProcessor.processingStatus().get(0).getCurrentPosition();
            assertTrue(optionalPosition.isPresent());
            assertEquals(totalNumberOfEvents, optionalPosition.getAsLong());
        });

        for (String aggregateId : aggregateIds) {
            // Validate first try event handling...
            // Successful...
            assertWithin(500, TimeUnit.MILLISECONDS,
                         () -> assertTrue(eventHandlingComponent.successfullyHandledOnFirstTry(aggregateId)));
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                    immediateSuccessesPerAggregate,
                    eventHandlingComponent.successfulHandlingCountOnFirstTry(aggregateId)
            ));
            // Unsuccessful...
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(
                    eventHandlingComponent.unsuccessfullyHandledOnFirstTry(aggregateId)
            ));
            assertEquals(1, eventHandlingComponent.unsuccessfulHandlingCountOnFirstTry(aggregateId));

            // Validate evaluation event handling...
            // Successful...
            assertWithin(4, TimeUnit.SECONDS, () -> assertTrue(
                    eventHandlingComponent.successfullyHandledOnEvaluation(aggregateId)
            ));
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                    expectedSuccessfulHandlingAfterEvaluation,
                    eventHandlingComponent.successfulHandlingCountOnEvaluation(aggregateId)
            ));
            // Unsuccessful...
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(
                    eventHandlingComponent.unsuccessfullyHandledOnEvaluation(aggregateId)
            ));
            assertTrue(eventHandlingComponent.unsuccessfulHandlingCountOnEvaluation(aggregateId)
                               >= persistentFailingPerAggregate);

            // Overall...
            assertEquals(expectedSuccessfulHandlingCountAfterEvaluation,
                         eventHandlingComponent.overallSuccessfulHandlingCount(aggregateId));

            // Validate existence of Dead Letter...
            assertTrue(deadLetterQueue.contains(new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP)));
        }

        publishingThread.join();
    }

    private void publishEventsFor(String aggregateId,
                                  int immediateSuccessesPerAggregate,
                                  int failFirstAndThenSucceedPerAggregate,
                                  int persistentFailingPerAggregate) {
        for (int i = 0; i < immediateSuccessesPerAggregate; i++) {
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        }
        for (int i = 0; i < failFirstAndThenSucceedPerAggregate; i++) {
            if (i == 0) {
                eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY)));
            } else if (failFirstAndThenSucceedPerAggregate - persistentFailingPerAggregate == i) {
                eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY)));
            } else {
                eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId,
                                                                                  SUCCEED,
                                                                                  SUCCEED_RETRY)));
            }
        }
    }

    private static class ProblematicEventHandlingComponent {

        private final Map<String, Integer> successfullyHandledOnFirstTry = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> successfullyHandledOnEvaluation = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> unsuccessfullyHandledOnFirstTry = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> unsuccessfullyHandledOnEvaluation = new ConcurrentSkipListMap<>();
        private final Set<String> handledEvent = new ConcurrentSkipListSet<>();

        @EventHandler
        public void on(DeadLetterableEvent event, @MessageIdentifier String eventIdentifier) {
            String aggregateIdentifier = event.getAggregateIdentifier();

            if (!handledEvent.contains(eventIdentifier) && !unsuccessfullyHandledOnFirstTry(aggregateIdentifier)) {
                // This is the first time we get this event.
                handledEvent.add(eventIdentifier);
                if (event.shouldSucceedOnFirstTry()) {
                    successfullyHandledOnFirstTry.compute(
                            aggregateIdentifier, (id, count) -> count == null ? 1 : ++count
                    );
                } else {
                    unsuccessfullyHandledOnFirstTry.compute(aggregateIdentifier,
                                                            (id, count) -> count == null ? 1 : ++count);
                    throw new RuntimeException("Let's dead-letter event [" + aggregateIdentifier + "]");
                }
            } else {
                // This is the second, third, ... time we get this event.
                if (event.shouldSucceedOnEvaluation()) {
                    successfullyHandledOnEvaluation.compute(
                            aggregateIdentifier, (id, count) -> count == null ? 1 : ++count
                    );
                } else {
                    unsuccessfullyHandledOnEvaluation.compute(
                            aggregateIdentifier, (id, count) -> count == null ? 1 : ++count
                    );
                    throw new RuntimeException("Let's dead-letter event [" + aggregateIdentifier + "] again");
                }
            }
        }

        public boolean successfullyHandledOnFirstTry(String aggregateIdentifier) {
            return successfullyHandledOnFirstTry.containsKey(aggregateIdentifier);
        }

        public int successfulHandlingCountOnFirstTry(String aggregateIdentifier) {
            return successfullyHandledOnFirstTry(aggregateIdentifier)
                    ? successfullyHandledOnFirstTry.get(aggregateIdentifier) : 0;
        }

        public boolean successfullyHandledOnEvaluation(String aggregateIdentifier) {
            return successfullyHandledOnEvaluation.containsKey(aggregateIdentifier);
        }

        public int successfulHandlingCountOnEvaluation(String aggregateIdentifier) {
            return successfullyHandledOnEvaluation(aggregateIdentifier)
                    ? successfullyHandledOnEvaluation.get(aggregateIdentifier) : 0;
        }

        public boolean unsuccessfullyHandledOnFirstTry(String aggregateIdentifier) {
            return unsuccessfullyHandledOnFirstTry.containsKey(aggregateIdentifier);
        }

        public int unsuccessfulHandlingCountOnFirstTry(String aggregateIdentifier) {
            return unsuccessfullyHandledOnFirstTry(aggregateIdentifier)
                    ? unsuccessfullyHandledOnFirstTry.get(aggregateIdentifier) : 0;
        }

        public boolean unsuccessfullyHandledOnEvaluation(String aggregateIdentifier) {
            return unsuccessfullyHandledOnEvaluation.containsKey(aggregateIdentifier);
        }

        public int unsuccessfulHandlingCountOnEvaluation(String aggregateIdentifier) {
            return unsuccessfullyHandledOnEvaluation(aggregateIdentifier)
                    ? unsuccessfullyHandledOnEvaluation.get(aggregateIdentifier) : 0;
        }

        public int overallSuccessfulHandlingCount(String aggregateIdentifier) {
            return successfullyHandledOnFirstTry.get(aggregateIdentifier)
                    + successfullyHandledOnEvaluation.get(aggregateIdentifier);
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

        public boolean shouldSucceedOnFirstTry() {
            return shouldSucceed;
        }

        public boolean shouldSucceedOnEvaluation() {
            return shouldSucceedOnEvaluation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DeadLetterableEvent that = (DeadLetterableEvent) o;
            return shouldSucceed == that.shouldSucceed
                    && shouldSucceedOnEvaluation == that.shouldSucceedOnEvaluation
                    && Objects.equals(aggregateIdentifier, that.aggregateIdentifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregateIdentifier, shouldSucceed, shouldSucceedOnEvaluation);
        }

        @Override
        public String toString() {
            return "DeadLetterableEvent{" +
                    "aggregateIdentifier='" + aggregateIdentifier + '\'' +
                    ", shouldSucceed=" + shouldSucceed +
                    ", shouldSucceedOnEvaluation=" + shouldSucceedOnEvaluation +
                    '}';
        }
    }
}
