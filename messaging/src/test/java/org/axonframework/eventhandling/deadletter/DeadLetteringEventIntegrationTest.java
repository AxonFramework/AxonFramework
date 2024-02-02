/*
 * Copyright (c) 2010-2023. Axon Framework
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.common.AxonException;
import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MessageIdentifier;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.ThrowableCause;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.utils.InMemoryStreamableEventSource;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;
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
 *     from a {@code DeadLetterQueue} on {@link DeadLetteringEventHandlerInvoker#process(Predicate)}}.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are unsuccessfully evaluated
 *     and requeued in the {@code DeadLetterQueue} on {@link DeadLetteringEventHandlerInvoker#process(Predicate)}.</li>
 *     <li>Concurrently publish events that succeed and/or fail and validate the evaluation
 *     by the {@code DeadLetterQueue}.</li>
 *     <li>Concurrently publish events in bulk that succeed and/or fail and validate the evaluation
 *     by the {@code DeadLetterQueue}.</li>
 * </ul>
 *
 * @author Steven van Beelen
 */
public abstract class DeadLetteringEventIntegrationTest {

    protected static final String PROCESSING_GROUP = "problematicProcessingGroup";
    private static final boolean SUCCEED = true;
    private static final boolean SUCCEED_RETRY = true;
    private static final boolean FAIL = false;
    private static final boolean FAIL_RETRY = false;
    private static final int DEFAULT_RETRIES = 1;
    private static final String BLOB_OF_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
            + "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
            + "Gravida quis blandit turpis cursus in. "
            + "Nulla facilisi etiam dignissim diam quis enim lobortis scelerisque fermentum. "
            + "Egestas maecenas pharetra convallis posuere morbi leo urna. "
            + "Dictumst quisque sagittis purus sit amet volutpat consequat. "
            + "At volutpat diam ut venenatis tellus in metus vulputate eu. "
            + "Imperdiet dui accumsan sit amet nulla facilisi. Eget est lorem ipsum dolor sit amet. "
            + "Vestibulum morbi blandit cursus risus at ultrices mi tempus imperdiet. "
            + "Sed tempus urna et pharetra pharetra massa massa. Dolor magna eget est lorem. "
            + "Purus semper eget duis at tellus. "
            + "Tincidunt augue interdum velit euismod in pellentesque massa placerat duis."
            + "\n\n"
            + "Quis ipsum suspendisse ultrices gravida dictum fusce ut. "
            + "Nascetur ridiculus mus mauris vitae ultricies leo integer malesuada. "
            + "Sit amet purus gravida quis blandit turpis cursus in. "
            + "Gravida rutrum quisque non tellus. "
            + "Eros donec ac odio tempor orci dapibus. "
            + "Dictum varius duis at consectetur lorem donec massa sapien."
            + "Tincidunt arcu non sodales neque sodales ut etiam sit amet. "
            + "Sagittis aliquam malesuada bibendum arcu vitae. "
            + "Vel turpis nunc eget lorem dolor sed viverra. "
            + "In egestas erat imperdiet sed euismod nisi. "
            + "Lorem ipsum dolor sit amet consectetur.";

    private ProblematicEventHandlingComponent eventHandlingComponent;
    private SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue;
    private DeadLetteringEventHandlerInvoker deadLetteringInvoker;
    private InMemoryStreamableEventSource eventSource;
    private StreamingEventProcessor streamingProcessor;
    protected TransactionManager transactionManager;
    private final AtomicInteger maxRetries = new AtomicInteger(DEFAULT_RETRIES);

    private ScheduledExecutorService executor;
    private final AtomicBoolean returnReferenceErrorFromPolicy = new AtomicBoolean(false);

    /**
     * Constructs the {@link SequencedDeadLetterQueue} implementation used during the integration test.
     *
     * @return A {@link SequencedDeadLetterQueue} implementation used during the integration test.
     */
    protected abstract SequencedDeadLetterQueue<EventMessage<?>> buildDeadLetterQueue();

    protected TransactionManager getTransactionManager() {
        return new NoOpTransactionManager();
    }

    protected boolean identifierCacheEnabled() {
        return false;
    }

    @BeforeEach
    void setUp() {
        transactionManager = getTransactionManager();
        eventHandlingComponent = new ProblematicEventHandlingComponent();
        deadLetterQueue = buildDeadLetterQueue();

        // A policy that ensure a letter is only retried once by adding diagnostics.
        EnqueuePolicy<EventMessage<?>> enqueuePolicy = (letter, cause) -> {
            int retries = (int) letter.diagnostics().getOrDefault("retries", 0);
            if (retries < maxRetries.get()) {
                Throwable decisionThrowable = cause;
                if (returnReferenceErrorFromPolicy.get()) {
                    decisionThrowable = new ReferenceException(UUID.randomUUID());
                }
                return Decisions.enqueue(
                        ThrowableCause.truncated(decisionThrowable),
                        l -> MetaData.with("retries", (int) l.diagnostics().getOrDefault("retries", 0) + 1)
                );
            } else {
                return Decisions.evict();
            }
        };

        DeadLetteringEventHandlerInvoker.Builder invokerBuilder = DeadLetteringEventHandlerInvoker
                .builder()
                .eventHandlers(eventHandlingComponent)
                .sequencingPolicy(event -> ((DeadLetterableEvent) event.getPayload()).getAggregateIdentifier())
                .enqueuePolicy(enqueuePolicy)
                .queue(deadLetterQueue)
                .transactionManager(transactionManager);

        if (identifierCacheEnabled()) {
            invokerBuilder.enableSequenceIdentifierCache();
        }
        deadLetteringInvoker = invokerBuilder.build();

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
        maxRetries.set(DEFAULT_RETRIES);
        returnReferenceErrorFromPolicy.set(false);
    }

    /**
     * Start this test's {@link StreamingEventProcessor}. This will start event handling.
     */
    protected void startProcessingEvent() {
        streamingProcessor.start();
    }

    /**
     * Process any sequence of {@link DeadLetter dead letters}. Uses the
     * {@link DeadLetteringEventHandlerInvoker#processAny()} operation for this.
     */
    protected void processAnyDeadLetter() {
        deadLetteringInvoker.processAny();
    }

    /**
     * Process any sequence of {@link DeadLetter dead letters} at set intervals of 5ms. Uses the
     * {@link DeadLetteringEventHandlerInvoker#processAny()} operation for this.
     */
    protected void processAnyDeadLettersPeriodically() {
        executor.scheduleWithFixedDelay(this::processAnyDeadLetter, 5, 5, TimeUnit.MILLISECONDS);
    }

    @Test
    void failedEventHandlingEnqueuesTheEvent() {
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

        assertTrue(deadLetterQueue.contains("failure"));
        assertFalse(deadLetterQueue.contains("success"));

        Iterator<DeadLetter<? extends EventMessage<?>>> sequence = deadLetterQueue.deadLetterSequence("failure")
                                                                                  .iterator();
        assertTrue(sequence.hasNext());
        assertEquals(failedEvent.getPayload(), sequence.next().message().getPayload());
        assertFalse(sequence.hasNext());
    }

    @Test
    void eventsInTheSameSequenceAreAllEnqueuedIfOneOfThemFails() {
        int expectedSuccessfulHandlingCount = 3;
        String aggregateId = UUID.randomUUID().toString();
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

        assertTrue(deadLetterQueue.contains(aggregateId));
        assertWithin(2, TimeUnit.SECONDS, () -> {
            Iterator<DeadLetter<? extends EventMessage<?>>> sequence = deadLetterQueue.deadLetterSequence(aggregateId)
                                                                                      .iterator();
            assertTrue(sequence.hasNext());
            assertEquals(firstDeadLetter, sequence.next().message().getPayload());
            assertTrue(sequence.hasNext());
            assertEquals(secondDeadLetter, sequence.next().message().getPayload());
            assertTrue(sequence.hasNext());
            assertEquals(thirdDeadLetter, sequence.next().message().getPayload());
            assertFalse(sequence.hasNext());
        });
    }

    @Test
    void successfulRetryingLettersEvictsTheLettersFromTheQueue() {
        int expectedSuccessfulInitialHandlingCount = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulInitialHandlingCount = 1;
        int expectedSuccessfulEvaluationCount = 3;
        int expectedUnsuccessfulEvaluationCount = 0;

        String aggregateId = UUID.randomUUID().toString();

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

        assertTrue(deadLetterQueue.contains(aggregateId));

        deadLetteringInvoker.process(deadLetter -> true);

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

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(deadLetterQueue.contains(aggregateId)));
    }

    @Test
    void unsuccessfulProcessingLettersRequeuesTheLettersInTheQueue() {
        int expectedSuccessfulInitialHandlingCount = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulInitialHandlingCount = 1;
        int expectedSuccessfulEvaluationCount = 2;
        int expectedUnsuccessfulEvaluationCount = 1;

        String aggregateId = UUID.randomUUID().toString();

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

        assertTrue(deadLetterQueue.contains(aggregateId));

        deadLetteringInvoker.process(deadLetter -> true);

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
        assertTrue(deadLetterQueue.contains(aggregateId));
    }

    @Test
    void publishEventsAndProcessDeadLettersConcurrentlyShouldWorkFine() {
        int expectedSuccessfulInitialHandlingCount = 3;
        // The first failure ensure subsequent events don't reach the handler.
        // So there can only be a single failure per sequence on the first try.
        int expectedUnsuccessfulInitialHandlingCount = 1;
        int expectedSuccessfulEvaluationCount = 2;
        int expectedUnsuccessfulEvaluationCount = 1;

        String aggregateId = UUID.randomUUID().toString();

        // Starting both is sufficient since both Processor and DeadLettering Invoker have their own thread pool.
        startProcessingEvent();
        processAnyDeadLettersPeriodically();

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

        assertTrue(deadLetterQueue.contains(aggregateId));

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
        assertTrue(deadLetterQueue.contains(aggregateId));
    }

    @Test
    @Timeout(20)
    void publishEventsAndProcessDeadLettersConcurrentlyInBulkShouldWorkFine() throws InterruptedException {
        int immediateSuccessesPerAggregate = 5;
        int failFirstAndThenSucceedPerAggregate = 4;
        int persistentFailingPerAggregate = 1;
        int expectedSuccessfulEvaluationCount = failFirstAndThenSucceedPerAggregate - persistentFailingPerAggregate;
        int expectedOverallSuccessfulHandlingCount = immediateSuccessesPerAggregate + expectedSuccessfulEvaluationCount;

        int publishingRuns = 40;
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
        assertWithin(15, TimeUnit.SECONDS, () -> {
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
            assertWithin(15, TimeUnit.SECONDS, () -> assertTrue(
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

    @Test
    void processedDeadLetterIsResolvedAsParameterToEventHandlers() {
        int expectedSuccessfulHandlingCount = 3;
        String aggregateId = UUID.randomUUID().toString();
        // Three events in sequence "aggregateId" succeed
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
        // On event in sequence "aggregateId" fails, causing the rest to fail, but succeed on a retry
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));

        startProcessingEvent();

        await().pollDelay(Duration.ofMillis(25))
               .atMost(Duration.ofSeconds(1))
               .until(() -> streamingProcessor.processingStatus().size() == 1);
        //noinspection OptionalGetWithoutIsPresent
        await().pollDelay(Duration.ofMillis(25))
               .atMost(Duration.ofSeconds(1))
               .until(() -> streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 6);

        assertTrue(eventHandlingComponent.initialHandlingWasSuccessful(aggregateId));
        assertEquals(expectedSuccessfulHandlingCount,
                     eventHandlingComponent.successfulInitialHandlingCount(aggregateId));
        assertTrue(eventHandlingComponent.initialHandlingWasUnsuccessful(aggregateId));
        assertEquals(1, eventHandlingComponent.unsuccessfulInitialHandlingCount(aggregateId));

        assertTrue(deadLetterQueue.contains(aggregateId));

        deadLetteringInvoker.process(deadLetter -> true);

        assertEquals(3, eventHandlingComponent.resolvedDeadLetterParameterCount(aggregateId));
    }

    @Test
    void deadLetterEventProcessingTaskIsUsingInterceptor() {
        //needed to increase otherwise it would be evicted anyway
        maxRetries.set(3);
        AtomicBoolean invoked = new AtomicBoolean(false);
        deadLetteringInvoker.registerHandlerInterceptor(errorCatchingInterceptor(invoked));

        String aggregateId = UUID.randomUUID().toString();
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL)));
        startProcessingEvent();
        await().pollDelay(Duration.ofMillis(25))
               .atMost(Duration.ofSeconds(10))
               .until(() -> deadLetterQueue.size() == 1);

        // component needs to be reset, so process will cause an exception again
        eventHandlingComponent.handledEvent.clear();
        eventHandlingComponent.firstTryFailures.clear();
        deadLetteringInvoker.process(deadLetter -> true);
        assertTrue(invoked.get());
        assertEquals(0, deadLetterQueue.size());
    }

    @Test
    void causeFromDecisionShouldBeStored() {
        returnReferenceErrorFromPolicy.set(true);
        String aggregateId = UUID.randomUUID().toString();
        eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL)));
        startProcessingEvent();
        await().pollDelay(Duration.ofMillis(25))
               .atMost(Duration.ofSeconds(1))
               .until(() -> deadLetterQueue.amountOfSequences() == 1);
        DeadLetter<?> deadLetter = deadLetterQueue.deadLetters().iterator().next().iterator().next();
        assertTrue(deadLetter.cause().isPresent());
        String causeType = deadLetter.cause().get().type();
        assertEquals(ReferenceException.class.getName(), causeType);
    }

    @Test
    void largeThrowableMessageIsTruncatedUponCauseCreation() {
        String aggregateId = UUID.randomUUID().toString();
        String truncatedText = "truncated-text";
        String testCauseMessage = BLOB_OF_TEXT + truncatedText;

        eventSource.publishMessage(
                asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, FAIL, testCauseMessage))
        );

        startProcessingEvent();
        await().pollDelay(Duration.ofMillis(25))
               .atMost(Duration.ofSeconds(1))
               .until(() -> deadLetterQueue.amountOfSequences() == 1);

        DeadLetter<?> deadLetter = deadLetterQueue.deadLetters().iterator().next().iterator().next();
        Optional<Cause> optionalCause = deadLetter.cause();
        assertTrue(optionalCause.isPresent());
        String resultMessage = optionalCause.get().message();
        assertNotEquals(testCauseMessage, resultMessage);
        assertFalse(resultMessage.contains(truncatedText));
        assertTrue(resultMessage.contains(BLOB_OF_TEXT.substring(0, 10)));
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
        private final Map<String, Integer> hasResolvedDeadLetterParameter = new ConcurrentSkipListMap<>();

        @EventHandler
        public void on(DeadLetterableEvent event,
                       @MessageIdentifier String eventIdentifier,
                       DeadLetter<EventMessage<DeadLetterableEvent>> deadLetter) {
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

            if (deadLetter != null) {
                hasResolvedDeadLetterParameter.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
            }
        }

        private void processInitialHandlingOf(DeadLetterableEvent event, String sequenceId) {
            if (event.isShouldSucceed()) {
                firstTrySuccesses.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
            } else {
                firstTryFailures.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                throw new RuntimeException(
                        "Initial handling failed. Let's dead letter event [" + sequenceId + "].\n"
                                + event.getCauseMessage()
                );
            }
        }

        private void processEvaluationOf(DeadLetterableEvent event, String sequenceId) {
            if (event.isShouldSucceedOnEvaluation()) {
                evaluationSuccesses.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
            } else {
                evaluationFailures.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                throw new RuntimeException(
                        "Evaluation failed. Let's dead letter event [" + sequenceId + "].\n" + event.getCauseMessage()
                );
            }
        }

        public boolean initialHandlingWasSuccessful(String sequenceId) {
            return firstTrySuccesses.containsKey(sequenceId);
        }

        public int successfulInitialHandlingCount(String sequenceId) {
            return initialHandlingWasSuccessful(sequenceId) ? firstTrySuccesses.get(sequenceId) : 0;
        }

        public boolean evaluationWasSuccessful(String sequenceId) {
            return evaluationSuccesses.containsKey(sequenceId);
        }

        public int successfulEvaluationCount(String sequenceId) {
            return evaluationWasSuccessful(sequenceId) ? evaluationSuccesses.get(sequenceId) : 0;
        }

        public boolean initialHandlingWasUnsuccessful(String sequenceId) {
            return firstTryFailures.containsKey(sequenceId);
        }

        public int unsuccessfulInitialHandlingCount(String sequenceId) {
            return initialHandlingWasUnsuccessful(sequenceId) ? firstTryFailures.get(sequenceId) : 0;
        }

        public boolean evaluationWasUnsuccessful(String sequenceId) {
            return evaluationFailures.containsKey(sequenceId);
        }

        public int unsuccessfulEvaluationCount(String sequenceId) {
            return evaluationWasUnsuccessful(sequenceId) ? evaluationFailures.get(sequenceId) : 0;
        }

        public int overallSuccessfulHandlingCount(String sequenceId) {
            return firstTrySuccesses.get(sequenceId) + evaluationSuccesses.get(sequenceId);
        }

        public int resolvedDeadLetterParameterCount(String sequenceId) {
            return hasResolvedDeadLetterParameter.get(sequenceId);
        }
    }

    private static class DeadLetterableEvent {

        private final String aggregateIdentifier;
        private final boolean shouldSucceed;
        private final boolean shouldSucceedOnEvaluation;
        private final String causeMessage;

        private DeadLetterableEvent(String aggregateIdentifier,
                                    boolean shouldSucceed) {
            this(aggregateIdentifier, shouldSucceed, true);
        }

        private DeadLetterableEvent(String aggregateIdentifier,
                                    boolean shouldSucceed,
                                    boolean shouldSucceedOnEvaluation) {
            this(aggregateIdentifier, shouldSucceed, shouldSucceedOnEvaluation, "");
        }

        @JsonCreator
        public DeadLetterableEvent(@JsonProperty("aggregateIdentifier") String aggregateIdentifier,
                                   @JsonProperty("shouldSucceed") boolean shouldSucceed,
                                   @JsonProperty("shouldSucceedOnEvaluation") boolean shouldSucceedOnEvaluation,
                                   @JsonProperty("causeMessage") String causeMessage) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.shouldSucceed = shouldSucceed;
            this.shouldSucceedOnEvaluation = shouldSucceedOnEvaluation;
            this.causeMessage = causeMessage;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public boolean isShouldSucceed() {
            return shouldSucceed;
        }

        public boolean isShouldSucceedOnEvaluation() {
            return shouldSucceedOnEvaluation;
        }

        public String getCauseMessage() {
            return causeMessage;
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
            return shouldSucceed == that.shouldSucceed && shouldSucceedOnEvaluation == that.shouldSucceedOnEvaluation
                    && Objects.equals(aggregateIdentifier, that.aggregateIdentifier) && Objects.equals(
                    causeMessage,
                    that.causeMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregateIdentifier, shouldSucceed, shouldSucceedOnEvaluation, causeMessage);
        }

        @Override
        public String toString() {
            return "DeadLetterableEvent{" +
                    "aggregateIdentifier='" + aggregateIdentifier + '\'' +
                    ", shouldSucceed=" + shouldSucceed +
                    ", shouldSucceedOnEvaluation=" + shouldSucceedOnEvaluation +
                    ", causeMessage='" + causeMessage + '\'' +
                    '}';
        }
    }

    private MessageHandlerInterceptor<? super EventMessage<?>> errorCatchingInterceptor(AtomicBoolean invoked) {
        return (unitOfWork, chain) -> {
            invoked.set(true);
            try {
                chain.proceedSync();
            } catch (RuntimeException e) {
                return unitOfWork;
            }
            return unitOfWork;
        };
    }

    private static class ReferenceException extends AxonException {

        private static final long serialVersionUID = 1380362964599517107L;

        ReferenceException(UUID reference) {
            super(reference.toString());
        }
    }
}
