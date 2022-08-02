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
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MessageIdentifier;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequenceIdentifier;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.utils.InMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the combination of an {@link org.axonframework.eventhandling.EventProcessor} containing a
 * {@link DeadLetteringEventHandlerInvoker} (a specific type of Processing Group). This test validates that:
 *
 * <ul>
 *     <li>Handled {@link EventMessage EventMessages} are enqueued in a {@link SequencedDeadLetterQueue}
 *     if event handling fails.</li>
 *     <li>Handled {@code EventMessage EventMessages} are enqueued in a {@code DeadLetterQueue}
 *     if a previous event in that sequence was enqueued.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are successfully evaluated and evicted
 *     from a {@code DeadLetterQueue} on {@link DeadLetteringEventHandlerInvoker#processIdentifierMatchingSequence(Predicate)} ()}.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are unsuccessfully evaluated
 *     and requeued in the {@code DeadLetterQueue} on {@link DeadLetteringEventHandlerInvoker#processIdentifierMatchingSequence(Predicate)}.</li>
 *     <li>Concurrently publish events that succeed and/or fail and validate the evaluation
 *     by the {@code DeadLetterQueue}.</li>
 *     <li>Concurrently publish events in bulk that succeed and/or fail and validate the evaluation
 *     by the {@code DeadLetterQueue}.</li>
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
    private SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> deadLetterQueue;
    private DeadLetteringEventHandlerInvoker deadLetteringInvoker;
    private InMemoryStreamableEventSource eventSource;
    private StreamingEventProcessor streamingProcessor;

    private ScheduledExecutorService executor;

    /**
     * Constructs the {@link SequencedDeadLetterQueue} implementation used during the integration test.
     *
     * @return A {@link SequencedDeadLetterQueue} implementation used during the integration test.
     */
    abstract SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> buildDeadLetterQueue();

    @BeforeEach
    void setUp() {
        TransactionManager transactionManager = NoTransactionManager.instance();

        eventHandlingComponent = new ProblematicEventHandlingComponent();
        deadLetterQueue = buildDeadLetterQueue();

        // A policy that ensure a letter is only retried once by adding diagnostics.
        EnqueuePolicy<DeadLetter<EventMessage<?>>> enqueuePolicy = (letter, cause) -> {
            int retries = (int) letter.diagnostic().getOrDefault("retries", 0);
            if (retries < 1) {
                return Decisions.enqueue(
                        cause, l -> MetaData.with("retries", (int) l.diagnostic().getOrDefault("retries", 0) + 1)
                );
            } else {
                return Decisions.evict();
            }
        };

        deadLetteringInvoker =
                DeadLetteringEventHandlerInvoker.builder()
                                                .eventHandlers(eventHandlingComponent)
                                                .sequencingPolicy(event -> ((DeadLetterableEvent) event.getPayload()).getAggregateIdentifier())
                                                .processingGroup(PROCESSING_GROUP)
                                                .enqueuePolicy(enqueuePolicy)
                                                .queue(deadLetterQueue)
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

        executor = Executors.newScheduledThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        boolean executorTerminated = false;
        CompletableFuture<Void> processorShutdown = streamingProcessor.shutdownAsync();
        try {
            processorShutdown.get(15, TimeUnit.SECONDS);
            executorTerminated = executor.awaitTermination(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        if (!executorTerminated) {
            executor.shutdownNow();
        }
    }

    /**
     * Start this test's {@link StreamingEventProcessor}. This will start event handling.
     */
    protected void startProcessingEvent() {
        streamingProcessor.start();
    }

    /**
     * Process {@link DeadLetter dead-letters} matching the given {@code sequenceId}. Uses the
     * {@link DeadLetteringEventHandlerInvoker#processIdentifierMatchingSequence(Predicate)} operation for this.
     *
     * @param sequenceId The {@link SequenceIdentifier} to process matching {@link DeadLetter dead-letters} for.
     */
    protected void processDeadLettersFor(SequenceIdentifier sequenceId) {
        deadLetteringInvoker.processIdentifierMatchingSequence(identifier -> identifier.equals(sequenceId));
    }

    /**
     * Process any sequence of {@link DeadLetter dead-letters}. Uses the
     * {@link DeadLetteringEventHandlerInvoker#processAny()} operation for this.
     */
    protected void processAnyDeadLetter() {
        deadLetteringInvoker.processAny();
    }

    /**
     * Process {@link DeadLetter dead-letters} matching the given {@code sequenceId} at set intervals of 5ms. Uses the
     * {@link DeadLetteringEventHandlerInvoker#processIdentifierMatchingSequence(Predicate)} operation for this.
     *
     * @param sequenceId The {@link SequenceIdentifier} to process matching {@link DeadLetter dead-letters} for.
     */
    protected void processDeadLettersPeriodically(SequenceIdentifier sequenceId) {
        executor.scheduleWithFixedDelay(() -> processDeadLettersFor(sequenceId), 5, 5, TimeUnit.MILLISECONDS);
    }

    /**
     * Process any sequence of {@link DeadLetter dead-letters} at set intervals of 5ms. Uses the
     * {@link DeadLetteringEventHandlerInvoker#processAny()} operation for this.
     */
    protected void processAnyDeadLettersPeriodically() {
        executor.scheduleWithFixedDelay(this::processAnyDeadLetter, 5, 5, TimeUnit.MILLISECONDS);
    }

    @Test
    void testFailedEventHandlingEnqueuesTheEvent() {
        EventMessage<Object> failedEvent = asEventMessage(new DeadLetterableEvent("failure", FAIL));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent("success", SUCCEED)));
        eventSource.publishMessage(failedEvent);

        startProcessingEvent();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 2)
        );

        assertTrue(eventHandlingComponent.initialHandlingWasSuccessful("success"));
        assertTrue(eventHandlingComponent.initialHandlingWasUnsuccessful("failure"));

        SequenceIdentifier expectedId = new EventSequenceIdentifier("failure", PROCESSING_GROUP);
        assertTrue(deadLetterQueue.contains(expectedId));
        assertFalse(deadLetterQueue.contains(new EventSequenceIdentifier("success", PROCESSING_GROUP)));

        Iterator<DeadLetter<EventMessage<?>>> sequence = deadLetterQueue.deadLetterSequence(expectedId).iterator();
        assertTrue(sequence.hasNext());
        assertEquals(failedEvent.getPayload(), sequence.next().message().getPayload());
        assertFalse(sequence.hasNext());
    }

    @Test
    void testEventsInTheSameSequenceAreAllEnqueuedIfOneOfThemFails() {
        int expectedSuccessfulHandlingCount = 3;
        String aggregateId = UUID.randomUUID().toString();
        SequenceIdentifier sequenceId = new EventSequenceIdentifier(aggregateId, PROCESSING_GROUP);
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

        assertTrue(eventHandlingComponent.initialHandlingWasSuccessful(aggregateId));
        assertEquals(expectedSuccessfulHandlingCount,
                     eventHandlingComponent.successfulInitialHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.initialHandlingWasUnsuccessful(aggregateId));
        assertEquals(1, eventHandlingComponent.unsuccessfulInitialHandlingCount(aggregateId));

        assertTrue(deadLetterQueue.contains(sequenceId));
        Iterator<DeadLetter<EventMessage<?>>> sequence = deadLetterQueue.deadLetterSequence(sequenceId).iterator();
        assertTrue(sequence.hasNext());
        assertEquals(firstDeadLetter, sequence.next().message().getPayload());
        assertTrue(sequence.hasNext());
        assertEquals(secondDeadLetter, sequence.next().message().getPayload());
        assertTrue(sequence.hasNext());
        assertEquals(thirdDeadLetter, sequence.next().message().getPayload());
        assertFalse(sequence.hasNext());
    }

    @Test
    void testSuccessfulRetryingEvictsTheDeadLetterFromTheQueue() {
        int expectedSuccessfulInitialHandlingCount = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulInitialHandlingCount = 1;
        int expectedSuccessfulEvaluationCount = 3;
        int expectedUnsuccessfulEvaluationCount = 0;

        String aggregateId = UUID.randomUUID().toString();
        SequenceIdentifier sequenceId = new EventSequenceIdentifier(aggregateId, PROCESSING_GROUP);

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

        assertTrue(eventHandlingComponent.initialHandlingWasSuccessful(aggregateId));
        assertEquals(expectedSuccessfulInitialHandlingCount,
                     eventHandlingComponent.successfulInitialHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.initialHandlingWasUnsuccessful(aggregateId));
        assertEquals(expectedUnsuccessfulInitialHandlingCount,
                     eventHandlingComponent.unsuccessfulInitialHandlingCount(aggregateId));

        assertTrue(deadLetterQueue.contains(sequenceId));

        processDeadLettersFor(sequenceId);

        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.evaluationWasSuccessful(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulEvaluationCount,
                eventHandlingComponent.successfulEvaluationCount(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertFalse(eventHandlingComponent.evaluationWasUnsuccessful(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedUnsuccessfulEvaluationCount,
                eventHandlingComponent.unsuccessfulEvaluationCount(aggregateId)
        ));

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(deadLetterQueue.contains(sequenceId)));
    }

    @Test
    void testUnsuccessfulProcessingRequeuesTheDeadLetterInTheQueue() {
        int expectedSuccessfulInitialHandlingCount = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulInitialHandlingCount = 1;
        int expectedSuccessfulEvaluationCount = 2;
        int expectedUnsuccessfulEvaluationCount = 1;

        String aggregateId = UUID.randomUUID().toString();
        SequenceIdentifier sequenceId = new EventSequenceIdentifier(aggregateId, PROCESSING_GROUP);

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
        assertWithin(2, TimeUnit.SECONDS, () -> {
            OptionalLong optionalPosition = streamingProcessor.processingStatus().get(0).getCurrentPosition();
            assertTrue(optionalPosition.isPresent());
            assertTrue(optionalPosition.getAsLong() >= 6);
        });

        assertTrue(eventHandlingComponent.initialHandlingWasSuccessful(aggregateId));
        assertEquals(expectedSuccessfulInitialHandlingCount,
                     eventHandlingComponent.successfulInitialHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.initialHandlingWasUnsuccessful(aggregateId));
        assertEquals(expectedUnsuccessfulInitialHandlingCount,
                     eventHandlingComponent.unsuccessfulInitialHandlingCount(aggregateId));

        assertTrue(deadLetterQueue.contains(sequenceId));

        processDeadLettersFor(sequenceId);

        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.evaluationWasSuccessful(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedSuccessfulEvaluationCount,
                eventHandlingComponent.successfulEvaluationCount(aggregateId)
        ));
        assertWithin(1, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.evaluationWasUnsuccessful(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedUnsuccessfulEvaluationCount,
                eventHandlingComponent.unsuccessfulEvaluationCount(aggregateId)
        ));

        // As evaluation fails, the sequenceId should still exist
        assertTrue(deadLetterQueue.contains(sequenceId));
    }

    @Test
    void testPublishEventsAndProcessDeadLettersConcurrently() {
        int expectedSuccessfulInitialHandlingCount = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulInitialHandlingCount = 1;
        int expectedSuccessfulEvaluationCount = 2;
        int expectedUnsuccessfulEvaluationCount = 1;

        String aggregateId = UUID.randomUUID().toString();
        SequenceIdentifier sequenceId = new EventSequenceIdentifier(aggregateId, PROCESSING_GROUP);

        // Starting both is sufficient since both Processor and DeadLettering Invoker have their own thread pool.
        startProcessingEvent();
        processDeadLettersPeriodically(sequenceId);

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
        assertWithin(2, TimeUnit.SECONDS, () -> {
            OptionalLong optionalPosition = streamingProcessor.processingStatus().get(0).getCurrentPosition();
            assertTrue(optionalPosition.isPresent());
            assertTrue(optionalPosition.getAsLong() >= 6);
        });

        assertTrue(eventHandlingComponent.initialHandlingWasSuccessful(aggregateId));
        assertEquals(expectedSuccessfulInitialHandlingCount,
                     eventHandlingComponent.successfulInitialHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.initialHandlingWasUnsuccessful(aggregateId));
        assertEquals(expectedUnsuccessfulInitialHandlingCount,
                     eventHandlingComponent.unsuccessfulInitialHandlingCount(aggregateId));

        assertTrue(deadLetterQueue.contains(sequenceId));

        assertWithin(2, TimeUnit.SECONDS,
                     () -> assertTrue(eventHandlingComponent.evaluationWasSuccessful(aggregateId)));
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                expectedSuccessfulEvaluationCount,
                eventHandlingComponent.successfulEvaluationCount(aggregateId)
        ));
        assertWithin(500, TimeUnit.MILLISECONDS,
                     () -> assertTrue(eventHandlingComponent.evaluationWasUnsuccessful(aggregateId)));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                expectedUnsuccessfulEvaluationCount,
                eventHandlingComponent.unsuccessfulEvaluationCount(aggregateId)
        ));

        // As evaluation fails, the sequenceId should still exist
        assertTrue(deadLetterQueue.contains(sequenceId));
    }

    @Test
    @Timeout(10)
    void testPublishEventsAndProcessDeadLettersConcurrentlyInBulk() throws InterruptedException {
        int immediateSuccessesPerAggregate = 5;
        int failFirstAndThenSucceedPerAggregate = 4;
        int persistentFailingPerAggregate = 1;
        int expectedSuccessfulEvaluationCount = failFirstAndThenSucceedPerAggregate - persistentFailingPerAggregate;
        int expectedOverallSuccessfulHandlingCount = immediateSuccessesPerAggregate + expectedSuccessfulEvaluationCount;

        int publishingRuns = 100;
        int totalNumberOfEvents =
                (immediateSuccessesPerAggregate + failFirstAndThenSucceedPerAggregate) * publishingRuns;

        Set<String> aggregateIds = new HashSet<>();
        Set<String> validatedAggregateIds = new HashSet<>();
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
        processAnyDeadLettersPeriodically();

        publishingThread.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
        assertWithin(2, TimeUnit.SECONDS, () -> {
            OptionalLong optionalPosition = streamingProcessor.processingStatus().get(0).getCurrentPosition();
            assertTrue(optionalPosition.isPresent());
            assertEquals(totalNumberOfEvents, optionalPosition.getAsLong());
        });

        for (String aggregateId : aggregateIds) {
            if (validatedAggregateIds.contains(aggregateId)) {
                continue;
            }

            // Validate first try event handling...
            // Successful...
            assertWithin(500, TimeUnit.MILLISECONDS,
                         () -> assertTrue(eventHandlingComponent.initialHandlingWasSuccessful(aggregateId)));
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                    immediateSuccessesPerAggregate,
                    eventHandlingComponent.successfulInitialHandlingCount(aggregateId)
            ));
            // Unsuccessful...
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(
                    eventHandlingComponent.initialHandlingWasUnsuccessful(aggregateId)
            ));
            assertEquals(1, eventHandlingComponent.unsuccessfulInitialHandlingCount(aggregateId));

            // Validate evaluation event handling...
            // Successful...
            assertWithin(3, TimeUnit.SECONDS, () -> assertTrue(
                    eventHandlingComponent.evaluationWasSuccessful(aggregateId)
            ));
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                    expectedSuccessfulEvaluationCount,
                    eventHandlingComponent.successfulEvaluationCount(aggregateId)
            ));
            // Unsuccessful...
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(
                    eventHandlingComponent.evaluationWasUnsuccessful(aggregateId)
            ));
            assertTrue(eventHandlingComponent.unsuccessfulEvaluationCount(aggregateId)
                               >= persistentFailingPerAggregate);

            // Overall...
            assertEquals(expectedOverallSuccessfulHandlingCount,
                         eventHandlingComponent.overallSuccessfulHandlingCount(aggregateId));

            validatedAggregateIds.add(aggregateId);
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
                eventSource.publishMessage(
                        asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY))
                );
            }
        }
    }

    private static class ProblematicEventHandlingComponent {

        private final Set<String> handledEvent = new ConcurrentSkipListSet<>();
        private final Map<String, Integer> firstTrySuccesses = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> evaluationSuccesses = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> firstTryFailures = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> evaluationFailures = new ConcurrentSkipListMap<>();

        @EventHandler
        public void on(DeadLetterableEvent event, @MessageIdentifier String eventIdentifier) {
            // The aggregate identifier effectively references the sequence because of the configured SequencingPolicy.
            String sequenceId = event.getAggregateIdentifier();

            if (!handledEvent.contains(eventIdentifier)) {
                // This is the first time we get this event.
                handledEvent.add(eventIdentifier);
                if (!initialHandlingWasUnsuccessful(sequenceId)) {
                    // This is the first time we get this sequence.
                    processInitialHandlingOf(event, sequenceId);
                } else {
                    // This is the second, third, ... time we get this sequence.
                    processEvaluationOf(event, sequenceId);
                }
            } else {
                // This is the second, third, ... time we get this event.
                processEvaluationOf(event, sequenceId);
            }
        }

        private void processInitialHandlingOf(DeadLetterableEvent event, String sequenceId) {
            if (event.shouldSucceedOnFirstTry()) {
                firstTrySuccesses.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
            } else {
                firstTryFailures.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                throw new RuntimeException("Initial handling failed. Let's dead-letter event [" + sequenceId + "].");
            }
        }

        private void processEvaluationOf(DeadLetterableEvent event, String sequenceId) {
            if (event.shouldSucceedOnEvaluation()) {
                evaluationSuccesses.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
            } else {
                evaluationFailures.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                throw new RuntimeException("Evaluation failed. Let's dead-letter event [" + sequenceId + "].");
            }
        }

        public boolean initialHandlingWasSuccessful(String aggregateIdentifier) {
            return firstTrySuccesses.containsKey(aggregateIdentifier);
        }

        public int successfulInitialHandlingCount(String aggregateIdentifier) {
            return initialHandlingWasSuccessful(aggregateIdentifier) ? firstTrySuccesses.get(aggregateIdentifier) : 0;
        }

        public boolean evaluationWasSuccessful(String aggregateIdentifier) {
            return evaluationSuccesses.containsKey(aggregateIdentifier);
        }

        public int successfulEvaluationCount(String aggregateIdentifier) {
            return evaluationWasSuccessful(aggregateIdentifier) ? evaluationSuccesses.get(aggregateIdentifier) : 0;
        }

        public boolean initialHandlingWasUnsuccessful(String aggregateIdentifier) {
            return firstTryFailures.containsKey(aggregateIdentifier);
        }

        public int unsuccessfulInitialHandlingCount(String aggregateIdentifier) {
            return initialHandlingWasUnsuccessful(aggregateIdentifier) ? firstTryFailures.get(aggregateIdentifier) : 0;
        }

        public boolean evaluationWasUnsuccessful(String aggregateIdentifier) {
            return evaluationFailures.containsKey(aggregateIdentifier);
        }

        public int unsuccessfulEvaluationCount(String aggregateIdentifier) {
            return evaluationWasUnsuccessful(aggregateIdentifier) ? evaluationFailures.get(aggregateIdentifier) : 0;
        }

        public int overallSuccessfulHandlingCount(String aggregateIdentifier) {
            return firstTrySuccesses.get(aggregateIdentifier) + evaluationSuccesses.get(aggregateIdentifier);
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
