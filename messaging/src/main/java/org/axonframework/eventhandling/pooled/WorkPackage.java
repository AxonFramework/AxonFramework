package org.axonframework.eventhandling.pooled;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.WrappedToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Defines the process of handling {@link EventMessage}s for a specific {@link Segment}. This entails validating if the
 * event should be handled through a {@link EventValidator} and after that processing a collection of events in the
 * {@link BatchProcessor}.
 * <p>
 * Events are received through the {@link #scheduleEvent(TrackedEventMessage)} operation, delegated by a {@link
 * Coordinator}. Receiving event(s) means this job will be scheduled to process these events through a {@link
 * ScheduledExecutorService}. As there are local threads and outside threads invoking methods on the {@link
 * WorkPackage}, several methods have threading notes describing what can invoke them safely.
 * <p>
 * Since the {@code WorkPackage} is in charge of a {@code Segment}, it maintains the claim on the matching {@link
 * TrackingToken}. In absence of new events, it will also {@link TokenStore#extendClaim(String, int)} on the {@code
 * TrackingToken}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see PooledTrackingEventProcessor
 * @see Coordinator
 * @since 4.5
 */
class WorkPackage {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int BATCH_SIZE = 100;
    static final int BUFFER_SIZE = 1024;

    private final String name;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;
    private final ScheduledExecutorService executorService;
    private final EventValidator eventValidator;
    private final BatchProcessor batchProcessor;
    private final Segment segment;
    private final long claimExtensionThreshold;
    private final Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater;

    private TrackingToken lastDeliveredToken; // For use only by event delivery threads, like Coordinator
    private TrackingToken lastConsumedToken;
    private TrackingToken lastStoredToken;
    private long lastClaimExtension;

    private final Queue<TrackedEventMessage<?>> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Exception>> abortFlag = new AtomicReference<>();
    private final AtomicReference<Exception> abortException = new AtomicReference<>();

    /**
     * Constructs a {@link WorkPackage}.
     *
     * @param name                    the name of the processor this {@link WorkPackage} processes events for
     * @param tokenStore              the storage solution of {@link TrackingToken}s. Used to extend claims on and
     *                                update the {@code initialToken}
     * @param transactionManager      a {@link TransactionManager} used to invoke {@link TokenStore} operations and
     *                                event processing inside a transaction
     * @param executorService         a {@link ScheduledExecutorService} used to run this work package's tasks in
     * @param eventValidator          validates whether a buffered event should be handled by this package's {@code
     *                                segment}
     * @param batchProcessor          processes a batch of events
     * @param segment                 the {@link Segment} this work package is in charge of
     * @param initialToken            the initial {@link TrackingToken} when this package starts processing events
     * @param claimExtensionThreshold the time in milliseconds after which the claim of the {@link TrackingToken} will
     *                                be extended. Will only be used in absence of regular token updates through event
     *                                processing
     * @param segmentStatusUpdater    lambda to be invoked whenever the status of this package's {@code segment}
     *                                changes
     */
    public WorkPackage(String name,
                       TokenStore tokenStore,
                       TransactionManager transactionManager,
                       ScheduledExecutorService executorService,
                       EventValidator eventValidator,
                       BatchProcessor batchProcessor,
                       Segment segment,
                       TrackingToken initialToken,
                       long claimExtensionThreshold,
                       Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater) {
        this.name = name;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
        this.executorService = executorService;
        this.eventValidator = eventValidator;
        this.batchProcessor = batchProcessor;
        this.segment = segment;
        this.lastDeliveredToken = initialToken;
        this.claimExtensionThreshold = claimExtensionThreshold;
        this.segmentStatusUpdater = segmentStatusUpdater;

        this.lastConsumedToken = initialToken;
        this.lastClaimExtension = System.currentTimeMillis();
    }

    /**
     * Schedule a {@link TrackedEventMessage} for processing by this work package. Will immediately disregard the given
     * {@code event} if its {@link TrackingToken} is covered by the previously scheduled event.
     * <p>
     * <b>Threading note:</b> This method is and should only to be called by the {@link Coordinator} thread of a {@link
     * PooledTrackingEventProcessor}
     *
     * @param event the event to schedule for work in this work package
     */
    public void scheduleEvent(TrackedEventMessage<?> event) {
        if (lastDeliveredToken != null && lastDeliveredToken.covers(event.trackingToken())) {
            return;
        }
        logger.debug("Assigned event [{}] with position [{}] to work package [{}].",
                     event.getIdentifier(), event.trackingToken().position().orElse(-1), segment.getSegmentId());

        events.add(event);
        lastDeliveredToken = event.trackingToken();
        scheduleWorker();
    }

    /**
     * Schedule this {@link WorkPackage} to process its batch of scheduled events in a dedicated thread.
     * <p>
     * <b>Threading note:</b> This method is safe to be called by both the {@link Coordinator} threads and {@link
     * WorkPackage} threads of a {@link PooledTrackingEventProcessor}.
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

            processEvents();
            scheduled.set(false);
            if (!events.isEmpty() || abortFlag.get() != null) {
                logger.debug("Rescheduling Work Package [{}]-[{}] since there are events left.",
                             segment.getSegmentId(), name);
                scheduleWorker();
            }
        });
    }

    private void processEvents() {
        List<TrackedEventMessage<?>> eventBatch = new ArrayList<>();
        while (!isAbortTriggered() && eventBatch.size() < BATCH_SIZE && !events.isEmpty()) {
            TrackedEventMessage<?> event = events.poll();
            lastConsumedToken = WrappedToken.advance(lastConsumedToken, event.trackingToken());
            try {
                if (eventValidator.shouldHandle(event, segment)) {
                    eventBatch.add(event);
                }
            } catch (Exception e) {
                handleError(e);
                lastConsumedToken = lastStoredToken;
                return;
            }
        }

        if (!eventBatch.isEmpty()) {
            logger.debug("Work Package [{}]-[{}] is processing a batch of {} events.",
                         segment.getSegmentId(), name, eventBatch.size());
            UnitOfWork<TrackedEventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventBatch);
            try {
                unitOfWork.attachTransaction(transactionManager);
                unitOfWork.onPrepareCommit(u -> storeToken(lastConsumedToken));
                unitOfWork.afterCommit(
                        u -> segmentStatusUpdater.accept(status -> status.advancedTo(lastConsumedToken))
                );
                batchProcessor.processBatch(eventBatch, unitOfWork, Collections.singleton(segment));
            } catch (Exception e) {
                handleError(e);
            }
        } else {
            segmentStatusUpdater.accept(status -> status.advancedTo(lastConsumedToken));
            // Empty batch, check for token extension time
            long now = System.currentTimeMillis();
            if (lastClaimExtension < now - claimExtensionThreshold) {
                logger.debug("Extending claim on segment for Work Package [{}]-[{}].", name, segment.getSegmentId());

                Runnable tokenOperation = lastStoredToken == lastConsumedToken
                        ? () -> tokenStore.extendClaim(name, segment.getSegmentId())
                        : () -> storeToken(lastConsumedToken);
                transactionManager.executeInTransaction(tokenOperation);
                lastClaimExtension = now;
            }
        }
    }

    private void handleError(Exception cause) {
        logger.warn("Work Package [{}]-[{}] is handling error [{}].", name, segment.getSegmentId(), cause);
        segmentStatusUpdater.accept(status -> status.markError(cause));
        abortFlag.updateAndGet(e -> getOrDefault(e, () -> CompletableFuture.completedFuture(cause)));
    }

    private void storeToken(TrackingToken token) {
        logger.debug("Work Package [{}]-[{}] will store token [{}].", name, segment.getSegmentId(), token);
        tokenStore.storeToken(token, name, segment.getSegmentId());
        lastStoredToken = token;
        lastClaimExtension = System.currentTimeMillis();
    }

    /**
     * Indicates whether this {@link WorkPackage} has any processing capacity remaining, or whether it has reached its
     * soft limit. Note that one can still deliver events for processing in this {@code WorkPackage}.
     *
     * @return {@code true} if the {@link WorkPackage} has remaining capacity, or {@code false} if the soft limit has
     * been reached
     */
    public boolean hasRemainingCapacity() {
        return this.events.size() < BUFFER_SIZE;
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
     * Returns the {@link TrackingToken} of the {@link TrackedEventMessage} that was delivered in the last {@link
     * #scheduleEvent(TrackedEventMessage)} call.
     * <p>
     * <b>Threading note:</b> This method is only safe to call from {@link Coordinator} threads. The {@link
     * WorkPackage} threads must not rely on this method.
     *
     * @return the {@link TrackingToken} of the last {@link TrackedEventMessage} that was delivered to this {@link
     * WorkPackage}
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
     * complete with the exception of the first request.
     * <p>
     * An aborted {@code WorkPackage} cannot be restarted.
     *
     * @param abortReason the reason to request the {@link WorkPackage} to abort
     * @return a {@link CompletableFuture} that completes with the first reason once the {@link WorkPackage} has stopped
     * processing
     */
    public CompletableFuture<Exception> abort(Exception abortReason) {
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
     * Mark this {@link WorkPackage} to stop processing events. The returned {@link CompletableFuture} is completed with
     * the {@link TrackingToken} of the last processed {@link TrackedEventMessage}.
     * <p>
     * Note that this approach aborts the work package, which cannot be restarted afterwards.
     *
     * @return a {@link CompletableFuture} is completed with the {@link TrackingToken} of the last processed {@link
     * TrackedEventMessage}
     */
    public CompletableFuture<TrackingToken> stopPackage() {
        return abort(null).thenApply(e -> lastStoredToken);
    }

    /**
     * Functional interface defining a validation if a given {@link TrackedEventMessage} should be handled within the
     * given {@link Segment}.
     */
    @FunctionalInterface
    interface EventValidator {

        /**
         * Indicates whether the work package should handle the given {@code eventMessage} for the given {@code
         * segment}.
         *
         * @param eventMessage the message for which to identify if the work package should handle it
         * @param segment      the segment for which the event should be processed
         * @return {@code true} if the event message should be handled, otherwise {@code false}
         * @throws Exception when validating if the given {@code eventMessage} fails
         */
        boolean shouldHandle(TrackedEventMessage<?> eventMessage, Segment segment) throws Exception;
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
                          UnitOfWork<? extends EventMessage<?>> unitOfWork,
                          Collection<Segment> processingSegments) throws Exception;
    }
}
