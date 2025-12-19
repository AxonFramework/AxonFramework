/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonException;
import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.ThrowableCause;
import org.axonframework.messaging.eventhandling.AsyncInMemoryStreamableEventSource;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

import static org.awaitility.Awaitility.await;
import static org.axonframework.common.util.AssertUtils.assertWithin;
import static org.axonframework.messaging.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test validating the combination of a {@link StreamingEventProcessor} containing a
 * {@link DeadLetteringEventHandlingComponent}. This test validates that:
 *
 * <ul>
 *     <li>Handled {@link EventMessage EventMessages} are enqueued in a {@link SequencedDeadLetterQueue}
 *     if event handling fails.</li>
 *     <li>Handled {@code EventMessage EventMessages} are enqueued in a {@code DeadLetterQueue}
 *     if a previous event in that sequence was enqueued.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are successfully evaluated and evicted
 *     from a {@code DeadLetterQueue} on {@link DeadLetteringEventHandlingComponent#process}.</li>
 *     <li>Enqueued {@code EventMessage EventMessages} are unsuccessfully evaluated
 *     and requeued in the {@code DeadLetterQueue} on {@link DeadLetteringEventHandlingComponent#process}.</li>
 *     <li>Concurrently publish events that succeed and/or fail and validate the evaluation
 *     by the {@code DeadLetterQueue}.</li>
 *     <li>Concurrently publish events in bulk that succeed and/or fail and validate the evaluation
 *     by the {@code DeadLetterQueue}.</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
abstract class DeadLetteringEventIntegrationTest {

    private static final String PROCESSING_GROUP = "problematicProcessingGroup";
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

    private ProblematicEventHandler eventHandler;
    private SequencedDeadLetterQueue<EventMessage> deadLetterQueue;
    private DeadLetteringEventHandlingComponent deadLetteringComponent;
    private AsyncInMemoryStreamableEventSource eventSource;
    private StreamingEventProcessor streamingProcessor;
    private final AtomicInteger maxRetries = new AtomicInteger(DEFAULT_RETRIES);

    private ScheduledExecutorService executor;
    private ScheduledExecutorService coordinatorExecutor;
    private ScheduledExecutorService workerExecutor;
    private final AtomicBoolean returnReferenceErrorFromPolicy = new AtomicBoolean(false);

    /**
     * Creates the {@link SequencedDeadLetterQueue} to be used by this integration test. Subclasses should implement
     * this method to provide specific queue implementations (e.g., in-memory, JPA, JDBC).
     *
     * @return A new instance of {@link SequencedDeadLetterQueue} for testing.
     */
    protected abstract SequencedDeadLetterQueue<EventMessage> createDeadLetterQueue();

    @BeforeEach
    void setUp() {
        eventHandler = new ProblematicEventHandler();
        deadLetterQueue = createDeadLetterQueue();

        // A policy that ensures a letter is only retried once by adding diagnostics.
        EnqueuePolicy<EventMessage> enqueuePolicy = (letter, cause) -> {
            int retries = Integer.parseInt(letter.diagnostics().getOrDefault("retries", "0"));
            if (retries < maxRetries.get()) {
                Throwable decisionThrowable = cause;
                if (returnReferenceErrorFromPolicy.get()) {
                    decisionThrowable = new ReferenceException(UUID.randomUUID());
                }
                return Decisions.enqueue(
                        ThrowableCause.truncated(decisionThrowable),
                        l -> Metadata.with(
                                "retries",
                                Integer.toString(Integer.parseInt(l.diagnostics().getOrDefault("retries", "0")) + 1)
                        )
                );
            } else {
                return Decisions.evict();
            }
        };

        // Create a SimpleEventHandlingComponent with custom sequencing policy
        SequencingPolicy sequencingPolicy = (event, context) ->
                Optional.of(((DeadLetterableEvent) event.payload()).getAggregateIdentifier());

        SimpleEventHandlingComponent simpleComponent = new SimpleEventHandlingComponent(sequencingPolicy);
        simpleComponent.subscribe(
                new QualifiedName(DeadLetterableEvent.class),
                eventHandler
        );

        deadLetteringComponent = DeadLetteringEventHandlingComponent.builder()
                                                                     .delegate(simpleComponent)
                                                                     .queue(deadLetterQueue)
                                                                     .enqueuePolicy(enqueuePolicy)
                                                                     .build();

        eventSource = new AsyncInMemoryStreamableEventSource();
        coordinatorExecutor = Executors.newSingleThreadScheduledExecutor();
        workerExecutor = Executors.newSingleThreadScheduledExecutor();

        var configuration = new PooledStreamingEventProcessorConfiguration()
                .eventSource(eventSource)
                .unitOfWorkFactory(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE))
                .tokenStore(new InMemoryTokenStore())
                .coordinatorExecutor(coordinatorExecutor)
                .workerExecutor(workerExecutor)
                .initialSegmentCount(1)
                .claimExtensionThreshold(1000);

        streamingProcessor = new PooledStreamingEventProcessor(
                PROCESSING_GROUP,
                List.of(deadLetteringComponent),
                configuration
        );
        executor = Executors.newScheduledThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        boolean executorTerminated = false;
        CompletableFuture<Void> processorShutdown = streamingProcessor.shutdown();
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
        coordinatorExecutor.shutdownNow();
        workerExecutor.shutdownNow();
        maxRetries.set(DEFAULT_RETRIES);
        returnReferenceErrorFromPolicy.set(false);
    }

    /**
     * Start this test's {@link StreamingEventProcessor}. This will start event handling.
     */
    private void startProcessingEvent() {
        FutureUtils.joinAndUnwrap(streamingProcessor.start());
    }

    /**
     * Process any sequence of {@link DeadLetter dead letters}. Uses the
     * {@link DeadLetteringEventHandlingComponent#processAny(ProcessingContext)} operation for this.
     */
    private void processAnyDeadLetter() {
        deadLetteringComponent.processAny(new StubProcessingContext()).join();
    }

    /**
     * Process any sequence of {@link DeadLetter dead letters} at set intervals of 5ms.
     */
    private void processAnyDeadLettersPeriodically() {
        executor.scheduleWithFixedDelay(this::processAnyDeadLetter, 5, 5, TimeUnit.MILLISECONDS);
    }

    @Nested
    class WhenHandlingFailingEvents {

        @Test
        void failedEventHandlingEnqueuesTheEvent() {
            // given
            EventMessage failedEvent = asEventMessage(new DeadLetterableEvent("failure", FAIL));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent("success", SUCCEED)));
            eventSource.publishMessage(failedEvent);

            // when
            startProcessingEvent();

            // then
            assertWithin(1, TimeUnit.SECONDS, () -> {
                assertEquals(1, streamingProcessor.processingStatus().size());
                var status = streamingProcessor.processingStatus().get(0);
                assertNotNull(status);
                assertTrue(status.getCurrentPosition().orElse(-1) >= 2);
            });

            assertTrue(eventHandler.initialHandlingWasSuccessful("success"));
            assertTrue(eventHandler.initialHandlingWasUnsuccessful("failure"));

            assertTrue(deadLetterQueue.contains("failure").join());
            assertFalse(deadLetterQueue.contains("success").join());

            Iterator<DeadLetter<? extends EventMessage>> sequence =
                    deadLetterQueue.deadLetterSequence("failure").join().iterator();
            assertTrue(sequence.hasNext());
            assertEquals(failedEvent.payload(), sequence.next().message().payload());
            assertFalse(sequence.hasNext());
        }

        @Test
        void eventsInTheSameSequenceAreAllEnqueuedIfOneOfThemFails() {
            // given
            int expectedSuccessfulHandlingCount = 3;
            String aggregateId = UUID.randomUUID().toString();
            // Three events in sequence "aggregateId" succeed
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            // One event in sequence "aggregateId" fails, causing the rest to fail
            DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL);
            eventSource.publishMessage(asEventMessage(firstDeadLetter));
            DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED);
            eventSource.publishMessage(asEventMessage(secondDeadLetter));
            DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED);
            eventSource.publishMessage(asEventMessage(thirdDeadLetter));

            // when
            startProcessingEvent();

            // then
            assertWithin(1, TimeUnit.SECONDS, () -> {
                assertEquals(1, streamingProcessor.processingStatus().size());
                var status = streamingProcessor.processingStatus().get(0);
                assertNotNull(status);
                assertTrue(status.getCurrentPosition().orElse(-1) >= 6);
            });

            assertTrue(eventHandler.initialHandlingWasSuccessful(aggregateId));
            assertEquals(expectedSuccessfulHandlingCount,
                         eventHandler.successfulInitialHandlingCount(aggregateId));
            assertTrue(eventHandler.initialHandlingWasUnsuccessful(aggregateId));
            assertEquals(1, eventHandler.unsuccessfulInitialHandlingCount(aggregateId));

            assertTrue(deadLetterQueue.contains(aggregateId).join());
            assertWithin(2, TimeUnit.SECONDS, () -> {
                Iterator<DeadLetter<? extends EventMessage>> sequence =
                        deadLetterQueue.deadLetterSequence(aggregateId).join().iterator();
                assertTrue(sequence.hasNext());
                assertEquals(firstDeadLetter, sequence.next().message().payload());
                assertTrue(sequence.hasNext());
                assertEquals(secondDeadLetter, sequence.next().message().payload());
                assertTrue(sequence.hasNext());
                assertEquals(thirdDeadLetter, sequence.next().message().payload());
                assertFalse(sequence.hasNext());
            });
        }
    }

    @Nested
    class WhenProcessingDeadLetters {

        @Test
        void successfulRetryingLettersEvictsTheLettersFromTheQueue() {
            // given
            int expectedSuccessfulInitialHandlingCount = 3;
            int expectedUnsuccessfulInitialHandlingCount = 1;
            int expectedSuccessfulEvaluationCount = 3;
            int expectedUnsuccessfulEvaluationCount = 0;

            String aggregateId = UUID.randomUUID().toString();

            // Three events in sequence "aggregateId" succeed
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            // One event in sequence "aggregateId" fails, causing the rest to fail, but succeed on retry
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));

            startProcessingEvent();

            assertWithin(1, TimeUnit.SECONDS, () -> {
                assertEquals(1, streamingProcessor.processingStatus().size());
                var status = streamingProcessor.processingStatus().get(0);
                assertNotNull(status);
                assertTrue(status.getCurrentPosition().orElse(-1) >= 6);
            });

            assertTrue(eventHandler.initialHandlingWasSuccessful(aggregateId));
            assertEquals(expectedSuccessfulInitialHandlingCount,
                         eventHandler.successfulInitialHandlingCount(aggregateId));
            assertTrue(eventHandler.initialHandlingWasUnsuccessful(aggregateId));
            assertEquals(expectedUnsuccessfulInitialHandlingCount,
                         eventHandler.unsuccessfulInitialHandlingCount(aggregateId));

            assertTrue(deadLetterQueue.contains(aggregateId).join());

            // when
            deadLetteringComponent.process(deadLetter -> true, new StubProcessingContext()).join();

            // then
            assertWithin(1, TimeUnit.SECONDS,
                         () -> assertTrue(eventHandler.evaluationWasSuccessful(aggregateId)));
            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                    expectedSuccessfulEvaluationCount,
                    eventHandler.successfulEvaluationCount(aggregateId)
            ));
            assertWithin(1, TimeUnit.SECONDS,
                         () -> assertFalse(eventHandler.evaluationWasUnsuccessful(aggregateId)));
            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                    expectedUnsuccessfulEvaluationCount,
                    eventHandler.unsuccessfulEvaluationCount(aggregateId)
            ));

            assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(deadLetterQueue.contains(aggregateId).join()));
        }

        @Test
        void unsuccessfulProcessingLettersRequeuesTheLettersInTheQueue() {
            // given
            int expectedSuccessfulInitialHandlingCount = 3;
            int expectedUnsuccessfulInitialHandlingCount = 1;
            int expectedSuccessfulEvaluationCount = 2;
            int expectedUnsuccessfulEvaluationCount = 1;

            String aggregateId = UUID.randomUUID().toString();

            // Three events in sequence "aggregateId" succeed
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            // One event in sequence "aggregateId" fails, causing the rest to fail, but...
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY)));
            // ...the last retry fails.
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY)));

            startProcessingEvent();

            assertWithin(2, TimeUnit.SECONDS, () -> {
                assertEquals(1, streamingProcessor.processingStatus().size());
                var status = streamingProcessor.processingStatus().get(0);
                assertNotNull(status);
                assertTrue(status.getCurrentPosition().orElse(-1) >= 6);
            });

            assertTrue(eventHandler.initialHandlingWasSuccessful(aggregateId));
            assertEquals(expectedSuccessfulInitialHandlingCount,
                         eventHandler.successfulInitialHandlingCount(aggregateId));
            assertTrue(eventHandler.initialHandlingWasUnsuccessful(aggregateId));
            assertEquals(expectedUnsuccessfulInitialHandlingCount,
                         eventHandler.unsuccessfulInitialHandlingCount(aggregateId));

            assertTrue(deadLetterQueue.contains(aggregateId).join());

            // when
            deadLetteringComponent.process(deadLetter -> true, new StubProcessingContext()).join();

            // then
            assertWithin(1, TimeUnit.SECONDS,
                         () -> assertTrue(eventHandler.evaluationWasSuccessful(aggregateId)));
            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                    expectedSuccessfulEvaluationCount,
                    eventHandler.successfulEvaluationCount(aggregateId)
            ));
            assertWithin(1, TimeUnit.SECONDS,
                         () -> assertTrue(eventHandler.evaluationWasUnsuccessful(aggregateId)));
            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                    expectedUnsuccessfulEvaluationCount,
                    eventHandler.unsuccessfulEvaluationCount(aggregateId)
            ));

            // As evaluation fails, the sequenceId should still exist
            assertTrue(deadLetterQueue.contains(aggregateId).join());
        }
    }

    @Nested
    class WhenProcessingConcurrently {

        @Test
        void publishEventsAndProcessDeadLettersConcurrentlyShouldWorkFine() {
            // given
            int expectedSuccessfulInitialHandlingCount = 3;
            int expectedUnsuccessfulInitialHandlingCount = 1;
            int expectedSuccessfulEvaluationCount = 2;
            int expectedUnsuccessfulEvaluationCount = 1;

            String aggregateId = UUID.randomUUID().toString();

            // Starting both is sufficient since both Processor and DeadLettering have their own thread pool.
            startProcessingEvent();
            processAnyDeadLettersPeriodically();

            // when
            // Three events in sequence "aggregateId" succeed
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, SUCCEED)));
            // One event in sequence "aggregateId" fails, causing the rest to fail, but...
            DeadLetterableEvent firstDeadLetter = new DeadLetterableEvent(aggregateId, FAIL, SUCCEED_RETRY);
            eventSource.publishMessage(asEventMessage(firstDeadLetter));
            DeadLetterableEvent secondDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, SUCCEED_RETRY);
            eventSource.publishMessage(asEventMessage(secondDeadLetter));
            // ...the last retry fails.
            DeadLetterableEvent thirdDeadLetter = new DeadLetterableEvent(aggregateId, SUCCEED, FAIL_RETRY);
            eventSource.publishMessage(asEventMessage(thirdDeadLetter));

            // then
            assertWithin(2, TimeUnit.SECONDS, () -> {
                assertEquals(1, streamingProcessor.processingStatus().size());
                var status = streamingProcessor.processingStatus().get(0);
                assertNotNull(status);
                assertTrue(status.getCurrentPosition().orElse(-1) >= 6);
            });

            assertTrue(eventHandler.initialHandlingWasSuccessful(aggregateId));
            assertEquals(expectedSuccessfulInitialHandlingCount,
                         eventHandler.successfulInitialHandlingCount(aggregateId));
            assertTrue(eventHandler.initialHandlingWasUnsuccessful(aggregateId));
            assertEquals(expectedUnsuccessfulInitialHandlingCount,
                         eventHandler.unsuccessfulInitialHandlingCount(aggregateId));

            assertTrue(deadLetterQueue.contains(aggregateId).join());

            assertWithin(2, TimeUnit.SECONDS,
                         () -> assertTrue(eventHandler.evaluationWasSuccessful(aggregateId)));
            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                    expectedSuccessfulEvaluationCount,
                    eventHandler.successfulEvaluationCount(aggregateId)
            ));
            assertWithin(500, TimeUnit.MILLISECONDS,
                         () -> assertTrue(eventHandler.evaluationWasUnsuccessful(aggregateId)));
            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
                    expectedUnsuccessfulEvaluationCount,
                    eventHandler.unsuccessfulEvaluationCount(aggregateId)
            ));

            // As evaluation fails, the sequenceId should still exist
            assertTrue(deadLetterQueue.contains(aggregateId).join());
        }

        @Test
        @Timeout(20)
        void publishEventsAndProcessDeadLettersConcurrentlyInBulkShouldWorkFine() throws InterruptedException {
            // given
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

            // when
            startProcessingEvent();
            processAnyDeadLettersPeriodically();

            publishingThread.start();

            // then
            assertWithin(15, TimeUnit.SECONDS, () -> {
                assertEquals(1, streamingProcessor.processingStatus().size());
                var status = streamingProcessor.processingStatus().get(0);
                assertNotNull(status);
                assertEquals(totalNumberOfEvents, status.getCurrentPosition().orElse(-1));
            });

            for (String aggregateId : aggregateIds) {
                if (validatedAggregateIds.contains(aggregateId)) {
                    continue;
                }

                // Validate first try event handling...
                // Successful...
                assertWithin(500, TimeUnit.MILLISECONDS,
                             () -> assertTrue(eventHandler.initialHandlingWasSuccessful(aggregateId)));
                assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                        immediateSuccessesPerAggregate,
                        eventHandler.successfulInitialHandlingCount(aggregateId)
                ));
                // Unsuccessful...
                assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(
                        eventHandler.initialHandlingWasUnsuccessful(aggregateId)
                ));
                assertEquals(1, eventHandler.unsuccessfulInitialHandlingCount(aggregateId));

                // Validate evaluation event handling...
                // Successful...
                assertWithin(15, TimeUnit.SECONDS, () -> assertTrue(
                        eventHandler.evaluationWasSuccessful(aggregateId)
                ));
                assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(
                        expectedSuccessfulEvaluationCount,
                        eventHandler.successfulEvaluationCount(aggregateId)
                ));
                // Unsuccessful...
                assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(
                        eventHandler.evaluationWasUnsuccessful(aggregateId)
                ));
                assertTrue(eventHandler.unsuccessfulEvaluationCount(aggregateId)
                                   >= persistentFailingPerAggregate);

                // Overall...
                assertEquals(expectedOverallSuccessfulHandlingCount,
                             eventHandler.overallSuccessfulHandlingCount(aggregateId));

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
    }

    @Nested
    class WhenStoringCause {

        @Test
        void causeFromDecisionShouldBeStored() {
            // given
            returnReferenceErrorFromPolicy.set(true);
            String aggregateId = UUID.randomUUID().toString();
            eventSource.publishMessage(asEventMessage(new DeadLetterableEvent(aggregateId, FAIL)));

            // when
            startProcessingEvent();

            // then
            await().pollDelay(Duration.ofMillis(25))
                   .atMost(Duration.ofSeconds(1))
                   .until(() -> deadLetterQueue.amountOfSequences().join() == 1);

            DeadLetter<?> deadLetter = deadLetterQueue.deadLetters().join().iterator().next().iterator().next();
            assertTrue(deadLetter.cause().isPresent());
            String causeType = deadLetter.cause().get().type();
            assertEquals(ReferenceException.class.getName(), causeType);
        }

        @Test
        void largeThrowableMessageIsTruncatedUponCauseCreation() {
            // given
            String aggregateId = UUID.randomUUID().toString();
            String truncatedText = "truncated-text";
            String testCauseMessage = BLOB_OF_TEXT + truncatedText;

            eventSource.publishMessage(
                    asEventMessage(new DeadLetterableEvent(aggregateId, FAIL, FAIL, testCauseMessage))
            );

            // when
            startProcessingEvent();

            // then
            await().pollDelay(Duration.ofMillis(25))
                   .atMost(Duration.ofSeconds(1))
                   .until(() -> deadLetterQueue.amountOfSequences().join() == 1);

            DeadLetter<?> deadLetter = deadLetterQueue.deadLetters().join().iterator().next().iterator().next();
            Optional<Cause> optionalCause = deadLetter.cause();
            assertTrue(optionalCause.isPresent());
            String resultMessage = optionalCause.get().message();
            assertNotEquals(testCauseMessage, resultMessage);
            assertFalse(resultMessage.contains(truncatedText));
            assertTrue(resultMessage.contains(BLOB_OF_TEXT.substring(0, 10)));
        }
    }

    /**
     * An {@link EventHandler} that can succeed or fail based on the event payload.
     */
    private static class ProblematicEventHandler implements EventHandler {

        private final Set<String> handledEvent = new ConcurrentSkipListSet<>();
        private final Map<String, Integer> firstTrySuccesses = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> evaluationSuccesses = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> firstTryFailures = new ConcurrentSkipListMap<>();
        private final Map<String, Integer> evaluationFailures = new ConcurrentSkipListMap<>();

        @Nonnull
        @Override
        public MessageStream.Empty<Message> handle(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            DeadLetterableEvent payload = (DeadLetterableEvent) event.payload();
            String sequenceId = payload.getAggregateIdentifier();
            String eventIdentifier = event.identifier();

            if (!handledEvent.contains(eventIdentifier)) {
                // This is the first time we get this event.
                handledEvent.add(eventIdentifier);
                if (!initialHandlingWasUnsuccessful(sequenceId)) {
                    // This is the first time we get this sequence.
                    return processInitialHandlingOf(payload, sequenceId);
                } else {
                    // This is the second, third, ... time we get this sequence.
                    return processEvaluationOf(payload, sequenceId);
                }
            } else {
                // This is the second, third, ... time we get this event.
                return processEvaluationOf(payload, sequenceId);
            }
        }

        private MessageStream.Empty<Message> processInitialHandlingOf(DeadLetterableEvent event, String sequenceId) {
            if (event.isShouldSucceed()) {
                firstTrySuccesses.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                return MessageStream.empty();
            } else {
                firstTryFailures.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                return MessageStream.<Message>failed(new RuntimeException(
                        "Initial handling failed. Let's dead letter event [" + sequenceId + "].\n"
                                + event.getCauseMessage()
                )).ignoreEntries();
            }
        }

        private MessageStream.Empty<Message> processEvaluationOf(DeadLetterableEvent event, String sequenceId) {
            if (event.isShouldSucceedOnEvaluation()) {
                evaluationSuccesses.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                return MessageStream.empty();
            } else {
                evaluationFailures.compute(sequenceId, (id, count) -> count == null ? 1 : ++count);
                return MessageStream.<Message>failed(new RuntimeException(
                        "Evaluation failed. Let's dead letter event [" + sequenceId + "].\n" + event.getCauseMessage()
                )).ignoreEntries();
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
    }

    /**
     * A simple event that can be configured to succeed or fail during handling.
     */
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

    private static class ReferenceException extends AxonException {

        ReferenceException(UUID reference) {
            super(reference.toString());
        }
    }
}
