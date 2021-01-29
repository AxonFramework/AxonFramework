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
 * TODO fill class level javadoc
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
    private final HandlerValidator handlerValidator;
    private final BatchProcessor eventBatchProcessor;
    private final Segment segment;
    private final long claimExtensionThreshold;
    private final Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater;

    // For use only by event delivery threads
    private TrackingToken lastDeliveredToken;
    private TrackingToken lastConsumedToken;
    private TrackingToken lastStoredToken;
    private long lastClaimExtension;

    private final Queue<TrackedEventMessage<?>> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Exception>> abortFlag = new AtomicReference<>();

    public WorkPackage(String name,
                       TokenStore tokenStore,
                       TransactionManager transactionManager,
                       ScheduledExecutorService executorService,
                       HandlerValidator handlerValidator,
                       BatchProcessor eventBatchProcessor,
                       Segment segment,
                       TrackingToken initialToken,
                       Consumer<UnaryOperator<TrackerStatus>> segmentStatusUpdater) {
        this.name = name;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
        this.executorService = executorService;
        this.handlerValidator = handlerValidator;
        this.eventBatchProcessor = eventBatchProcessor;
        this.segment = segment;
        this.claimExtensionThreshold = 5000; // TODO make configurable
        this.lastDeliveredToken = initialToken;
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
        logger.debug("Assigned event [{}] to work package [{}].", event.getIdentifier(), segment.getSegmentId());

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
                aborting.complete(null);
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
        while (eventBatch.size() < BATCH_SIZE && !events.isEmpty()) {
            TrackedEventMessage<?> event = events.poll();
            lastConsumedToken = WrappedToken.advance(lastConsumedToken, event.trackingToken());
            try {
                if (handlerValidator.canHandle(event, segment)) {
                    eventBatch.add(event);
                }
            } catch (Exception e) {
                handleError(e);
            }
        }

        if (!eventBatch.isEmpty()) {
            logger.debug("Work Package [{}]-[{}] is processing a batch of {} events.",
                         segment.getSegmentId(), name, eventBatch.size());
            UnitOfWork<TrackedEventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventBatch);
            try {
                unitOfWork.attachTransaction(transactionManager);
                unitOfWork.onPrepareCommit(u -> storeToken(lastConsumedToken));
                unitOfWork.afterCommit(u -> segmentStatusUpdater.accept(s -> s.advancedTo(lastConsumedToken)));
                eventBatchProcessor.processInUnitOfWork(eventBatch, unitOfWork, Collections.singleton(segment));
            } catch (Exception e) {
                handleError(e);
            }
        } else {
            segmentStatusUpdater.accept(s -> s.advancedTo(lastConsumedToken));
            // Empty batch, check for token extension time
            long now = System.currentTimeMillis();
            if (lastClaimExtension < now - claimExtensionThreshold) {
                logger.info("Extending claims for {} segment {}", name, segment.getSegmentId());

                Runnable tokenOperation = lastStoredToken == lastConsumedToken
                        ? () -> tokenStore.extendClaim(name, segment.getSegmentId())
                        : () -> storeToken(lastConsumedToken);
                transactionManager.executeInTransaction(tokenOperation);
                lastClaimExtension = now;
            }
        }
    }

    private void handleError(Exception cause) {
        segmentStatusUpdater.accept(s -> s.markError(cause));
        abortFlag.updateAndGet(e -> getOrDefault(e, () -> CompletableFuture.completedFuture(cause)));
        // TODO: 26-01-21 find way to release claim
    }

    private void storeToken(TrackingToken token) {
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
        return this.lastDeliveredToken;
    }

    /**
     * Returns the {@link Segment} that this {@link WorkPackage} is processing events for.
     *
     * @return the {@link Segment} that this {@link WorkPackage} is processing events for
     */
    public Segment getSegment() {
        return segment;
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
                currentReason -> currentReason == null
                        ? new CompletableFuture<Exception>().thenApply(ex -> getOrDefault(ex, abortReason))
                        : currentReason
        );
        // Reschedule the worker to ensure the abort flag is processed
        scheduleWorker();
        return abortTask;
    }

    /**
     * TODO fill class level javadoc
     */
    @FunctionalInterface
    public interface BatchProcessor {

        /**
         * @param eventMessages
         * @param unitOfWork
         * @param processingSegments
         * @throws Exception
         */
        void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                 UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 Collection<Segment> processingSegments) throws Exception;
    }

    /**
     * TODO fill class level javadoc
     */
    @FunctionalInterface
    public interface HandlerValidator {

        /**
         * @param eventMessage
         * @param segment
         * @return
         * @throws Exception
         */
        boolean canHandle(TrackedEventMessage<?> eventMessage, Segment segment) throws Exception;
    }
}
