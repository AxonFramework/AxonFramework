package org.axonframework.eventhandling.pooled;

import org.axonframework.common.io.IOUtils;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.GenericEventMessage;
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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * @see WorkPackage
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
    private final long tokenClaimInterval;

    private final Map<Integer, WorkPackage> workPackages = new ConcurrentHashMap<>();
    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.initial());
    private final ConcurrentMap<Integer, Instant> releasesDeadlines = new ConcurrentHashMap<>();
    private int errorWaitBackOff = 500;
    private final Queue<CoordinatorTask> coordinatorTasks = new ConcurrentLinkedQueue<>();

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
                       long tokenClaimInterval) {
        this.name = name;
        this.messageSource = messageSource;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
        this.workPackageFactory = workPackageFactory;
        this.executorService = executorService;
        this.processingStatusUpdater = processingStatusUpdater;
        this.tokenClaimInterval = tokenClaimInterval;
    }

    /**
     * Start the event coordination task of this coordinator. Will shutdown this service immediately if the coordination
     * task cannot be started.
     */
    public void start() {
        RunState newState = this.runState.updateAndGet(RunState::attemptStart);
        if (newState.wasStarted()) {
            logger.info("Starting Coordinator for processor [{}].", name);
            try {
                executorService.submit(new CoordinationTask());
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
        logger.info("Coordinator [{}] will disregard segment [{}] for processing until {}.",
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
     * Instructs this coordinator to split the segment for the given {@code segmentId}.
     * <p>
     * If this coordinator is currently in charge of the specified segment, the {@link WorkPackage} will be aborted and
     * subsequently its segment split. When this coordinator is not in charge of the specified {@code segmentId}, it
     * will try to claim the segment's {@link TrackingToken} and then split it.
     * <p>
     * In either way, the segment's claim (if present) will be released, so that another thread can proceed with
     * processing it.
     *
     * @param segmentId the identifier of the segment to split
     * @return a {@link CompletableFuture} providing the result of the split operation
     */
    public CompletableFuture<Boolean> splitSegment(int segmentId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        coordinatorTasks.add(new SplitTask(result, name, segmentId, workPackages, tokenStore, transactionManager));
        return result;
    }

    /**
     * Instructs this coordinator to merge the segment for the given {@code segmentId}.
     * <p>
     * If this coordinator is currently in charge of the {@code segmentId} and the segment to merge it with, both {@link
     * WorkPackage}s will be aborted, after which the merge will start. When this coordinator is not in charge of one of
     * the two segments, it will try to claim either segment's {@link TrackingToken} and perform the merge then.
     * <p>
     * In either approach, this operation will delete one of the segments and release the claim on the other, so that
     * another thread can proceed with processing it.
     *
     * @param segmentId the identifier of the segment to merge
     * @return a {@link CompletableFuture} indicating whether the merge was executed successfully
     */
    public CompletableFuture<Boolean> mergeSegment(int segmentId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        coordinatorTasks.add(new MergeTask(result, name, segmentId, workPackages, tokenStore, transactionManager));
        return result;
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
     * A {@link Runnable} defining the entire coordination process dealt with by a {@link Coordinator}. This task will
     * reschedule itself on various occasions, as long as the states of the coordinator is running. Coordinating in this
     * sense means:
     * <ol>
     *     <li>Validating if there are {@link CoordinatorTask}s to run, and run a single one if there are any.</li>
     *     <li>Periodically checking for unclaimed segments, claim these and start a {@link WorkPackage} per claim.</li>
     *     <li>(Re)Opening an Event stream based on the lower bound token of all active {@code WorkPackages}.</li>
     *     <li>Reading events from the stream.</li>
     *     <li>Scheduling read events for each {@code WorkPackage} through {@link WorkPackage#scheduleEvent(TrackedEventMessage)}.</li>
     *     <li>Releasing claims of aborted {@code WorkPackages}.</li>
     *     <li>Rescheduling itself to be picked up at a reasonable point in time.</li>
     * </ol>
     */
    private class CoordinationTask implements Runnable {

        private final AtomicBoolean processingGate = new AtomicBoolean();
        private final AtomicBoolean scheduledGate = new AtomicBoolean();
        private final AtomicBoolean interruptibleScheduledGate = new AtomicBoolean();
        private BlockingStream<TrackedEventMessage<?>> eventStream;
        private TrackingToken lastScheduledToken = NoToken.INSTANCE;
        private boolean availabilityCallbackSupported;
        private long unclaimedSegmentValidationThreshold;

        @Override
        public void run() {
            if (!processingGate.compareAndSet(false, true)) {
                // Another thread is already processing, so stop this invocation.
                return;
            }

            if (!runState.get().isRunning()) {
                logger.debug("Stopped processing. Runnable flag is false.");
                logger.debug("Releasing claims for processor [{}].", name);
                abortWorkPackages(null).thenRun(() -> runState.get().shutdownHandle().complete(null));
                return;
            }

            if (!coordinatorTasks.isEmpty()) {
                CoordinatorTask task = coordinatorTasks.remove();
                logger.debug("Coordinator [{}] found a task [{}] to run.", name, task.description());
                task.run()
                    .whenComplete((result, exception) -> {
                        processingGate.set(false);
                        scheduleImmediateCoordinationTask();
                    });
                return;
            }

            if (eventStream == null
                    || unclaimedSegmentValidationThreshold <= GenericEventMessage.clock.instant().toEpochMilli()) {
                unclaimedSegmentValidationThreshold =
                        GenericEventMessage.clock.instant().toEpochMilli() + tokenClaimInterval;

                try {
                    TrackingToken newWorkToken = startNewWorkPackages();
                    openStream(constructLowerBound(newWorkToken));
                } catch (Exception e) {
                    logger.warn("Exception occurred while Coordinator [{}] starting work packages"
                                        + " and defining the point to start in the event stream.", name);
                    abortAndScheduleRetry(e);
                }
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

                if (isSpaceAvailable() && eventStream.hasNextAvailable()) {
                    // All work package have space available to handle events and there are still events on the stream.
                    // We should thus start this process again immediately.
                    // It will likely jump all the if-statement directly, thus initiating the reading of events ASAP.
                    scheduleImmediateCoordinationTask();
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
                logger.warn("Exception occurred while Coordinator [{}] was coordinating the work packages.", name);
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

        /**
         * Start new {@link WorkPackage}s for each segment this task is able to claim for its processor.
         *
         * @return the lower bound {@link TrackingToken} based on all the newly fetched segments
         */
        private TrackingToken startNewWorkPackages() {
            return transactionManager.fetchInTransaction(() -> {
                int[] segments = tokenStore.fetchSegments(name);
                TrackingToken lowerBound = NoToken.INSTANCE;
                for (int segmentId : segments) {
                    if (shouldNotClaimSegment(segmentId)) {
                        logger.debug("Segment [{}] is still marked to not be claimed by this coordinator.", segmentId);
                        continue;
                    }
                    if (workPackages.containsKey(segmentId)) {
                        logger.debug("No need to fetch segment [{}] as it is already owned by coordinator [{}].",
                                     segmentId, name);
                        continue;
                    }

                    try {
                        TrackingToken token = tokenStore.fetchToken(name, segmentId);
                        logger.debug("Token for segment [{}] on processor [{}] claimed: [{}]. Preparing work package.",
                                     segmentId, name, token);
                        Segment segment = Segment.computeSegment(segmentId, segments);
                        WorkPackage workPackage =
                                workPackages.computeIfAbsent(segmentId, k -> workPackageFactory.apply(segment, token));
                        lowerBound = lowerBound == null
                                ? null
                                : lowerBound.lowerBound(workPackage.lastDeliveredToken());
                        releasesDeadlines.remove(segmentId);
                    } catch (UnableToClaimTokenException e) {
                        logger.debug("Unable to claim the token for segment[{}]. It is owned by another process.",
                                     segmentId);
                    }
                }
                return lowerBound;
            });
        }

        private boolean shouldNotClaimSegment(int segmentId) {
            return releasesDeadlines.containsKey(segmentId)
                    && releasesDeadlines.get(segmentId).isAfter(GenericEventMessage.clock.instant());
        }

        /**
         * Constructs the lower bound position out of the given {@code trackingToken} and the {@code
         * lastScheduledToken}. When the given token is a {@link NoToken}, invoking {@link
         * NoToken#lowerBound(TrackingToken)} ensure {@code lastScheduledToken} will be returned. When it is not a
         * {@code NoToken}, the lower bound operation on the {@code lastScheduledToken} will be the result.
         * <p>
         * This approach ensures that the {@link TrackingToken#lowerBound(TrackingToken)} operations is performed on
         * tokens for which the lower bound can be calculated between them.
         *
         * @param trackingToken the {@link TrackingToken} compared with the {@code lastScheduledToken} to calculate the
         *                      lower bound
         * @return the lower bound position from the given {@code trackingToken} and the {@code lastScheduledToken}
         */
        private TrackingToken constructLowerBound(TrackingToken trackingToken) {
            return NoToken.INSTANCE.equals(trackingToken)
                    ? trackingToken.lowerBound(lastScheduledToken)
                    : lastScheduledToken.lowerBound(trackingToken);
        }

        private void openStream(TrackingToken trackingToken) {
            if (NoToken.INSTANCE.equals(trackingToken)) {
                logger.debug("Coordinator [{}] is not in charge of any segments. Will retry in {} milliseconds.",
                             name, tokenClaimInterval);
                return;
            }

            try {
                // We already had a stream, thus we started new WorkPackages. Close old stream to start at the new position.
                if (eventStream != null) {
                    logger.debug("Coordinator [{}] will close the current stream, to be reopened with token [{}].",
                                 name, trackingToken);
                    eventStream.close();
                    eventStream = null;
                }

                eventStream = messageSource.openStream(trackingToken);
                logger.debug("Coordinator [{}] opened stream with tracking token [{}].", name, trackingToken);
                availabilityCallbackSupported = eventStream.setOnAvailableCallback(this::startCoordinationTask);
            } catch (Exception e) {
                logger.warn("Exception occurred while Coordinator [{}] tried to close/open an Event Stream.", name);
                abortAndScheduleRetry(e);
            }
        }

        private boolean isSpaceAvailable() {
            return workPackages.values().stream()
                               .allMatch(WorkPackage::hasRemainingCapacity);
        }

        /**
         * Start coordinating work to the {@link WorkPackage}s. This firstly means retrieving events from the {@link
         * StreamableMessageSource}, schedule these events to all the {@code WorkPackage}s.
         * <p>
         * Secondly, the {@code WorkPackage}s are checked if they are aborted. If any are aborted, this {@link
         * Coordinator} will abandon the {@code WorkPackage} and release the claim on the token.
         * <p>
         * Lastly, the {@link WorkPackage#scheduleWorker()} method is invoked. This ensures the {@code WorkPackage}s
         * will keep their claim on their {@link TrackingToken} even if no events have been scheduled.
         *
         * @throws InterruptedException from {@link StreamableMessageSource#openStream(TrackingToken)}
         */
        private void coordinateWorkPackages() throws InterruptedException {
            logger.debug("Coordinator [{}] is coordinating work to all its work packages.", name);
            for (int fetched = 0;
                 fetched < WorkPackage.BUFFER_SIZE && isSpaceAvailable() && eventStream.hasNextAvailable();
                 fetched++) {
                TrackedEventMessage<?> event = eventStream.nextAvailable();
                for (WorkPackage workPackage : workPackages.values()) {
                    workPackage.scheduleEvent(event);
                }
                lastScheduledToken = event.trackingToken();
            }

            // If a work package has been aborted by something else than the Coordinator. We should abandon it.
            workPackages.values().stream()
                        .filter(WorkPackage::isAbortTriggered)
                        .forEach(workPackage -> abortWorkPackage(workPackage, null));

            // Chances are no events were scheduled at all. Scheduling regardless will ensure the token claim is held.
            workPackages.values()
                        .forEach(WorkPackage::scheduleWorker);
        }

        private void scheduleImmediateCoordinationTask() {
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
            logger.warn("Releasing claims and rescheduling the coordination task in {}ms", errorWaitBackOff, cause);

            errorWaitBackOff = Math.min(errorWaitBackOff * 2, 60000);
            abortWorkPackages(cause).thenRun(
                    () -> {
                        logger.debug(
                                "Work packages have aborted. Scheduling new fetcher to run in {}ms", errorWaitBackOff
                        );
                        executorService.schedule(new CoordinationTask(), errorWaitBackOff, TimeUnit.MILLISECONDS);
                    }
            );
            IOUtils.closeQuietly(eventStream);
        }

        private CompletableFuture<Void> abortWorkPackage(WorkPackage work, Exception cause) {
            return work.abort(cause)
                       .thenAccept(e -> logger.debug(
                               "Worker [{}] has aborted. Releasing claim.", work.segment().getSegmentId(), e
                       ))
                       .thenRun(() -> workPackages.remove(work.segment().getSegmentId(), work))
                       .thenRun(() -> transactionManager.executeInTransaction(
                               () -> tokenStore.releaseClaim(name, work.segment().getSegmentId())
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
