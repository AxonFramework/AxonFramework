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
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.axonframework.eventhandling.WrappedToken.unwrapLowerBound;

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
    // not sure if this is accurate, but it's as close as we can get
    private static final Instant BIG_BANG = Instant.MIN;

    private final String name;
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;
    private final ScheduledExecutorService executorService;
    private final BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory;
    private final EventFilter eventFilter;
    private final Consumer<? super TrackedEventMessage<?>> ignoredMessageHandler;
    private final BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater;
    private final long tokenClaimInterval;
    private final Clock clock;

    private final Map<Integer, WorkPackage> workPackages = new ConcurrentHashMap<>();
    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.initial());
    private final Map<Integer, Instant> releasesDeadlines = new ConcurrentHashMap<>();
    private int errorWaitBackOff = 500;
    private final Queue<CoordinatorTask> coordinatorTasks = new ConcurrentLinkedQueue<>();
    private final AtomicReference<CoordinationTask> coordinationTask = new AtomicReference<>();

    /**
     * Instantiate a Builder to be able to create a {@link Coordinator}. This builder <b>does not</b> validate the
     * fields. Hence any fields provided should be validated by the user of the {@link Builder}.
     *
     * @return a Builder to be able to create a {@link Coordinator}
     */
    protected static Builder builder() {
        return new Builder();
    }

    private Coordinator(Builder builder) {
        this.name = builder.name;
        this.messageSource = builder.messageSource;
        this.tokenStore = builder.tokenStore;
        this.transactionManager = builder.transactionManager;
        this.executorService = builder.executorService;
        this.workPackageFactory = builder.workPackageFactory;
        this.eventFilter = builder.eventFilter;
        this.ignoredMessageHandler = builder.ignoredMessageHandler;
        this.processingStatusUpdater = builder.processingStatusUpdater;
        this.tokenClaimInterval = builder.tokenClaimInterval;
        this.clock = builder.clock;
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
                CoordinationTask task = new CoordinationTask();
                executorService.submit(task);
                this.coordinationTask.set(task);
            } catch (Exception e) {
                // A failure starting the processor. We need to stop immediately.
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
        CompletableFuture<Void> handle = runState.updateAndGet(RunState::attemptStop)
                                                 .shutdownHandle();
        CoordinationTask task = coordinationTask.getAndSet(null);
        if (task != null) {
            task.scheduleImmediateCoordinationTask();
        }
        return handle;
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
     * Schedules the Coordinator for processing. Use this method after assigning tasks to or setting flags on the
     * coordinator to have it respond to those changes.
     */
    private void scheduleCoordinator() {
        CoordinationTask coordinator = coordinationTask.get();
        if (coordinator != null) {
            coordinator.scheduleImmediateCoordinationTask();
        }
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
     * Instructs this coordinator to release the segment with the given {@code segmentId}. Furthermore, it will be
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
        logger.debug("Coordinator [{}] will release segment [{}] for processing until {}.",
                     name, segmentId, releaseDuration);
        releasesDeadlines.put(segmentId, releaseDuration);
        scheduleCoordinator();
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
        scheduleCoordinator();
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
        scheduleCoordinator();
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
                logger.debug("Stopped processing. Runnable flag is false.\n"
                                     + "Releasing claims and closing the event stream for processor [{}].", name);
                abortWorkPackages(null).thenRun(() -> runState.get().shutdownHandle().complete(null));
                IOUtils.closeQuietly(eventStream);
                return;
            }

            workPackages.entrySet().stream()
                        .filter(entry -> isSegmentBlockedFromClaim(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .forEach(workPackage -> abortWorkPackage(workPackage, null));

            if (!coordinatorTasks.isEmpty()) {
                CoordinatorTask task = coordinatorTasks.remove();
                logger.debug("Coordinator [{}] found a task [{}] to run.", name, task.getDescription());
                task.run()
                    .thenRun(() -> unclaimedSegmentValidationThreshold = 0)
                    .whenComplete((result, exception) -> {
                        processingGate.set(false);
                        scheduleImmediateCoordinationTask();
                    });
                return;
            }

            if (eventStream == null || unclaimedSegmentValidationThreshold <= clock.instant().toEpochMilli()) {
                unclaimedSegmentValidationThreshold = clock.instant().toEpochMilli() + tokenClaimInterval;

                try {
                    TrackingToken newWorkToken = startNewWorkPackages();
                    openStream(constructLowerBound(newWorkToken));
                } catch (Exception e) {
                    logger.warn("Exception occurred while Coordinator [{}] starting work packages"
                                        + " and opening the event stream.", name);
                    abortAndScheduleRetry(e);
                    return;
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
                // As segments are used for Segment#computeSegment, we cannot filter out the WorkPackages upfront.
                int[] unClaimedSegments = Arrays.stream(segments)
                                                .filter(segmentId -> !workPackages.containsKey(segmentId))
                                                .toArray();
                TrackingToken lowerBound = NoToken.INSTANCE;
                for (int segmentId : unClaimedSegments) {
                    if (isSegmentBlockedFromClaim(segmentId)) {
                        logger.debug("Segment [{}] is still marked to not be claimed by this coordinator.", segmentId);
                        processingStatusUpdater.accept(segmentId, u -> null);
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
                                : lowerBound.lowerBound(unwrapLowerBound(workPackage.lastDeliveredToken()));
                        releasesDeadlines.remove(segmentId);
                    } catch (UnableToClaimTokenException e) {
                        processingStatusUpdater.accept(segmentId, u -> null);
                        logger.debug("Unable to claim the token for segment[{}]. It is owned by another process.",
                                     segmentId);
                    }
                }
                return lowerBound;
            });
        }

        private boolean isSegmentBlockedFromClaim(int segmentId) {
            return releasesDeadlines.getOrDefault(segmentId, BIG_BANG)
                                    .isAfter(clock.instant());
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

            // We already had a stream, thus we started new WorkPackages. Close old stream to start at the new position.
            if (eventStream != null) {
                logger.debug("Coordinator [{}] will close the current stream, to be reopened with token [{}].",
                             name, trackingToken);
                eventStream.close();
                eventStream = null;
            }

            eventStream = messageSource.openStream(trackingToken);
            logger.debug("Coordinator [{}] opened stream with tracking token [{}].", name, trackingToken);
            availabilityCallbackSupported = eventStream.setOnAvailableCallback(this::scheduleImmediateCoordinationTask);
        }

        private boolean isSpaceAvailable() {
            return workPackages.values().stream()
                               .allMatch(WorkPackage::hasRemainingCapacity);
        }

        /**
         * Start coordinating work to the {@link WorkPackage}s. This firstly means retrieving events from the {@link
         * StreamableMessageSource}, check whether the event can be handled by any of them and if so, schedule these
         * events to all the {@code WorkPackage}s. The {@code WorkPackage}s will state whether they''ll actually handle
         * the event through their response on {@link WorkPackage#scheduleEvent(TrackedEventMessage)}. If none of the
         * {@code WorkPackage}s can handle the event it will be ignored.
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

                if (!eventFilter.canHandleTypeOf(event)) {
                    eventStream.skipMessagesWithPayloadTypeOf(event);
                    ignoredMessageHandler.accept(event);
                } else {
                    boolean anyScheduled = false;
                    for (WorkPackage workPackage : workPackages.values()) {
                        boolean scheduled = workPackage.scheduleEvent(event);
                        anyScheduled = anyScheduled || scheduled;
                    }
                    if (!anyScheduled) {
                        ignoredMessageHandler.accept(event);
                    }
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
            logger.warn("Releasing claims and scheduling a new coordination task in {}ms", errorWaitBackOff, cause);

            errorWaitBackOff = Math.min(errorWaitBackOff * 2, 60000);
            abortWorkPackages(cause).thenRun(
                    () -> {
                        logger.debug("Work packages have aborted. Scheduling new Coordination task to run in {}ms",
                                     errorWaitBackOff);
                        // Construct a new CoordinationTask, thus abandoning the old task and it's progress entirely.
                        CoordinationTask task = new CoordinationTask();
                        executorService.schedule(task, errorWaitBackOff, TimeUnit.MILLISECONDS);
                        coordinationTask.set(task);
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

    /**
     * Functional interface defining a validation if a given {@link TrackedEventMessage} can be handled by all {@link
     * WorkPackage}s this {@link Coordinator} could ever service.
     */
    @FunctionalInterface
    interface EventFilter {

        /**
         * Checks whether the given {@code eventMessage} contains a type of message that can be handled by any of the
         * event handlers this processor coordinates.
         *
         * @param eventMessage the {@link TrackedEventMessage} to validate whether it can be handled
         * @return {@code true} if the processor contains a handler for given {@code eventMessage}'s type, {@code false}
         * otherwise
         */
        boolean canHandleTypeOf(TrackedEventMessage<?> eventMessage);
    }

    /**
     * Package private builder class to construct a {@link Coordinator}. Not used for validation of the fields as is the
     * case with most builders, but purely to clarify the construction of a {@code WorkPackage}.
     */
    static class Builder {

        private String name;
        private StreamableMessageSource<TrackedEventMessage<?>> messageSource;
        private TokenStore tokenStore;
        private TransactionManager transactionManager;
        private ScheduledExecutorService executorService;
        private BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory;
        private EventFilter eventFilter;
        private Consumer<? super TrackedEventMessage<?>> ignoredMessageHandler = i -> {
        };
        private BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater;
        private long tokenClaimInterval = 5000;
        private Clock clock = GenericEventMessage.clock;

        /**
         * The name of the processor this service coordinates for.
         *
         * @param name the name of the processor this service coordinates for
         * @return the current Builder instance, for fluent interfacing
         */
        Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * The source of events this coordinator should schedule per work package.
         *
         * @param messageSource the source of events this coordinator should schedule per work package
         * @return the current Builder instance, for fluent interfacing
         */
        Builder messageSource(StreamableMessageSource<TrackedEventMessage<?>> messageSource) {
            this.messageSource = messageSource;
            return this;
        }

        /**
         * The storage solution for {@link TrackingToken}s. Used to find and claim unclaimed segments for a processor.
         *
         * @param tokenStore the storage solution for {@link TrackingToken}s
         * @return the current Builder instance, for fluent interfacing
         */
        Builder tokenStore(TokenStore tokenStore) {
            this.tokenStore = tokenStore;
            return this;
        }

        /**
         * A {@link TransactionManager} used to invoke all {@link TokenStore} operations inside a transaction.
         *
         * @param transactionManager a {@link TransactionManager} used to invoke all {@link TokenStore} operations
         *                           inside a transaction
         * @return the current Builder instance, for fluent interfacing
         */
        Builder transactionManager(TransactionManager transactionManager) {
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * A {@link ScheduledExecutorService} used to run this coordinators tasks with.
         *
         * @param executorService a {@link ScheduledExecutorService} used to run this coordinators tasks with
         * @return the current Builder instance, for fluent interfacing
         */
        Builder executorService(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Factory method to construct a {@link WorkPackage} with.
         *
         * @param workPackageFactory factory method to construct a {@link WorkPackage} with
         * @return the current Builder instance, for fluent interfacing
         */
        Builder workPackageFactory(BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory) {
            this.workPackageFactory = workPackageFactory;
            return this;
        }

        /**
         * A {@link EventFilter} used to check whether {@link TrackedEventMessage} must be ignored by all {@link
         * WorkPackage}s.
         *
         * @param eventFilter a {@link EventFilter} used to check whether {@link TrackedEventMessage} must be ignored by
         *                    all {@link WorkPackage}s
         * @return the current Builder instance, for fluent interfacing
         */
        Builder eventFilter(EventFilter eventFilter) {
            this.eventFilter = eventFilter;
            return this;
        }


        /**
         * A {@link Consumer} of {@link TrackedEventMessage} that is invoked when the event is ignored by all {@link
         * WorkPackage}s this {@link Coordinator} controls. Defaults to a no-op.
         *
         * @param ignoredMessageHandler lambda that is invoked when the event is ignored by all {@link WorkPackage}s
         *                              this {@link Coordinator} controls
         * @return the current Builder instance, for fluent interfacing
         */
        Builder onMessageIgnored(Consumer<? super TrackedEventMessage<?>> ignoredMessageHandler) {
            this.ignoredMessageHandler = ignoredMessageHandler;
            return this;
        }

        /**
         * Lambda used to update the processing {@link TrackerStatus} per {@link WorkPackage}
         *
         * @param processingStatusUpdater lambda used to update the processing {@link TrackerStatus} per {@link
         *                                WorkPackage}
         * @return the current Builder instance, for fluent interfacing
         */
        Builder processingStatusUpdater(BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater) {
            this.processingStatusUpdater = processingStatusUpdater;
            return this;
        }

        /**
         * The time in milliseconds this coordinator will wait to reattempt claiming segments for processing.  Defaults
         * to {@code 5000}.
         *
         * @param tokenClaimInterval the time in milliseconds this coordinator will wait to reattempt claiming segments
         *                           for processing
         * @return the current Builder instance, for fluent interfacing
         */
        Builder tokenClaimInterval(long tokenClaimInterval) {
            this.tokenClaimInterval = tokenClaimInterval;
            return this;
        }

        /**
         * The {@link Clock} used for any time dependent operations in this {@link Coordinator}. For example used to
         * define when to attempt claiming new tokens. Defaults to {@link GenericEventMessage#clock}.
         *
         * @param clock a {@link Clock} used for any time dependent operations in this {@link Coordinator}
         * @return the current Builder instance, for fluent interfacing
         */
        Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Initializes a {@link Coordinator} as specified through this Builder.
         *
         * @return a {@link Coordinator} as specified through this Builder
         */
        Coordinator build() {
            return new Coordinator(this);
        }
    }
}
