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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.WrappedToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.TrackingTokenSource;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.axonframework.common.FutureUtils.emptyCompletedFuture;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.axonframework.common.ProcessUtils.executeUntilTrue;

/**
 * Coordinator for the {@link PooledStreamingEventProcessor}. Uses coordination tasks (separate threads) to starts a
 * work package for every {@link TrackingToken} it is able to claim. The tokens for every work package are combined and
 * the lower bound of this combined token is used to open an event stream from a {@link StreamableEventSource}. Events
 * are scheduled one by one to <em>all</em> work packages coordinated by this service.
 * <p>
 * Coordination tasks will run and be rerun as long as this service is considered to be {@link #isRunning()}.
 * Coordination will continue whenever exceptions occur, albeit with an incremental back off. Due to this, both
 * {@link #isError()} and {@link #isRunning()} can result in {@code true} at the same time.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @see PooledStreamingEventProcessor
 * @see WorkPackage
 * @since 4.5
 */
class Coordinator {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final StreamableEventSource<? extends EventMessage<?>> eventSource;
    private final TokenStore tokenStore;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final ScheduledExecutorService executorService;
    private final BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory;
    private final Consumer<? super EventMessage<?>> ignoredMessageHandler;
    private final BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater;
    private final long tokenClaimInterval;
    private final long claimExtensionThreshold;
    private final Clock clock;
    private final MaxSegmentProvider maxSegmentProvider;
    private final int initialSegmentCount;
    private final Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken;
    private final boolean coordinatorExtendsClaims;
    private final Consumer<Segment> segmentReleasedAction;
    private final EventCriteria eventCriteria;

    private final Map<Integer, WorkPackage> workPackages = new ConcurrentHashMap<>();
    private final AtomicReference<RunState> runState;
    private final Map<Integer, Instant> releasesDeadlines = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> releasesLastBackOffSeconds = new ConcurrentHashMap<>();
    private int errorWaitBackOff = 500;
    private final Queue<CoordinatorTask> coordinatorTasks = new ConcurrentLinkedQueue<>();
    private final AtomicReference<CoordinationTask> coordinationTask = new AtomicReference<>();

    /**
     * Instantiate a Builder to be able to create a {@link Coordinator}. This builder <b>does not</b> validate the
     * fields. Hence, any fields provided should be validated by the user of the {@link Builder}.
     *
     * @return a Builder to be able to create a {@link Coordinator}
     */
    protected static Builder builder() {
        return new Builder();
    }

    private Coordinator(Builder builder) {
        this.name = builder.name;
        this.eventSource = builder.eventSource;
        this.tokenStore = builder.tokenStore;
        this.unitOfWorkFactory = builder.unitOfWorkFactory;
        this.executorService = builder.executorService;
        this.workPackageFactory = builder.workPackageFactory;
        this.ignoredMessageHandler = builder.ignoredMessageHandler;
        this.processingStatusUpdater = builder.processingStatusUpdater;
        this.tokenClaimInterval = builder.tokenClaimInterval;
        this.claimExtensionThreshold = builder.claimExtensionThreshold;
        this.clock = builder.clock;
        this.maxSegmentProvider = builder.maxSegmentProvider;
        this.initialSegmentCount = builder.initialSegmentCount;
        this.initialToken = builder.initialToken;
        this.runState = new AtomicReference<>(RunState.initial(builder.shutdownAction));
        this.coordinatorExtendsClaims = builder.coordinatorExtendsClaims;
        this.segmentReleasedAction = builder.segmentReleasedAction;
        this.eventCriteria = builder.eventCriteria;
    }

    /**
     * Start the event coordination task of this coordinator. Will shutdown this service immediately if the coordination
     * task cannot be started.
     */
    public void start() {
        RunState newState = this.runState.updateAndGet(RunState::attemptStart);
        if (newState.wasStarted()) {
            logger.debug("Starting Coordinator for Processor [{}].", name);
            try {
                executeUntilTrue(Coordinator.this::initializeTokenStore, 100L, 30L);
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
        logger.debug("Stopping Coordinator for Processor [{}].", name);
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
        logger.debug("Processor [{}] will release segment {} for processing until {}.",
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
        coordinatorTasks.add(new SplitTask(result, name, segmentId, workPackages, tokenStore, unitOfWorkFactory));
        scheduleCoordinator();
        return result;
    }

    /**
     * Instructs this coordinator to merge the segment for the given {@code segmentId}.
     * <p>
     * If this coordinator is currently in charge of the {@code segmentId} and the segment to merge it with, both
     * {@link WorkPackage}s will be aborted, after which the merge will start. When this coordinator is not in charge of
     * one of the two segments, it will try to claim either segment's {@link TrackingToken} and perform the merge then.
     * <p>
     * In either approach, this operation will delete one of the segments and release the claim on the other, so that
     * another thread can proceed with processing it.
     *
     * @param segmentId the identifier of the segment to merge
     * @return a {@link CompletableFuture} indicating whether the merge was executed successfully
     */
    public CompletableFuture<Boolean> mergeSegment(int segmentId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        coordinatorTasks.add(new MergeTask(result, name, segmentId, workPackages, tokenStore, unitOfWorkFactory));
        scheduleCoordinator();
        return result;
    }

    /**
     * Instructs this coordinator to claim the segment for the given {@code segmentId}.
     * <p>
     * If this coordinator is already processing the segment, it will have a successful result. If the segment's token
     * is currently taken by another processor instance, the result will be unsuccessful.
     * <p>
     * The token will immediately be claimed, but processing of the token will start after the invocation of this
     * instruction has been completed successfully by scheduling the coordinator. This will see the claimed token and
     * start a {@link WorkPackage} for it.
     *
     * @param segmentId the identifier of the segment to claim
     * @return a {@link CompletableFuture} indicating whether the claim was executed successfully
     */
    public CompletableFuture<Boolean> claimSegment(int segmentId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        coordinatorTasks.add(new ClaimTask(result,
                                           name,
                                           segmentId,
                                           workPackages,
                                           releasesDeadlines,
                                           tokenStore,
                                           unitOfWorkFactory));
        scheduleCoordinator();
        return result;
    }

    private boolean initializeTokenStore() {
        AtomicBoolean tokenStoreInitialized = new AtomicBoolean(false);
        try {
            joinAndUnwrap(
                    unitOfWorkFactory.create()
                                     .executeWithResult(context -> {
                                         int[] segments = tokenStore.fetchSegments(name);
                                         if (segments == null || segments.length == 0) {
                                             logger.info("Initializing segments for processor [{}] ({} segments)",
                                                         name,
                                                         initialSegmentCount);
                                             tokenStore.initializeTokenSegments(
                                                     name,
                                                     initialSegmentCount,
                                                     joinAndUnwrap(initialToken.apply(eventSource)));
                                         }
                                         tokenStoreInitialized.set(true);
                                         return emptyCompletedFuture();
                                     })
            );
        } catch (Exception e) {
            logger.info(
                    "Error while initializing the Token Store. This may simply indicate concurrent attempts to initialize.",
                    e);
        }
        return tokenStoreInitialized.get();
    }

    /**
     * Status holder for this service. Defines whether it is running, has been started (to ensure double
     * {@link #start()} invocations do not restart this coordinator) and maintains a shutdown handler to complete
     * asynchronously through {@link #stop()}.
     */
    private static class RunState {

        private final boolean isRunning;
        private final boolean wasStarted;
        private final CompletableFuture<Void> shutdownHandle;
        private final Runnable shutdownAction;

        private RunState(boolean isRunning,
                         boolean wasStarted,
                         CompletableFuture<Void> shutdownHandle,
                         Runnable shutdownAction) {
            this.isRunning = isRunning;
            this.wasStarted = wasStarted;
            this.shutdownHandle = shutdownHandle;
            this.shutdownAction = shutdownAction;
        }

        public static RunState initial(Runnable shutdownAction) {
            return new RunState(false, false, emptyCompletedFuture(), shutdownAction);
        }

        public RunState attemptStart() {
            if (isRunning) {
                // It was already started
                return new RunState(true, false, null, shutdownAction);
            } else if (shutdownHandle.isDone()) {
                // Shutdown has previously been completed. It's allowed to start
                return new RunState(true, true, null, shutdownAction);
            } else {
                // Shutdown is in progress
                return this;
            }
        }

        public RunState attemptStop() {
            // It's already stopped
            if (!isRunning || shutdownHandle != null) {
                return this;
            }
            CompletableFuture<Void> newShutdownHandle = new CompletableFuture<>();
            newShutdownHandle.whenComplete((r, e) -> shutdownAction.run());
            return new RunState(false, false, newShutdownHandle, shutdownAction);
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
     * Package private builder class to construct a {@link Coordinator}. Not used for validation of the fields as is the
     * case with most builders, but purely to clarify the construction of a {@code WorkPackage}.
     */
    static class Builder {

        private String name;
        private StreamableEventSource<? extends EventMessage<?>> eventSource;
        private TokenStore tokenStore;
        private UnitOfWorkFactory unitOfWorkFactory;
        private ScheduledExecutorService executorService;
        private BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory;
        private Consumer<? super EventMessage<?>> ignoredMessageHandler = i -> {
        };
        private BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater;
        private long tokenClaimInterval = 5000;
        private long claimExtensionThreshold = 5000;
        private Clock clock = GenericEventMessage.clock;
        private MaxSegmentProvider maxSegmentProvider;
        private int initialSegmentCount = 16;
        private Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken;
        private Runnable shutdownAction = () -> {
        };
        private boolean coordinatorExtendsClaims = false;
        private Consumer<Segment> segmentReleasedAction = segment -> {
        };
        private EventCriteria eventCriteria = EventCriteria.havingAnyTag();

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
         * @param eventSource The source of events this coordinator should schedule per work package.
         * @return The current Builder instance, for fluent interfacing.
         */
        Builder eventSource(StreamableEventSource<? extends EventMessage<?>> eventSource) {
            this.eventSource = eventSource;
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
         * A {@link UnitOfWorkFactory} that spawns {@link org.axonframework.messaging.unitofwork.UnitOfWork} used to
         * invoke all {@link TokenStore} operations inside a unit of work.
         *
         * @param unitOfWorkFactory a {@link UnitOfWorkFactory} that spawns
         *                          {@link org.axonframework.messaging.unitofwork.UnitOfWork} used to invoke all
         *                          {@link TokenStore} operations inside a unit of work
         * @return the current Builder instance, for fluent interfacing
         */
        Builder unitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
            this.unitOfWorkFactory = unitOfWorkFactory;
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
         * A {@link Consumer} of {@link EventMessage} that is invoked when the event is ignored by all
         * {@link WorkPackage}s this {@link Coordinator} controls. Defaults to a no-op.
         *
         * @param ignoredMessageHandler lambda that is invoked when the event is ignored by all {@link WorkPackage}s
         *                              this {@link Coordinator} controls
         * @return the current Builder instance, for fluent interfacing
         */
        Builder onMessageIgnored(Consumer<? super EventMessage<?>> ignoredMessageHandler) {
            this.ignoredMessageHandler = ignoredMessageHandler;
            return this;
        }

        /**
         * Lambda used to update the processing {@link TrackerStatus} per {@link WorkPackage}
         *
         * @param processingStatusUpdater lambda used to update the processing {@link TrackerStatus} per
         *                                {@link WorkPackage}
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
         * Sets the threshold after which workers should extend their claims if they haven't processed any messages.
         * Defaults to {@code 5000}.
         *
         * @param claimExtensionThreshold the threshold in milliseconds
         * @return the current Builder instance, for fluent interfacing
         */
        Builder claimExtensionThreshold(long claimExtensionThreshold) {
            this.claimExtensionThreshold = claimExtensionThreshold;
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
         * Sets the {@link MaxSegmentProvider} providing the maximum number of segments this instance may claim.
         *
         * @param maxSegmentProvider The {@link MaxSegmentProvider} providing the maximum number of segments this
         *                           instance may claim.
         * @return The current Builder instance, for fluent interfacing.
         */
        Builder maxSegmentProvider(MaxSegmentProvider maxSegmentProvider) {
            this.maxSegmentProvider = maxSegmentProvider;
            return this;
        }

        /**
         * Sets the initial segment count used to create segments on start up. Defaults to 16.
         *
         * @param initialSegmentCount an {@code int} specifying the initial segment count used to create segments on
         *                            start up
         * @return the current Builder instance, for fluent interfacing
         */
        Builder initialSegmentCount(int initialSegmentCount) {
            this.initialSegmentCount = initialSegmentCount;
            return this;
        }

        /**
         * Specifies the {@link Function} used to generate the initial {@link TrackingToken}s. Defaults to an automatic
         * replay since the start of the stream.
         * <p>
         * More specifically, it defaults to a {@link org.axonframework.eventhandling.ReplayToken} that starts streaming
         * from the {@link StreamableEventSource#latestToken() tail} with the replay flag enabled until the
         * {@link StreamableEventSource#firstToken() head} at the moment of initialization is reached.
         *
         * @param initialToken a {@link Function} generating the initial {@link TrackingToken} based on a given
         *                     {@link StreamableEventSource}
         * @return the current Builder instance, for fluent interfacing
         */
        Builder initialToken(
                Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken
        ) {
            this.initialToken = initialToken;
            return this;
        }

        /**
         * Registers an action to perform when the coordinator shuts down. Will override any previously registered
         * actions. Defaults to a no-op.
         *
         * @param shutdownAction the action to perform when the coordinator is shut down
         * @return the current Builder instance, for fluent interfacing
         */
        Builder onShutdown(Runnable shutdownAction) {
            this.shutdownAction = shutdownAction;
            return this;
        }

        /**
         * Enabled this coordinator to {@link WorkPackage#extendClaimIfThresholdIsMet() extend the claims} of its
         * {@link WorkPackage WorkPackages}. Defaults to {@code false}.
         *
         * @param coordinatorExtendsClaims A flag dictating whether this coordinator will
         *                                 {@link WorkPackage#extendClaimIfThresholdIsMet() extend claims}.
         * @return The current Builder instance, for fluent interfacing.
         */
        Builder coordinatorClaimExtension(boolean coordinatorExtendsClaims) {
            this.coordinatorExtendsClaims = coordinatorExtendsClaims;
            return this;
        }

        /**
         * Registers an action to perform when a segment is released. Will override any previously registered actions.
         * Defaults to a no-op.
         *
         * @param segmentReleasedAction the action to perform when a segment is released
         * @return the current Builder instance, for fluent interfacing
         */
        Builder segmentReleasedAction(Consumer<Segment> segmentReleasedAction) {
            this.segmentReleasedAction = segmentReleasedAction;
            return this;
        }

        /**
         * Sets the {@link EventCriteria} used to filter events when opening the event stream. This allows the
         * coordinator to only process events that match the specified criteria, reducing the amount of data processed
         * and potentially improving performance.
         * <p>
         * By default, this is set to {@link EventCriteria#havingAnyTag()}, which means all events are processed.
         * 
         * @param eventCriteria the {@link EventCriteria} to use for filtering events
         * @return the current Builder instance, for fluent interfacing
         */
        Builder eventCriteria(EventCriteria eventCriteria) {
            this.eventCriteria = eventCriteria;
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
     * A {@link Runnable} defining the entire coordination process dealt with by a {@link Coordinator}. This task will
     * reschedule itself on various occasions, as long as the states of the coordinator is running. Coordinating in this
     * sense means:
     * <ol>
     *     <li>Abort {@link WorkPackage WorkPackages} for which {@link #releaseUntil(int, Instant)} has been invoked.</li>
     *     <li>{@link WorkPackage#extendClaimIfThresholdIsMet() Extend the claims} of all {@code WorkPackages} to relieve them of this effort.
     *     This is an optimization activated through {@link Builder#coordinatorClaimExtension(boolean)}.</li>
     *     <li>Validating if there are {@link CoordinatorTask CoordinatorTasks} to run, and run a single one if there are any.</li>
     *     <li>Periodically checking for unclaimed segments, claim these and start a {@code WorkPackage} per claim.</li>
     *     <li>(Re)Opening an Event stream based on the lower bound token of all active {@code WorkPackages}.</li>
     *     <li>Reading events from the stream.</li>
     *     <li>Scheduling read events for each {@code WorkPackage} through {@link WorkPackage#scheduleEvent(MessageStream.Entry)}.</li>
     *     <li>Releasing claims of aborted {@code WorkPackages}.</li>
     *     <li>Rescheduling itself to be picked up at a reasonable point in time.</li>
     * </ol>
     */
    private class CoordinationTask implements Runnable {

        private final AtomicBoolean processingGate = new AtomicBoolean();
        private final AtomicBoolean scheduledGate = new AtomicBoolean();
        private final AtomicBoolean interruptibleScheduledGate = new AtomicBoolean();
        private MessageStream<? extends EventMessage<?>> eventStream;
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
                logger.debug(
                        "Stopped processing. Runnable flag is false.\nReleasing claims and closing the event stream for Processor [{}].",
                        name);
                abortWorkPackages(null).thenRun(() -> runState.get().shutdownHandle().complete(null));
                closeStreamQuietly();
                return;
            }

            // Abort WorkPackages for which releaseUntil() has been invoked
            workPackages.entrySet().stream()
                        .filter(entry -> isSegmentBlockedFromClaim(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .forEach(workPackage -> {
                            logger.info(
                                    "Processor [{}] was requested and will comply with releasing claim for segment {}.",
                                    name, workPackage.segment().getSegmentId()
                            );
                            abortWorkPackage(workPackage, null);
                        });

            if (coordinatorExtendsClaims) {
                logger.debug(
                        "Processor [{}] will extend the claim of work packages that are busy processing events and have met the claim threshold.",
                        name);
                // Extend the claims of each work package busy processing events.
                // Doing so relieves this effort from the work package as an optimization.
                workPackages.values()
                            .stream()
                            .filter(workPackage -> !workPackage.isAbortTriggered())
                            .filter(WorkPackage::isProcessingEvents)
                            .forEach(workPackage -> {
                                try {
                                    workPackage.extendClaimIfThresholdIsMet();
                                } catch (Exception e) {
                                    logger.warn("Error while extending claim for Work Package [{}]-[{}]. "
                                                        + "Aborting Work Package...",
                                                workPackage.segment().getSegmentId(), name, e);
                                    workPackage.abort(e);
                                }
                            });
            }

            if (!coordinatorTasks.isEmpty()) {
                // Process any available coordinator tasks.
                CoordinatorTask task = coordinatorTasks.remove();
                logger.debug("Processor [{}] found task [{}] to run.", name, task.getDescription());
                task.run()
                    .thenRun(() -> unclaimedSegmentValidationThreshold = 0)
                    .whenComplete((result, exception) -> {
                        processingGate.set(false);
                        scheduleImmediateCoordinationTask();
                    });
                return;
            }

            if (eventStream == null || unclaimedSegmentValidationThreshold <= clock.instant().toEpochMilli()) {
                // Claim new segments, construct work packages per new segment, and open stream based on lowest segment
                unclaimedSegmentValidationThreshold = clock.instant().toEpochMilli() + tokenClaimInterval;
                try {
                    TrackingToken streamStartPosition = lastScheduledToken;
                    if (!releaseSegmentsIfTooManyClaimed()) {
                        logger.debug("Processor [{}] will try to claim new segments.", name);
                        Map<Segment, TrackingToken> newSegments = claimNewSegments();

                        for (Map.Entry<Segment, TrackingToken> entry : newSegments.entrySet()) {
                            Segment segment = entry.getKey();
                            TrackingToken token = entry.getValue();
                            TrackingToken otherUnwrapped = WrappedToken.unwrapLowerBound(token);

                            streamStartPosition = streamStartPosition == null || otherUnwrapped == null
                                    ? null : streamStartPosition.lowerBound(otherUnwrapped);
                            workPackages.computeIfAbsent(segment.getSegmentId(),
                                                         wp -> createWorkPackage(segment, token));
                        }

                        if (logger.isInfoEnabled() && !newSegments.isEmpty()) {
                            logger.info("Processor [{}] claimed {} new segments for processing",
                                        name,
                                        newSegments.size());
                        }
                    }
                    ensureOpenStream(streamStartPosition);
                } catch (Exception e) {
                    logger.warn("Exception occurred while Processor [{}] started work packages"
                                        + " and opened the event stream.", name, e);
                    abortAndScheduleRetry(e);
                    return;
                }
            }

            if (workPackages.isEmpty()) {
                // We didn't start any work packages. Retry later.
                logger.debug("No segments claimed. Will retry in {} milliseconds.", tokenClaimInterval);
                lastScheduledToken = NoToken.INSTANCE;
                closeStreamQuietly();
                eventStream = null;
                processingGate.set(false);
                scheduleDelayedCoordinationTask(tokenClaimInterval);
                return;
            }

            try {
                // Coordinate events to work packages and reschedule this coordinator
                if (!hasNextEvent() && isDone()) {
                    workPackages.keySet().forEach(i -> processingStatusUpdater.accept(i, TrackerStatus::caughtUp));
                }
                coordinateWorkPackages();
                errorWaitBackOff = 500;
                processingGate.set(false);

                if (isSpaceAvailable() && hasNextEvent()) {
                    // All work package have space available to handle events and there are still events on the stream.
                    // We should thus start this process again immediately.
                    // It will likely jump all the if-statement directly, thus initiating the reading of events ASAP.
                    scheduleImmediateCoordinationTask();
                } else if (isSpaceAvailable()) {
                    if (!availabilityCallbackSupported) {
                        scheduleCoordinationTask(500);
                    } else {
                        scheduleDelayedCoordinationTask(Math.min(claimExtensionThreshold, tokenClaimInterval));
                    }
                } else {
                    scheduleCoordinationTask(100);
                }
            } catch (Exception e) {
                logger.warn("Exception occurred while Processor [{}] was coordinating the work packages.", name, e);
                if (e instanceof InterruptedException) {
                    logger.error("Processor [{}] was interrupted. Shutting down.", name, e);
                    stop();
                    Thread.currentThread().interrupt();
                } else {
                    abortAndScheduleRetry(e);
                }
            }
        }

        /**
         * Closes currently opened {@code eventStream}, while suppressing any Exceptions it will generate. The given
         * {@code eventStream} may be {@code null}, in which case nothing happens.
         */
        private void closeStreamQuietly() {
            if (eventStream != null) {
                try {
                    eventStream.close();
                } catch (Exception e) {
                    logger.debug("Exception occurred while closing event stream for Processor [{}].", name, e);
                }
            }
        }

        private WorkPackage createWorkPackage(Segment segment, TrackingToken token) {
            WorkPackage workPackage = workPackageFactory.apply(segment, token);
            workPackage.onBatchProcessed(() -> resetRetryExponentialBackoff(segment.getSegmentId()));
            return workPackage;
        }

        private void resetRetryExponentialBackoff(int segmentId) {
            releasesLastBackOffSeconds.compute(segmentId, (s, b) -> null);
            logger.debug("Processor [{}] reset release deadline backoff for Segment [#{}].",
                         name,
                         segmentId);
        }

        private CompletableFuture<Void> abortWorkPackages(Exception cause) {
            return workPackages.values().stream()
                               .map(wp -> abortWorkPackage(wp, cause))
                               .reduce(CompletableFuture::allOf)
                               .orElse(emptyCompletedFuture())
                               .thenRun(workPackages::clear);
        }

        /**
         * Compares the maximum number of segments that can be claimed by a node for a fair distribution of the segments
         * among available processor nodes. Releases extra segments claimed by this event processor instance to be
         * available for claim by other processors.
         *
         * @return {@code true} if segments were released, {@code false} otherwise.
         */
        private boolean releaseSegmentsIfTooManyClaimed() {
            int maxSegmentsPerNode = maxSegmentProvider.apply(name);
            boolean tooManySegmentsClaimed = workPackages.size() > maxSegmentsPerNode;
            if (tooManySegmentsClaimed) {
                logger.info("Total segments [{}] for processor [{}] is above maxSegmentsPerNode = [{}], "
                                    + "going to release surplus claimed segments.",
                            workPackages.size(), name, maxSegmentsPerNode);
                workPackages.values()
                            .stream()
                            .limit(workPackages.size() - maxSegmentsPerNode)
                            .forEach(workPackage -> releaseUntil(
                                    workPackage.segment().getSegmentId(),
                                    GenericEventMessage.clock.instant().plusMillis(tokenClaimInterval)
                            ));
            }
            return tooManySegmentsClaimed;
        }

        /**
         * Attempts to claim new segments.
         *
         * @return a Map with each {@link TrackingToken} for newly claimed {@link Segment}
         */
        private Map<Segment, TrackingToken> claimNewSegments() {
            Map<Segment, TrackingToken> newClaims = new HashMap<>();
            List<Segment> segments = joinAndUnwrap(
                    unitOfWorkFactory.create()
                                     .executeWithResult(context -> CompletableFuture.completedFuture(tokenStore.fetchAvailableSegments(
                                             name)))
            );
            // As segments are used for Segment#computeSegment, we cannot filter out the WorkPackages upfront.
            List<Segment> unClaimedSegments = segments.stream()
                                                      .filter(segment -> !workPackages.containsKey(segment.getSegmentId()))
                                                      .collect(Collectors.toList());
            int maxSegmentsToClaim = maxSegmentProvider.apply(name) - workPackages.size();
            for (Segment segment : unClaimedSegments) {
                int segmentId = segment.getSegmentId();
                if (isSegmentBlockedFromClaim(segmentId)) {
                    logger.debug("Segment {} is still marked to not be claimed by Processor [{}] till [{}].",
                                 segmentId,
                                 name,
                                 releasesDeadlines.get(segmentId));
                    processingStatusUpdater.accept(segmentId, u -> null);
                    continue;
                }
                if (newClaims.size() < maxSegmentsToClaim) {
                    try {
                        TrackingToken token = joinAndUnwrap(
                                unitOfWorkFactory.create()
                                                 .executeWithResult(context -> CompletableFuture.completedFuture(
                                                         tokenStore.fetchToken(name, segment)))
                        );
                        newClaims.put(segment, token);
                        logger.info("Processor [{}] claimed the token for segment {}.", name, segmentId);
                    } catch (UnableToClaimTokenException e) {
                        processingStatusUpdater.accept(segmentId, u -> null);
                        logger.debug("Processor [{}] is unable to claim the token for segment {}. "
                                             + "It is owned by another process or has been split/merged concurrently.",
                                     name, segmentId);
                    }
                }
            }

            return newClaims;
        }

        private boolean isSegmentBlockedFromClaim(int segmentId) {
            Instant releaseDeadline = releasesDeadlines.compute(
                    segmentId,
                    (i, current) -> current == null || clock.instant().isAfter(current) ? null : current
            );
            return releaseDeadline != null;
        }

        private void ensureOpenStream(TrackingToken trackingToken) {
            // We already had a stream and the token differs the last scheduled token, thus we started new WorkPackages.
            // Close old stream to start at the new position, if we have Work Packages left.
            if (eventStream != null && !Objects.equals(trackingToken, lastScheduledToken)) {
                logger.debug("Processor [{}] will close the current stream.", name);
                closeStreamQuietly();
                eventStream = null;
                lastScheduledToken = NoToken.INSTANCE;
            }

            if (eventStream == null && !workPackages.isEmpty() && !(trackingToken instanceof NoToken)) {
                var startStreamingFrom = Objects.requireNonNullElse(trackingToken, new GlobalSequenceTrackingToken(-1));
                eventStream = eventSource.open(StreamingCondition.conditionFor(startStreamingFrom, eventCriteria));
                logger.debug("Processor [{}] opened stream with tracking token [{}] and criteria [{}].", name, trackingToken, eventCriteria);
                availabilityCallbackSupported = true;
                eventStream.onAvailable(this::scheduleImmediateCoordinationTask);
                lastScheduledToken = trackingToken;
            }
        }

        private boolean isSpaceAvailable() {
            return workPackages.values().stream()
                               .allMatch(WorkPackage::hasRemainingCapacity);
        }

        private boolean isDone() {
            return workPackages.values().stream()
                               .allMatch(WorkPackage::isDone);
        }

        private MessageStream.Entry<? extends EventMessage<?>> nextEventOrNull() {
            if (eventStream == null) {
                return null;
            }
            return eventStream.next().orElse(null);
        }

        private boolean hasNextEvent() {
            return eventStream != null && eventStream.hasNextAvailable();
        }

        private boolean eventsEqualingLastScheduledToken(TrackingToken lastScheduledToken) {
            MessageStream.Entry<? extends EventMessage<?>> nextEntry = eventStream.peek().orElse(null);
            if (nextEntry == null || lastScheduledToken == null) {
                return false;
            }

            TrackingToken nextToken = TrackingToken.fromContext(nextEntry).orElse(null);
            return Objects.equals(lastScheduledToken, nextToken);
        }

        /**
         * Start coordinating work to the {@link WorkPackage}s. This firstly means retrieving events from the
         * {@link StreamableEventSource}, check whether the event can be handled by any of them and if so, schedule
         * these events to all the {@code WorkPackage}s. The {@code WorkPackage}s will state whether they'll actually
         * handle the event through their response on {@link WorkPackage#scheduleEvent(MessageStream.Entry)}. If none of
         * the {@code WorkPackage}s can handle the event it will be ignored.
         * <p>
         * Secondly, the {@code WorkPackage}s are checked if they are aborted. If any are aborted, this
         * {@link Coordinator} will abandon the {@code WorkPackage} and release the claim on the token.
         * <p>
         * Lastly, the {@link WorkPackage#scheduleWorker()} method is invoked. This ensures the {@code WorkPackage}s
         * will keep their claim on their {@link TrackingToken} even if no events have been scheduled.
         */
        private void coordinateWorkPackages() {
            logger.debug("Processor [{}] is coordinating work to all its work packages.", name);

            if (eventStream != null) {
                var streamError = eventStream.error();
                if (streamError.isPresent()) {
                    throw new RuntimeException("Event stream has an error", streamError.get());
                }
            }

            for (int fetched = 0;
                 fetched < WorkPackage.BUFFER_SIZE && isSpaceAvailable() && hasNextEvent();
                 fetched++) {
                MessageStream.Entry<? extends EventMessage<?>> eventEntry = nextEventOrNull();
                if (eventEntry == null) {
                    break; // No more events available
                }

                TrackingToken eventToken = TrackingToken.fromContext(eventEntry).orElse(null);
                lastScheduledToken = eventToken;

                // Make sure all subsequent events with the same token as the last are added as well.
                // These are the result of upcasting and should always be scheduled in one go.
                if (eventsEqualingLastScheduledToken(eventToken)) {
                    List<MessageStream.Entry<? extends EventMessage<?>>> eventEntries = new ArrayList<>();
                    eventEntries.add(eventEntry);
                    while (eventsEqualingLastScheduledToken(eventToken)) {
                        MessageStream.Entry<? extends EventMessage<?>> nextEntry = nextEventOrNull();
                        if (nextEntry != null) {
                            eventEntries.add(nextEntry);
                        } else {
                            break; // No more events available
                        }
                    }
                    offerEventsToWorkPackages(eventEntries);
                } else {
                    offerEventToWorkPackages(eventEntry);
                }
            }

            // If a work package has been aborted by something else than the Coordinator. We should abandon it.
            workPackages.values().stream()
                        .filter(WorkPackage::isAbortTriggered)
                        .forEach(workPackage -> {
                            advanceReleaseDeadlineFor(workPackage.segment().getSegmentId());
                            abortWorkPackage(workPackage, null);
                        });

            // Chances are no events were scheduled at all. Scheduling regardless will ensure the token claim is held.
            workPackages.values()
                        .forEach(WorkPackage::scheduleWorker);
        }

        private void offerEventToWorkPackages(MessageStream.Entry<? extends EventMessage<?>> eventEntry) {
            boolean anyScheduled = false;
            for (WorkPackage workPackage : workPackages.values()) {
                boolean scheduled = workPackage.scheduleEvent(eventEntry);
                anyScheduled = anyScheduled || scheduled;
            }
            if (!anyScheduled) {
                EventMessage<?> event = eventEntry.message();
                ignoredMessageHandler.accept(event);
            }
        }

        private void offerEventsToWorkPackages(List<MessageStream.Entry<? extends EventMessage<?>>> eventEntries) {
            boolean anyScheduled = false;
            for (WorkPackage workPackage : workPackages.values()) {
                boolean scheduled = workPackage.scheduleEvents(eventEntries);
                anyScheduled = anyScheduled || scheduled;
            }
            if (!anyScheduled) {
                eventEntries.forEach(eventEntry -> {
                    EventMessage<?> event = eventEntry.message();
                    ignoredMessageHandler.accept(event);
                });
            }
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
            logger.info("Processor [{}] is releasing claims and scheduling a new coordination task in {}ms",
                        name, errorWaitBackOff);

            errorWaitBackOff = Math.min(errorWaitBackOff * 2, 60000);
            abortWorkPackages(cause).whenComplete(
                    (unused, throwable) -> {
                        if (throwable != null) {
                            logger.warn("An exception occurred during work packages abort on processor [{}].",
                                        name, throwable);
                        } else {
                            logger.debug("Work packages have aborted successfully.");
                        }
                        processingGate.set(false);
                        logger.debug("Scheduling new coordination task to run in {}ms", errorWaitBackOff);
                        // Construct a new CoordinationTask, thus abandoning the old task and it's progress entirely.
                        CoordinationTask task = new CoordinationTask();
                        executorService.schedule(task, errorWaitBackOff, TimeUnit.MILLISECONDS);
                        coordinationTask.set(task);
                    }
            );
            closeStreamQuietly();
        }

        private CompletableFuture<Void> abortWorkPackage(WorkPackage work, Exception cause) {
            int segmentId = work.segment().getSegmentId();
            return work.abort(cause)
                       .thenRun(() -> {
                           if (workPackages.remove(segmentId, work)) {
                               logger.debug("Processor [{}] released claim on {}.", name, work.segment());
                           }
                       })
                       .thenRun(() -> joinAndUnwrap(
                               unitOfWorkFactory
                                       .create()
                                       .executeWithResult(context -> {
                                           tokenStore.releaseClaim(name, segmentId);
                                           segmentReleasedAction.accept(work.segment());
                                           return emptyCompletedFuture();
                                       }))
                       )
                       .exceptionally(throwable -> {
                           logger.info("An exception occurred during the abort of work package [{}] on [{}] processor.",
                                       segmentId, name, throwable);
                           return null;
                       });
        }

        private void advanceReleaseDeadlineFor(int segmentId) {
            int errorWaitTime = releasesLastBackOffSeconds.compute(
                    segmentId,
                    (i, current) -> current == null ? 1 : Math.min(current * 2, 60)
            );
            releasesDeadlines.compute(
                    segmentId,
                    (i, current) -> {
                        Instant now = clock.instant();
                        Instant nextBackOffRetry = now.plusSeconds(errorWaitTime);
                        Instant releaseDeadline = (current != null && current.isAfter(nextBackOffRetry))
                                ? current
                                : nextBackOffRetry;
                        logger.debug(
                                "Processor [{}] set release deadline claim to [{}] for Segment [#{}] using backoff of [{}] seconds.",
                                name,
                                releaseDeadline,
                                segmentId,
                                errorWaitTime);
                        return releaseDeadline;
                    }
            );
        }
    }
}