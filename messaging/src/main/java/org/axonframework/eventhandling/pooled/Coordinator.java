package org.axonframework.eventhandling.pooled;

import org.axonframework.common.io.IOUtils;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.messaging.StreamableMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Coordinator for the {@link PooledTrackingEventProcessor}. Uses coordination tasks (separate threads) to starts a work
 * package for every {@link TrackingToken} it is able to claim. The tokens for every work package are combined and the
 * lower bound of this combined token is used to open an event stream from a {@link StreamableMessageSource}. Events are
 * scheduled one by one to <em>all</em> work packages coordinated by this service.
 * <p>
 * Coordination tasks will run and be rerun as long as this service is considered to be {@link #isRunning()}.
 * Coordination will continue whenever exceptions occur, albeit with an incremental back off. Due to this, both {@link
 * #isError()} and {@link #isRunning()} can result in {@code true} at the same time.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see PooledTrackingEventProcessor
 * @since 4.5
 */
class Coordinator {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;
    private final ScheduledExecutorService executorService;
    private final BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory;
    private final BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater;
    private final int batchSize;
    private final long tokenClaimInterval;

    private final Map<Integer, WorkPackage> workPackages = new ConcurrentHashMap<>();

    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.initial());
    private int errorWaitBackOff = 500;
    // TODO: 26-01-21 use to check whether to reuse found segments on startWorkPackages
    private final ConcurrentMap<Integer, Instant> releasesDeadlines = new ConcurrentHashMap<>();

    /**
     * Constructs a {@link Coordinator}.
     *
     * @param name                    the name of the processor this service coordinates for
     * @param messageSource           the source of events this coordinator should schedule per work package
     * @param tokenStore              the storage solution for {@link TrackingToken}s. Used to find and claim unclaimed
     *                                segments for a processor
     * @param transactionManager      a {@link TransactionManager} used to invoke all {@link TokenStore} operations
     *                                inside a transaction
     * @param executorService         a {@link ScheduledExecutorService} used to run this coordinators tasks with
     * @param workPackageFactory      factory method to construct work packages
     * @param processingStatusUpdater lambda used to update the processing status per work package
     * @param batchSize               the maximum number of events to process in a single batch
     * @param tokenClaimInterval      the time in milliseconds this coordinator will wait to reattempt claiming segments
     *                                for processing
     */
    public Coordinator(String name,
                       StreamableMessageSource<TrackedEventMessage<?>> messageSource,
                       TokenStore tokenStore,
                       TransactionManager transactionManager,
                       ScheduledExecutorService executorService,
                       BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory,
                       BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater,
                       int batchSize,
                       long tokenClaimInterval) {
        this.name = name;
        this.messageSource = messageSource;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
        this.workPackageFactory = workPackageFactory;
        this.executorService = executorService;
        this.processingStatusUpdater = processingStatusUpdater;
        this.batchSize = batchSize;
        this.tokenClaimInterval = tokenClaimInterval;
    }

    /**
     * Start the event coordination task of this coordinator. Will shutdown this service immediately if the coordination
     * task cannot be started.
     */
    public void start() {
        RunState newState = this.runState.updateAndGet(RunState::attemptStart);
        if (newState.wasStarted()) {
            logger.info("Starting coordinator for processor [{}].", name);
            try {
                executorService.submit(new CoordinatorTask());
            } catch (Exception e) {
                // A failure starting the processor. We need to stop immediately.
                logger.warn("An error occurred while trying to attempt to start the coordinator for processor [{}].",
                            name, e);
                runState.updateAndGet(RunState::attemptStop)
                        .shutdownHandle()
                        .complete(null);
                throw e;
            }
        } else if (!newState.isRunning) {
            throw new IllegalStateException("Cannot start a processor while it's in process of shutting down.");
        }
    }

    /**
     * Initiates a shutdown, providing a {@link CompletableFuture} that completes when the shutdown process is
     * finished.
     *
     * @return a CompletableFuture that completes when the shutdown process is finished
     */
    public CompletableFuture<Void> stop() {
        logger.info("Stopping coordinator for processor [{}].", name);
        return runState.updateAndGet(RunState::attemptStop)
                       .shutdownHandle();
    }

    /**
     * Returns {@code true} if this coordinator is running.
     *
     * @return {@code true} if this coordinator is running, {@code false} otherwise
     */
    public boolean isRunning() {
        return runState.get()
                       .isRunning();
    }

    /**
     * Returns {@code true} if this coordinator is in an error state.
     *
     * @return {@code true} if this coordinator is in an error state, {@code false} otherwise
     */
    public boolean isError() {
        return errorWaitBackOff > 500;
    }

    /**
     * Instructs this coordinator to release segment for the given {@code segmentId}. Furthermore, it will not be
     * ignored for "re-claiming" for the specified {@code releaseDuration}.
     * <p>
     * If the coordinator is not actively processing {@code segmentId}, it will be disregarded for processing for the
     * given timeframe nonetheless.
     *
     * @param segmentId       the id of the segment to be blacklisted
     * @param releaseDuration the amount of time {@code segmentId} should be ignored for "re-claiming"
     * @see StreamingEventProcessor#releaseSegment(int, long, TimeUnit)
     */
    public void releaseUntil(int segmentId, Instant releaseDuration) {
        logger.debug("Coordinator [{}] will disregard segment [{}] for processing until {}.",
                     name, segmentId, releaseDuration);
        releasesDeadlines.put(segmentId, releaseDuration);
        WorkPackage workPackage = workPackages.get(segmentId);
        if (workPackage != null) {
            workPackage.abort(null)
                       .thenRun(() -> logger.info("Released segment [{}] until {}", segmentId, releaseDuration));
        } else {
            logger.debug("Coordinator [{}] does not have to release segment [{}] as it is not in charge of it.",
                         name, segmentId);
        }
    }


    /**
     * Status holder for this service. Defines whether it is running, has been started (to ensure double {@link
     * #start()} invocations do not restart this coordinator) and maintains a shutdown handler to complete
     * asynchronously through {@link #stop()}.
     */
    private static class RunState {

        private final boolean isRunning;
        private final boolean wasStarted;
        private final CompletableFuture<Void> shutdownHandle;

        private RunState(boolean isRunning, boolean wasStarted, CompletableFuture<Void> shutdownHandle) {
            this.isRunning = isRunning;
            this.wasStarted = wasStarted;
            this.shutdownHandle = shutdownHandle;
        }

        public static RunState initial() {
            return new RunState(false, false, CompletableFuture.completedFuture(null));
        }

        public RunState attemptStart() {
            if (isRunning) {
                // It was already started
                return new RunState(true, false, null);
            } else if (shutdownHandle.isDone()) {
                // Shutdown has previously been completed. It's allowed to start
                return new RunState(true, true, null);
            } else {
                // Shutdown is in progress
                return this;
            }
        }

        public RunState attemptStop() {
            return !isRunning || shutdownHandle != null
                    ? this // It's already stopped
                    : new RunState(false, false, new CompletableFuture<>());
        }

        public boolean isRunning() {
            return isRunning;
        }

        public boolean wasStarted() {
            return wasStarted;
        }

        public CompletableFuture<Void> shutdownHandle() {
            return shutdownHandle;
        }
    }

    /**
     * A {@link Runnable} defining the entire coordination process delt with by a {@link Coordinator}.
     */
    private class CoordinatorTask implements Runnable {

        private static final int MAX_EVENT_TO_FETCH = 1024;

        private final AtomicBoolean processingGate = new AtomicBoolean();
        private final AtomicBoolean scheduledGate = new AtomicBoolean();
        private final AtomicBoolean interruptibleScheduledGate = new AtomicBoolean();
        private BlockingStream<TrackedEventMessage<?>> stream;
        private boolean availabilityCallbackSupported;

        @Override
        public void run() {
            if (!processingGate.compareAndSet(false, true)) {
                // Another thread is already processing, so stop this invocation.
                return;
            }

            if (!runState.get().isRunning()) {
                logger.debug("Stopped processing. Runnable flag is false.");
                logger.debug("Releasing claims for processor [{}].", name);
                abortWorkPackages(null).thenRun(() -> runState.get().shutdownHandle.complete(null));
                return;
            }

            if (stream == null) {
                // TODO - Also execute this when last check for unclaimed segments was beyond to define threshold (tokenClaimInterval)
                TrackingToken baseToken = startWorkPackages();
                openStream(baseToken);
            }

            if (workPackages.isEmpty()) {
                // We didn't start any work packages. Retry later.
                logger.debug("No Segments claimed. Will retry in {} milliseconds.", tokenClaimInterval);
                processingGate.set(false);
                scheduleCoordinationTask(tokenClaimInterval);
                return;
            }

            try {
                coordinateWorkPackages();
                errorWaitBackOff = 500;
                processingGate.set(false);

                if (isSpaceAvailable() && stream.hasNextAvailable()) {
                    // All work package have space available to handle events and there are still events on the stream.
                    // We should thus start this process again immediately.
                    // It will likely jump all the if-statement directly, thus initiating the reading of events ASAP.
                    startCoordinationTask();
                } else if (isSpaceAvailable()) {
                    // There is space, but no events to process. We caught up.
                    workPackages.keySet().forEach(i -> processingStatusUpdater.accept(i, TrackerStatus::caughtUp));

                    if (!availabilityCallbackSupported) {
                        scheduleCoordinationTask(500);
                    } else {
                        scheduleDelayedCoordinationTask(tokenClaimInterval);
                    }
                } else {
                    scheduleCoordinationTask(100);
                }
            } catch (Exception e) {
                abortAndScheduleRetry(e);
            }
        }

        private CompletableFuture<Void> abortWorkPackages(Exception cause) {
            return workPackages.values().stream()
                               .map(wp -> abortWorkPackage(wp, cause))
                               .reduce(CompletableFuture::allOf)
                               .orElse(CompletableFuture.completedFuture(null))
                               .thenRun(workPackages::clear);
        }

        private TrackingToken startWorkPackages() {
            return transactionManager.fetchInTransaction(() -> {
                int[] segments = tokenStore.fetchSegments(name);
                TrackingToken lowerBound = NoToken.INSTANCE;
                for (int segmentId : segments) {
                    if (!workPackages.containsKey(segmentId)) {
                        try {
                            TrackingToken token = tokenStore.fetchToken(name, segmentId);
                            logger.debug("Token for {} segment {} claimed: [{}]. Preparing work package.",
                                         name, segmentId, token);
                            Segment segment = Segment.computeSegment(segmentId, segments);
                            WorkPackage workPackage = workPackages.computeIfAbsent(
                                    segmentId, k -> workPackageFactory.apply(segment, token)
                            );
                            lowerBound = lowerBound == null
                                    ? null
                                    : lowerBound.lowerBound(workPackage.lastDeliveredToken());
                        } catch (UnableToClaimTokenException e) {
                            logger.debug("Unable to claim the token for segment[{}]. It is owned by another process.",
                                         segmentId);
                        }
                    } else {
                        logger.debug("Not need to fetch token for segment [{}]", segmentId);
                        // TODO: 26-01-21 Shouldn't we adjust the base token here too?
                        //  What if the new found segments are all ahead of the current packages?
                    }
                }
                return lowerBound;
            });
        }

        private void openStream(TrackingToken baseToken) {
            if (!NoToken.INSTANCE.equals(baseToken)) { // We calculated a proper token to open the stream from
                if (stream != null) { // We already had a stream open, so let's close the old one
                    // TODO: 26-01-21 should this ever happen?
                    // Yes,, if we have claimed new tokens
                    stream.close();
                    stream = null;
                }

                try {
                    // TODO: 26-01-21 store last read token, to make a nice lowerbound
                    // Make this lowerbound check
                    stream = messageSource.openStream(baseToken);
                    this.availabilityCallbackSupported = stream.setOnAvailableCallback(this::startCoordinationTask);
                    if (!availabilityCallbackSupported) {
                        startCoordinationTask();
                    } else {
                        scheduleDelayedCoordinationTask(tokenClaimInterval);
                    }
                } catch (Exception e) {
                    abortAndScheduleRetry(e);
                }
            } else {
                logger.debug("Coordinator [{}] is not in charge of any segments. Will retry in {} milliseconds.",
                             name, tokenClaimInterval);
            }
        }

        private boolean isSpaceAvailable() {
            return workPackages.values().stream()
                               .allMatch(WorkPackage::hasRemainingCapacity);
        }

        /**
         * Start coordinating work to the {@link WorkPackage}s. This means retrieving events from the {@link
         * StreamableMessageSource}, schedule these events to all the {@code WorkPackage}s and finally validate if any
         * of the {@code WorkPackage}s aborted their work. If any aborted their work, this {@link Coordinator} will
         * abandon the {@code WorkPackage} and release the claim on the token.
         *
         * @throws InterruptedException from {@link StreamableMessageSource#openStream(TrackingToken)}
         */
        private void coordinateWorkPackages() throws InterruptedException {
            logger.debug("Coordinator [{}] is coordinating work to all its work packages.", name);
            int maxToFetch = Math.max(batchSize * 10, MAX_EVENT_TO_FETCH);
            for (int fetched = 0; fetched < maxToFetch && isSpaceAvailable() && stream.hasNextAvailable(); fetched++) {
                TrackedEventMessage<?> event = stream.nextAvailable();
                for (WorkPackage workPackage : workPackages.values()) {
                    workPackage.scheduleEvent(event);
                }
            }

            // If a work package has been aborted by something else than the Coordinator. We should abandon it.
            workPackages.values().stream()
                        .filter(WorkPackage::isAbortTriggered)
                        .forEach(workPackage -> abortWorkPackage(workPackage, null));
        }

        private void startCoordinationTask() {
            scheduleCoordinationTask(0);
        }

        private void scheduleCoordinationTask(long delay) {
            if (scheduledGate.compareAndSet(false, true)) {
                executorService.schedule(() -> {
                    scheduledGate.set(false);
                    this.run();
                }, delay, TimeUnit.MILLISECONDS);
            }
        }

        private void scheduleDelayedCoordinationTask(long delay) {
            // We only want to schedule a delayed task if there isn't another delayed task scheduled,
            // and preferably not if a regular task has already been scheduled (hence just a get() for that flag)
            if (!scheduledGate.get() && interruptibleScheduledGate.compareAndSet(false, true)) {
                executorService.schedule(() -> {
                    interruptibleScheduledGate.set(false);
                    this.run();
                }, delay, TimeUnit.MILLISECONDS);
            }
        }

        private void abortAndScheduleRetry(Exception cause) {
            logger.warn(
                    "Exception occurred while trying to fetch events for processing. "
                            + "Releasing claims and rescheduling for processing in {}ms",
                    errorWaitBackOff, cause
            );

            errorWaitBackOff = Math.min(errorWaitBackOff * 2, 60000);
            abortWorkPackages(cause).thenRun(
                    () -> {
                        logger.debug(
                                "Work packages have aborted. Scheduling new fetcher to run in {}ms", errorWaitBackOff
                        );
                        executorService.schedule(new CoordinatorTask(), errorWaitBackOff, TimeUnit.MILLISECONDS);
                    }
            );
            IOUtils.closeQuietly(stream);
        }

        private CompletableFuture<Void> abortWorkPackage(WorkPackage work, Exception cause) {
            return work.abort(cause)
                       .thenAccept(e -> logger.debug(
                               "Worker [{}] has aborted. Releasing claim.", work.getSegment().getSegmentId(), e
                       ))
                       .thenRun(() -> workPackages.remove(work.getSegment().getSegmentId(), work))
                       .thenRun(() -> transactionManager.executeInTransaction(
                               () -> tokenStore.releaseClaim(name, work.getSegment().getSegmentId())
                       ));
        }
    }

    /**
     * A {@link TrackingToken} implementation used to define no token could be claimed by this {@link Coordinator}.
     */
    private static class NoToken implements TrackingToken {

        public static final TrackingToken INSTANCE = new NoToken();

        @Override
        public TrackingToken lowerBound(TrackingToken other) {
            return other;
        }

        @Override
        public TrackingToken upperBound(TrackingToken other) {
            return other;
        }

        @Override
        public boolean covers(TrackingToken other) {
            return false;
        }
    }
}
