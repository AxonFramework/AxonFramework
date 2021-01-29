package org.axonframework.eventhandling.pooled;

import org.axonframework.common.ObjectUtils;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

// TODO: 22-01-21 add javadoc
class WorkPackage {

    private static final Logger logger = LoggerFactory.getLogger(WorkPackage.class);
    private static final int BATCH_SIZE = 100;
    static final int BUFFER_SIZE = 1024;

    private final String name;
    private final Segment segment;
    private final BatchProcessor processor;
    private final HandlerValidator validator;
    private final ExecutorService executor;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;
    private long lastClaimExtension;
    private final long claimExtensionThreshold;

    private final Queue<TrackedEventMessage<?>> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    // For use only by event delivery threads
    private TrackingToken lastDeliveredToken;
    private TrackingToken lastConsumedToken;
    private TrackingToken lastStoredToken;
    private final AtomicReference<CompletableFuture<Exception>> abortFlag = new AtomicReference<>();
    private final Consumer<UnaryOperator<TrackerStatus>> status;

    public WorkPackage(String name,
                       Segment segment,
                       TrackingToken initialToken,
                       BatchProcessor processor,
                       HandlerValidator canHandle,
                       ExecutorService executor,
                       TokenStore tokenStore,
                       TransactionManager transactionManager,
                       Consumer<UnaryOperator<TrackerStatus>> statusUpdater) {
        this.name = name;
        this.segment = segment;
        this.lastDeliveredToken = initialToken;
        this.processor = processor;
        this.validator = canHandle;
        this.executor = executor;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
        this.lastClaimExtension = System.currentTimeMillis();
        this.lastConsumedToken = initialToken;
        // TODO make configurable
        this.claimExtensionThreshold = 5000;
        this.status = statusUpdater;
    }

    private void processEvents() {
        List<TrackedEventMessage<?>> batch = new ArrayList<>();
        // handle a batch
        while (batch.size() < BATCH_SIZE && !events.isEmpty()) {
            TrackedEventMessage<?> message = events.poll();
            lastConsumedToken = message.trackingToken();
            try {
                if (validator.canHandle(message, segment)) {
                    batch.add(message);
                }
            } catch (Exception e) {
                handleError(e);
            }
        }

        if (!batch.isEmpty()) {
            logger.debug("Processing a batch of {} messages", batch.size());
            BatchingUnitOfWork<TrackedEventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(batch);
            try {
                unitOfWork.attachTransaction(transactionManager);
                unitOfWork.onPrepareCommit(u -> storeToken(lastConsumedToken));
                unitOfWork.afterCommit(u -> status.accept(s -> s.advancedTo(lastConsumedToken)));
                processor.processInUnitOfWork(batch, unitOfWork, Collections.singleton(segment));
            } catch (Exception e) {
                handleError(e);
            }
        } else {
            status.accept(s -> s.advancedTo(lastConsumedToken));
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

    /**
     * Inidicates whether an Abort has been triggered for this Work Package. When {@code true}, any messages
     * scheduled for processing by this Work Package are likely to be ignored.
     * <p>
     * Use {@link #abort(Exception)} (possibly with a {@code null} reason) to obtain a CompletableFuture with a
     * reference to the abort reason.
     *
     * @return {@code true} if an abort was scheduled, otherwise {@code false}
     */
    public boolean isAbortTriggered() {
        return abortFlag.get() != null;
    }

    private void handleError(Exception cause) {
        status.accept(s -> s.markError(cause));
        abortFlag.updateAndGet(e -> ObjectUtils.getOrDefault(e, () -> CompletableFuture.completedFuture(cause)));
    }

    private void storeToken(TrackingToken token) {
        tokenStore.storeToken(token, name, segment.getSegmentId());
        lastStoredToken = token;
        lastClaimExtension = System.currentTimeMillis();
    }

    /**
     * Schedule an Event for processing.
     * <p>
     * Threading note: This method is only to be called by the Coordinator Thread of the PooledTrackingProcessors
     *
     * @param message The message to schedule in this Work Package
     */
    public void scheduleEvent(TrackedEventMessage<?> message) {
        if (this.lastDeliveredToken != null && this.lastDeliveredToken.covers(message.trackingToken())) {
            return;
        }
        logger.debug("Assigned message to work package {}", segment.getSegmentId());
        events.add(message);
        this.lastDeliveredToken = message.trackingToken();
        scheduleWorker();
    }

    /**
     * Indicates whether this Work Package has any processing capacity remaining, or whether it has reached it's soft
     * limit. Note that one can still deliver Event for processing in this Work Package.
     *
     * @return {@code true} if the Work Package has remaining capacity, or {@code false} if the soft limit has been
     * reached.
     */
    public boolean hasRemainingCapacity() {
        return this.events.size() < 1024;
    }

    /**
     * Returns the Tracking Token of the Message that was delivered in the last {@link
     * #scheduleEvent(TrackedEventMessage)} call.
     * <p>
     * Threading note: This method is only safe to call from Coordinator Threads. The Worker Processor thread must not
     * rely on this method.
     *
     * @return the Tracking Token of the last Message that was delivered to this Work Package
     */
    public TrackingToken lastDeliveredToken() {
        return this.lastDeliveredToken;
    }

    /**
     * Marks this WorkPackage as "aborted". The returned CompletableFuture is completed with the abort reason once the
     * WorkPackage has finished any processing that may had been started already.
     * <p>
     * If this WorkPackage was already aborted in another request, the returned CompletableFuture will complete with the
     * exception of the first request.
     * <p>
     * An aborted WorkPackage cannot be restarted.
     *
     * @param abortReason The reason to request the WorkPackage to abort
     *
     * @return a CompletableFuture that completes with the first reason once the WorkPackage has stopped processing.
     */
    public CompletableFuture<Exception> abort(Exception abortReason) {
        CompletableFuture<Exception> abortTask = abortFlag.updateAndGet(currentAbortReason -> {
            if (currentAbortReason == null) {
                return new CompletableFuture<Exception>().thenApply(ex -> ObjectUtils.getOrDefault(ex, abortReason));
            }
            return currentAbortReason;
        });
        // schedule the worker to ensure the abort flag is processed
        scheduleWorker();
        return abortTask;
    }

    /**
     * Threading note: This method is safe to be called by both the Coordinator Threads and Worker Threads of the
     * PooledTrackingProcessors
     */
    public void scheduleWorker() {
        if (scheduled.compareAndSet(false, true)) {
            logger.debug("Scheduled work package {}", segment.getSegmentId());
            executor.submit(() -> {
                CompletableFuture<Exception> aborting = abortFlag.get();
                if (aborting != null) {
                    status.accept(s -> null);
                    aborting.complete(null);
                    return;
                }

                processEvents();
                scheduled.set(false);
                if (!events.isEmpty() || abortFlag.get() != null) {
                    scheduleWorker();
                }
            });
        }
    }

    /**
     * Returns the Segment that this Worker is processing Events for
     *
     * @return the Segment that this Worker is processing Events for
     */
    public Segment getSegment() {
        return segment;
    }

    @FunctionalInterface
    public interface BatchProcessor {

        void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                 UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 Collection<Segment> processingSegments) throws Exception;

    }

    @FunctionalInterface
    public interface HandlerValidator {

        boolean canHandle(TrackedEventMessage<?> eventMessage, Segment segment) throws Exception;
    }

}
