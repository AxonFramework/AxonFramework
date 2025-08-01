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

package org.axonframework.eventhandling.pooled;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.WrappedToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.LegacyMessageSupportingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.axonframework.common.FutureUtils.emptyCompletedFuture;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;

/**
 * Defines the process of handling {@link EventMessage}s for a specific {@link Segment}. This entails validating if the
 * event can be handled through a {@link EventFilter} and after that processing a collection of events in the
 * {@link BatchProcessor}.
 * <p>
 * Events are received through the {@link #scheduleEvent(MessageStream.Entry)} operation, delegated by a
 * {@link Coordinator}. Receiving event(s) means this {@link WorkPackage} will be scheduled to process these events
 * through an {@link ExecutorService}. As there are local threads and outside threads invoking methods on the
 * {@code WorkPackage}, several methods have threading notes describing what can invoke them safely.
 * <p>
 * Since the {@code WorkPackage} is in charge of a {@code Segment}, it maintains the claim on the matching
 * {@link TrackingToken}. In absence of new events, it will also {@link TokenStore#extendClaim(String, int)} on the
 * {@code TrackingToken}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see PooledStreamingEventProcessor
 * @see Coordinator
 * @since 4.5
 */
class WorkPackage {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static final int BUFFER_SIZE = 1024;

    private final String name;
    private final TokenStore tokenStore;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final ExecutorService executorService;
    private final EventFilter eventFilter;
    private final BatchProcessor batchProcessor;
    private final Segment segment;
    private final int batchSize;
    private final long claimExtensionThreshold;
    private final Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater;
    private Runnable batchProcessedCallback;
    private final Clock clock;

    private TrackingToken lastDeliveredToken; // For use only by event delivery threads, like Coordinator
    private TrackingToken lastConsumedToken;
    private TrackingToken lastStoredToken;
    private final AtomicLong nextClaimExtension;
    private final AtomicBoolean processingEvents;

    private final Queue<ProcessingEntry> processingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Exception>> abortFlag = new AtomicReference<>();
    private final AtomicReference<Exception> abortException = new AtomicReference<>();

    /**
     * Instantiate a Builder to be able to create a {@link WorkPackage}. This builder <b>does not</b> validate the
     * fields. Hence, any fields provided should be validated by the user of the {@link WorkPackage.Builder}.
     *
     * @return a Builder to be able to create a {@link WorkPackage}
     */
    protected static Builder builder() {
        return new Builder();
    }

    private WorkPackage(Builder builder) {
        this.name = builder.name;
        this.tokenStore = builder.tokenStore;
        this.unitOfWorkFactory = builder.unitOfWorkFactory;
        this.executorService = builder.executorService;
        this.eventFilter = builder.eventFilter;
        this.batchProcessor = builder.batchProcessor;
        this.segment = builder.segment;
        this.lastDeliveredToken = builder.initialToken;
        this.batchSize = builder.batchSize;
        this.claimExtensionThreshold = builder.claimExtensionThreshold;
        this.segmentStatusUpdater = builder.segmentStatusUpdater;
        this.clock = builder.clock;
        this.lastConsumedToken = builder.initialToken;
        this.nextClaimExtension = new AtomicLong(now() + claimExtensionThreshold);
        this.processingEvents = new AtomicBoolean(false);
    }

    private long now() {
        return clock.instant().toEpochMilli();
    }

    /**
     * Schedule a collection of {@link MessageStream.Entry MessageStream.Entries} for processing by this work package.
     * <p>
     * Only use this method if the {@link TrackingToken TrackingTokens} of every event are equal, as those events should
     * be handled within a single transaction. This scenario presents itself whenever an event is upcasted into
     * <em>several instances</em>. When tokens differ between events please use
     * {@link #scheduleEvent(MessageStream.Entry)}.
     * <p>
     * Will disregard the given {@code eventEntries} if their {@code TrackingTokens} are covered by the previously scheduled
     * event.
     * <p>
     * <b>Threading note:</b> This method is and should only to be called by the {@link Coordinator} thread of a {@link
     * PooledStreamingEventProcessor}.
     *
     * @param eventEntries The event entries to schedule for work in this work package.
     * @return {@code True} if this {@link WorkPackage} scheduled one of the events for execution, otherwise
     * {@code false}.
     */
    public boolean scheduleEvents(List<MessageStream.Entry<? extends EventMessage<?>>> eventEntries) {
        if (eventEntries.isEmpty()) {
            // cannot schedule an empty events list
            return false;
        }
        assertEqualTokens(eventEntries);

        if (eventEntries.stream().allMatch(this::shouldNotSchedule)) {
            if (logger.isTraceEnabled()) {
                eventEntries.forEach(eventEntry -> {
                    TrackingToken eventToken = TrackingToken.fromContext(eventEntry).orElse(null);
                    logger.trace(
                            "Ignoring event [{}] with position [{}] for work package [{}]. "
                                    + "The last token [{}] covers event's token [{}].",
                            eventEntry.message().identifier(),
                            eventToken != null ? eventToken.position().orElse(-1) : -1,
                            segment.getSegmentId(),
                            lastDeliveredToken,
                            eventToken
                    );
                });
            }
            return false;
        }

        BatchProcessingEntry batchProcessingEntry = new BatchProcessingEntry();
        boolean canHandleAny = eventEntries.stream()
                                           .map(eventEntry -> {
                                               var context = new LegacyMessageSupportingContext(eventEntry.message());
                                               boolean canHandle = canHandle(eventEntry.message(), context);
                                               batchProcessingEntry.add(new DefaultProcessingEntry(eventEntry,
                                                                                                   canHandle));
                                               return canHandle;
                                           })
                                           .reduce(Boolean::logicalOr)
                                           .orElse(false);

        processingQueue.add(batchProcessingEntry);
        lastDeliveredToken = batchProcessingEntry.trackingToken();
        // the worker must always be scheduled to ensure claims are extended
        scheduleWorker();

        return canHandleAny;
    }

    private void assertEqualTokens(List<MessageStream.Entry<? extends EventMessage<?>>> eventEntries) {
        TrackingToken expectedToken = TrackingToken.fromContext(eventEntries.get(0)).orElse(null);
        Assert.isTrue(
                eventEntries.stream()
                            .map(entry -> TrackingToken.fromContext(entry).orElse(null))
                            .allMatch(token -> Objects.equals(expectedToken, token)),
                () -> "All tokens should match when scheduling multiple events in one go."
        );
    }

    /**
     * Schedule a {@link MessageStream.Entry} for processing by this work package. Will immediately disregard the given
     * {@code eventEntry} if its {@link TrackingToken} is covered by the previously scheduled event.
     * <p>
     * <b>Threading note:</b> This method is and should only to be called by the {@link Coordinator} thread of a {@link
     * PooledStreamingEventProcessor}.
     *
     * @param eventEntry The event entry to schedule for work in this work package.
     * @return {@code True} if this {@link WorkPackage} scheduled the event for execution, otherwise {@code false}.
     */
    public boolean scheduleEvent(MessageStream.Entry<? extends EventMessage<?>> eventEntry) {
        TrackingToken eventToken = TrackingToken.fromContext(eventEntry).orElse(null);
        if (shouldNotSchedule(eventEntry)) {
            logger.trace("Ignoring event [{}] with position [{}] for work package [{}]. "
                                 + "The last token [{}] covers event's token [{}].",
                         eventEntry.message().identifier(),
                         eventToken != null ? eventToken.position().orElse(-1) : -1,
                         segment.getSegmentId(),
                         lastDeliveredToken,
                         eventToken);
            return false;
        }

        logger.debug("Assigned event [{}] with position [{}] to work package [{}].",
                     eventEntry.message().identifier(),
                     eventToken != null ? eventToken.position().orElse(-1) : -1,
                     segment.getSegmentId());

        var context = new LegacyMessageSupportingContext(eventEntry.message());
        boolean canHandle = canHandle(eventEntry.message(), context);
        processingQueue.add(new DefaultProcessingEntry(eventEntry, canHandle));
        lastDeliveredToken = eventToken;
        // the worker must always be scheduled to ensure claims are extended
        scheduleWorker();

        return canHandle;
    }

    /**
     * The given {@code eventEntry} should not be scheduled if the {@link TrackingToken} extracted from its context
     * {@link TrackingToken#covers(TrackingToken)} the last delivered token.
     * <p>
     * This validation ensures events that this work package already covered are ignored.
     *
     * @param eventEntry The event entry to validate whether it should be scheduled yes or no.
     * @return {@code true} if the given {@code eventEntry} should not be scheduled, {@code false} otherwise.
     */
    private boolean shouldNotSchedule(MessageStream.Entry<? extends EventMessage<?>> eventEntry) {
        TrackingToken eventToken = TrackingToken.fromContext(eventEntry).orElse(null);
        // Null check is done to solve potential NullPointerException.
        return lastDeliveredToken != null && eventToken != null && lastDeliveredToken.covers(eventToken);
    }

    private boolean canHandle(EventMessage<?> eventMessage, ProcessingContext processingContext) {
        try {
            return eventFilter.canHandle(eventMessage, processingContext, segment);
        } catch (Exception e) {
            logger.warn("Error while detecting whether event can be handled in Work Package [{}]-[{}]. "
                                + "Aborting Work Package...",
                        segment.getSegmentId(), name, e);
            abort(e);
            return false;
        }
    }

    /**
     * Schedule this {@link WorkPackage} to process its batch of scheduled events in a dedicated thread.
     * <p>
     * <b>Threading note:</b> This method is safe to be called by both the {@link Coordinator} threads and {@link
     * WorkPackage} threads of a {@link PooledStreamingEventProcessor}.
     */
    public void scheduleWorker() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        logger.debug("Scheduling Work Package [{}]-[{}] to process events.", segment.getSegmentId(), name);

        executorService.submit(() -> {
            CompletableFuture<Exception> aborting = abortFlag.get();
            if (aborting != null) {
                logger.debug("Work Package [{}]-[{}] should be aborted. Will shutdown this work package.",
                             segment.getSegmentId(), name);
                segmentStatusUpdater.accept(previousStatus -> null);
                aborting.complete(abortException.get());
                return;
            }

            try {
                processEvents();
            } catch (Exception e) {
                logger.warn("Error while processing batch in Work Package [{}]-[{}]. Aborting Work Package...",
                            segment.getSegmentId(), name, e);
                abort(e);
            }
            scheduled.set(false);
            if (!processingQueue.isEmpty() || abortFlag.get() != null) {
                logger.debug("Rescheduling Work Package [{}]-[{}] since there are events left.",
                             segment.getSegmentId(), name);
                scheduleWorker();
            }
        });
    }

    private void processEvents() throws Exception {
        List<EventMessage<?>> eventBatch = new ArrayList<>();
        while (!isAbortTriggered() && eventBatch.size() < batchSize && !processingQueue.isEmpty()) {
            ProcessingEntry entry = processingQueue.poll();
            lastConsumedToken = WrappedToken.advance(lastConsumedToken, entry.trackingToken());
            entry.addToBatch(eventBatch);
        }

        // Make sure all subsequent events with the same token (if non-null) as the last are added as well.
        // These are the result of upcasting and should always be processed in the same batch.

        if (!eventBatch.isEmpty()) {
            logger.debug("Work Package [{}]-[{}] is processing a batch of {} events.",
                         segment.getSegmentId(), name, eventBatch.size());
            try {
                processingEvents.set(true);
                var unitOfWork = unitOfWorkFactory.create();
                unitOfWork.runOnPreInvocation(ctx -> {
                    ctx.putResource(Segment.RESOURCE_KEY, segment);
                    ctx.putResource(TrackingToken.RESOURCE_KEY, lastConsumedToken);
                });

                unitOfWork.runOnPrepareCommit(u -> storeToken(lastConsumedToken));
                unitOfWork.runOnAfterCommit(
                        u -> {
                            segmentStatusUpdater.accept(status -> status.advancedTo(lastConsumedToken));
                            batchProcessedCallback.run();
                        }
                );
                batchProcessor.processBatch(eventBatch, unitOfWork, Collections.singleton(segment));
            } finally {
                processingEvents.set(false);
            }
        } else {
            segmentStatusUpdater.accept(status -> status.advancedTo(lastConsumedToken));
            if (lastStoredToken != lastConsumedToken && now() > nextClaimExtension.get()) {
                joinAndUnwrap(
                        unitOfWorkFactory
                                .create()
                                .executeWithResult(context -> {
                                    storeToken(lastConsumedToken);
                                    return emptyCompletedFuture();
                                })
                );
            } else {
                extendClaimIfThresholdIsMet();
            }
        }
    }

    /**
     * Extend the claim of the {@link TrackingToken} owned by this {@code WorkPackage}, if the configurable
     * {@link PooledStreamingEventProcessor.Builder#claimExtensionThreshold(long) claim extension threshold} is met.
     */
    public void extendClaimIfThresholdIsMet() {
        if (now() > nextClaimExtension.get()) {
            logger.debug("Work Package [{}]-[{}] will extend its token claim.", name, segment.getSegmentId());
            joinAndUnwrap(
                    unitOfWorkFactory
                            .create()
                            .executeWithResult(context -> {
                                tokenStore.extendClaim(name, segment.getSegmentId());
                                return emptyCompletedFuture();
                            })
            );
            nextClaimExtension.set(now() + claimExtensionThreshold);
        }
    }

    private void storeToken(TrackingToken token) {
        logger.debug("Work Package [{}]-[{}] will store token [{}].", name, segment.getSegmentId(), token);
        tokenStore.storeToken(token, name, segment.getSegmentId());
        lastStoredToken = token;
        nextClaimExtension.set(now() + claimExtensionThreshold);
    }

    /**
     * Indicates whether this {@link WorkPackage} has any processing capacity remaining, or whether it has reached its
     * soft limit. Note that one can still deliver events for processing in this {@code WorkPackage}.
     *
     * @return {@code true} if the {@link WorkPackage} has remaining capacity, or {@code false} if the soft limit has
     * been reached
     */
    public boolean hasRemainingCapacity() {
        return this.processingQueue.size() < BUFFER_SIZE;
    }

    /**
     * Indicates whether this {@link WorkPackage} has any work in the queue or scheduled.
     *
     * @return {@code true} if the {@code processingQueue} is empty and there is nothing scheduled, or {@code false}
     * otherwise.
     */
    public boolean isDone() {
        return this.processingQueue.isEmpty() && !this.scheduled.get();
    }

    /**
     * Returns the {@link Segment} that this {@link WorkPackage} is processing events for.
     *
     * @return the {@link Segment} that this {@link WorkPackage} is processing events for
     */
    public Segment segment() {
        return segment;
    }

    /**
     * Returns the {@link TrackingToken} of the {@link MessageStream.Entry} that was delivered in the last
     * {@link #scheduleEvent(MessageStream.Entry)} call.
     * <p>
     * <b>Threading note:</b> This method is only safe to call from {@link Coordinator} threads. The {@link
     * WorkPackage} threads must not rely on this method.
     *
     * @return the {@link TrackingToken} of the last {@link MessageStream.Entry} that was delivered to this
     * {@link WorkPackage}
     */
    public TrackingToken lastDeliveredToken() {
        return lastDeliveredToken;
    }

    /**
     * Indicates whether an abort has been triggered for this {@link WorkPackage}. When {@code true}, any events
     * scheduled for processing by this {@code WorkPackage} are likely to be ignored.
     * <p>
     * Use {@link #abort(Exception)} (possibly with a {@code null} reason) to obtain a {@link CompletableFuture} with a
     * reference to the abort reason.
     *
     * @return {@code true} if an abort was scheduled, otherwise {@code false}
     */
    public boolean isAbortTriggered() {
        return abortFlag.get() != null;
    }

    /**
     * Marks this {@link WorkPackage} as <em>aborted</em>. The returned {@link CompletableFuture} is completed with the
     * abort reason once the {@code WorkPackage} has finished any processing that may had been started already.
     * <p>
     * If this {@code WorkPackage} was already aborted in another request, the returned {@code CompletableFuture} will
     * complete with exception for the first request.
     * <p>
     * An aborted {@code WorkPackage} cannot be restarted.
     *
     * @param abortReason the reason to request the {@link WorkPackage} to abort
     * @return a {@link CompletableFuture} that completes with the first reason once the {@link WorkPackage} has stopped
     * processing
     */
    public CompletableFuture<Exception> abort(Exception abortReason) {
        if (abortReason != null) {
            logger.debug("Abort request received for Work Package [{}]-[{}].",
                         name, segment.getSegmentId(), abortReason);
            segmentStatusUpdater.accept(
                    status -> {
                        if (status != null) {
                            return status.isErrorState() ? status : status.markError(abortReason);
                        }
                        return null;
                    }
            );
        }

        CompletableFuture<Exception> abortTask = abortFlag.updateAndGet(
                currentFlag -> {
                    if (currentFlag == null) {
                        abortException.set(abortReason);
                        return new CompletableFuture<>();
                    } else {
                        abortException.updateAndGet(
                                currentReason -> currentReason == null ? abortReason : currentReason
                        );
                        return currentFlag;
                    }
                }
        );
        // Reschedule the worker to ensure the abort flag is processed
        scheduleWorker();
        return abortTask;
    }

    /**
     * Lambda to be invoked whenever the event batch of this package's {@code segment} processed.
     *
     * @param batchProcessedCallback lambda to be invoked whenever the event batch of this package's {@code segment}
     *                               processed
     */
    void onBatchProcessed(Runnable batchProcessedCallback) {
        this.batchProcessedCallback = batchProcessedCallback;
    }

    /**
     * Returns whether this {@code WorkPackage} is actively processing events.
     *
     * @return Whether this {@code WorkPackage} is actively processing events.
     */
    public boolean isProcessingEvents() {
        return processingEvents.get();
    }

    /**
     * Functional interface defining a validation if a given {@link EventMessage} can be handled within the given
     * {@link Segment}.
     */
    @FunctionalInterface
    interface EventFilter {

        /**
         * Indicates whether the work package can handle the given {@code eventMessage} for the given {@code segment}.
         *
         * @param eventMessage the message for which to identify if the work package can handle it
         * @param segment      the segment for which the event can be processed
         * @return {@code true} if the event message can be handled, otherwise {@code false}
         * @throws Exception when validating of the given {@code eventMessage} fails
         */
        boolean canHandle(EventMessage<?> eventMessage, ProcessingContext context, Segment segment) throws Exception;
    }

    /**
     * Functional interface defining the processing of a batch of {@link EventMessage}s within a {@link UnitOfWork}.
     */
    @FunctionalInterface
    interface BatchProcessor {

        /**
         * Processes a given batch of {@code eventMessages}. These {@code eventMessages} will be processed within the
         * given {@code unitOfWork}. The collection of {@link Segment} instances defines the segments for which the
         * {@code eventMessages} should be processed.
         *
         * @param eventMessages      the batch of {@link EventMessage}s that is to be processed
         * @param unitOfWork         the {@link UnitOfWork} that has been prepared to process the {@code eventMessages}
         * @param processingSegments the {@link Segment}s for which the {@code eventMessages} should be processed in the
         *                           given {@code unitOfWork}
         * @throws Exception when an exception occurred during processing of the batch of {@code eventMessages}
         */
        void processBatch(List<? extends EventMessage<?>> eventMessages,
                          UnitOfWork unitOfWork,
                          Collection<Segment> processingSegments) throws Exception;
    }

    /**
     * Package private builder class to construct a {@link WorkPackage}. Not used for validation of the fields as is the
     * case with most builders, but purely to clarify the construction of a {@code WorkPackage}.
     */
    static class Builder {

        private String name;
        private TokenStore tokenStore;
        private UnitOfWorkFactory unitOfWorkFactory;
        private ExecutorService executorService;
        private EventFilter eventFilter;
        private BatchProcessor batchProcessor;
        private Segment segment;
        private TrackingToken initialToken;
        private int batchSize = 1;
        private long claimExtensionThreshold = 5000;
        private Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater;
        private Clock clock = GenericEventMessage.clock;

        /**
         * The {@code name} of the processor this {@link WorkPackage} processes events for.
         *
         * @param name the name of the processor this {@link WorkPackage} processes events for
         * @return the current Builder instance, for fluent interfacing
         */
        Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * The storage solution of {@link TrackingToken}s. Used to extend claims on and update the
         * {@code initialToken}.
         *
         * @param tokenStore the storage solution of {@link TrackingToken}s
         * @return the current Builder instance, for fluent interfacing
         */
        Builder tokenStore(TokenStore tokenStore) {
            this.tokenStore = tokenStore;
            return this;
        }

        /**
         * A {@link UnitOfWorkFactory} used to invoke {@link TokenStore} operations and event processing inside a
         * {@link UnitOfWork} (you may use
         * {@link TransactionalUnitOfWorkFactory to execute those operations transactionally}.
         *
         * @param unitOfWorkFactory a factory for {@link UnitOfWork} used to invoke {@link TokenStore} operations and
         *                          event processing
         * @return the current Builder instance, for fluent interfacing
         */
        Builder unitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
            this.unitOfWorkFactory = unitOfWorkFactory;
            return this;
        }

        /**
         * A {@link ExecutorService} used to run this work package's tasks in.
         *
         * @param executorService a {@link ExecutorService} used to run this work package's tasks in
         * @return the current Builder instance, for fluent interfacing
         */
        Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Checks whether a buffered event can be handled by this package's {@code segment}.
         *
         * @param eventFilter checks whether a buffered event can be handled by this package's {@code segment}
         * @return the current Builder instance, for fluent interfacing
         */
        Builder eventFilter(EventFilter eventFilter) {
            this.eventFilter = eventFilter;
            return this;
        }

        /**
         * A processor of a batch of events.
         *
         * @param batchProcessor processes a batch of events
         * @return the current Builder instance, for fluent interfacing
         */
        Builder batchProcessor(BatchProcessor batchProcessor) {
            this.batchProcessor = batchProcessor;
            return this;
        }

        /**
         * The {@link Segment} this work package is in charge of.
         *
         * @param segment the {@link Segment} this work package is in charge of
         * @return the current Builder instance, for fluent interfacing
         */
        Builder segment(Segment segment) {
            this.segment = segment;
            return this;
        }

        /**
         * The initial {@link TrackingToken} when this package starts processing events.
         *
         * @param initialToken the initial {@link TrackingToken} when this package starts processing events
         * @return the current Builder instance, for fluent interfacing
         */
        Builder initialToken(TrackingToken initialToken) {
            this.initialToken = initialToken;
            return this;
        }

        /**
         * The amount of events to be processed in a single batch. Defaults to {@code 1};
         *
         * @param batchSize the amount of events to be processed in a single batch
         * @return the current Builder instance, for fluent interfacing
         */
        Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * The time in milliseconds after which the claim of the {@link TrackingToken} will be extended. Will only be
         * used in absence of regular token updates through event processing. Defaults to {@code 5000};
         *
         * @param claimExtensionThreshold the time in milliseconds after which the claim of the {@link TrackingToken}
         *                                will be extended
         * @return the current Builder instance, for fluent interfacing
         */
        Builder claimExtensionThreshold(long claimExtensionThreshold) {
            this.claimExtensionThreshold = claimExtensionThreshold;
            return this;
        }

        /**
         * Lambda to be invoked whenever the status of this package's {@code segment} changes.
         *
         * @param segmentStatusUpdater lambda to be invoked whenever the status of this package's {@code segment}
         *                             changes
         * @return the current Builder instance, for fluent interfacing
         */
        Builder segmentStatusUpdater(Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater) {
            this.segmentStatusUpdater = segmentStatusUpdater;
            return this;
        }

        /**
         * Defines the {@link Clock} used for time dependent operations. For example used to update whenever this
         * {@link WorkPackage} updated the {@link TrackingToken} claim last. Defaults to
         * {@link GenericEventMessage#clock}.
         *
         * @param clock the {@link Clock} used for time dependent operations
         * @return the current Builder instance, for fluent interfacing
         */
        Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Initializes a {@link WorkPackage} as specified through this Builder.
         *
         * @return a {@link WorkPackage} as specified through this Builder
         */
        WorkPackage build() {
            return new WorkPackage(this);
        }
    }

    /**
     * Marker interface defining a unit of work containing one or more event messages to be processed by this work
     * package.
     */
    private interface ProcessingEntry {

        /**
         * Return the position of this processing entry.
         *
         * @return The position of this processing entry.
         */
        TrackingToken trackingToken();

        /**
         * Add this entry's events to the {@code eventBatch}. Since tracking is handled at the UnitOfWork level,
         * we only need to add the actual event messages to the batch.
         *
         * @param eventBatch The list of events to add this entry's events to.
         */
        void addToBatch(List<EventMessage<?>> eventBatch);
    }

    /**
     * Container of a {@link MessageStream.Entry} and {@code boolean} whether the given {@code eventMessage} can be
     * handled in this package. The combination constitutes to a processing entry the {@link WorkPackage} should
     * ingest.
     */
    private static class DefaultProcessingEntry implements ProcessingEntry {

        private final MessageStream.Entry<? extends EventMessage<?>> eventEntry;
        private final boolean canHandle;

        public DefaultProcessingEntry(MessageStream.Entry<? extends EventMessage<?>> eventEntry, boolean canHandle) {
            this.eventEntry = eventEntry;
            this.canHandle = canHandle;
        }

        @Override
        public TrackingToken trackingToken() {
            return TrackingToken.fromContext(eventEntry).orElse(null);
        }

        @Override
        public void addToBatch(List<EventMessage<?>> eventBatch) {
            if (canHandle) {
                eventBatch.add(eventEntry.message());
            }
        }
    }

    /**
     * Container of a batch of {@link ProcessingEntry ProcessingEntries}. These entries are grouped together since they
     * should be handled within a single batch by the work package.
     */
    private static class BatchProcessingEntry implements ProcessingEntry {

        private final List<ProcessingEntry> processingEntries;

        public BatchProcessingEntry() {
            this.processingEntries = new ArrayList<>();
        }

        public void add(ProcessingEntry processingEntry) {
            processingEntries.add(processingEntry);
        }

        @Override
        public TrackingToken trackingToken() {
            return processingEntries.get(0).trackingToken();
        }

        @Override
        public void addToBatch(List<EventMessage<?>> eventBatch) {
            processingEntries.forEach(entry -> entry.addToBatch(eventBatch));
        }
    }
}