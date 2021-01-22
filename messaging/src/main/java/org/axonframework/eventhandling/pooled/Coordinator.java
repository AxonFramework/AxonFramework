package org.axonframework.eventhandling.pooled;

import org.axonframework.common.io.IOUtils;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.messaging.StreamableMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

// TODO: 22-01-21 add javadoc
public class Coordinator {

    private static final Logger logger = LoggerFactory.getLogger(Coordinator.class);

    private final String name;
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;
    private final BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory;
    private final ScheduledExecutorService executorService;
    private final BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater;
    private int errorWaitBackOff = 500;
    private final Map<Integer, WorkPackage> workPackages = new ConcurrentHashMap<>();

    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.initial());
    private final ConcurrentMap<Integer, Instant> releasesDeadlines = new ConcurrentHashMap<>();

    public Coordinator(String name,
                       StreamableMessageSource<TrackedEventMessage<?>> messageSource,
                       TokenStore tokenStore,
                       TransactionManager transactionManager,
                       BiFunction<Segment, TrackingToken, WorkPackage> workPackageFactory,
                       ScheduledExecutorService executorService,
                       BiConsumer<Integer, UnaryOperator<TrackerStatus>> processingStatusUpdater) {
        this.name = name;
        this.messageSource = messageSource;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
        this.workPackageFactory = workPackageFactory;
        this.executorService = executorService;
        this.processingStatusUpdater = processingStatusUpdater;
    }

    public void start() {
        RunState newState = this.runState.updateAndGet(RunState::attemptStart);
        if (newState.wasStarted()) {
            logger.info("Starting coordinator {}", name);

            try {
                executorService.submit(new CoordinatorTask());
            } catch (Exception e) {
                // a failure starting the processor. We need to stop immediately.
                logger.warn("An error occurred while trying to attempt to start the Processor", e);
                runState.updateAndGet(RunState::attemptStop).shutdownHandle.complete(null);
                throw e;
            }
        } else if (!newState.isRunning) {
            throw new IllegalStateException("Cannot start a processor while it's in process of shutting down");
        }
    }

    public CompletableFuture<Void> stop() {
        logger.info("Stopping processor");
        return runState.updateAndGet(RunState::attemptStop).shutdownHandle();
    }

    public boolean isRunning() {
        return runState.get().isRunning();
    }

    public boolean isError() {
        return errorWaitBackOff > 500;
    }

    public void releaseUntil(int segmentId, Instant releaseDeadline) {
        releasesDeadlines.put(segmentId, releaseDeadline);
        WorkPackage workPackage = workPackages.get(segmentId);
        if (workPackage != null) {
            workPackage.abort(null);
        }
    }

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
                // it was already started
                return new RunState(true, false, null);
            } else if (shutdownHandle.isDone()) {
                // shutdown has previously been completed. It's allowed to start
                return new RunState(true, true, null);
            } else {
                // shutdown is in progress
                return this;
            }
        }

        public RunState attemptStop() {
            if (!isRunning || shutdownHandle != null) {
                // it's already stopped
                return this;
            }
            return new RunState(false, false, new CompletableFuture<>());
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

    private class CoordinatorTask implements Runnable {

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
                logger.info("Stopped processing. Runnable flag is false.");
                logger.info("Releasing claims for {}", name);
                abortWorkPackages(null).thenRun(() -> runState.get().shutdownHandle.complete(null));
                return;
            }

            if (stream == null) {
                // TODO - Also execute this when last check for segments was beyond threshold
                startWorkPackages();
            }
            if (workPackages.isEmpty()) {
                // we didn't start any work packages. Retry later.
                logger.info("No Segments claimed. Will retry in {} seconds", 5);
                processingGate.set(false);
                scheduleCoordinationTask(5, TimeUnit.SECONDS);
                return;
            }
            try {
                readEvents();
                errorWaitBackOff = 500;

                processingGate.set(false);

                if (isSpaceAvailable() && stream.hasNextAvailable()) {
                    scheduleCoordinationTask(0, TimeUnit.MILLISECONDS);
                } else if (isSpaceAvailable()) {
                    // there is space, but no events to process. We caught up
                    workPackages.keySet().forEach(i -> processingStatusUpdater.accept(i, TrackerStatus::caughtUp));

                    if (!availabilityCallbackSupported) {
                        scheduleCoordinationTask(500, TimeUnit.MILLISECONDS);
                    } else {
                        scheduleDelayedCoordinationTask(5, TimeUnit.SECONDS);
                    }
                } else {
                    scheduleCoordinationTask(100, TimeUnit.MILLISECONDS);
                }

            } catch (Exception e) {
                abortAndScheduleRetry(e);
            }
        }

        private void startWorkPackages() {
            TrackingToken baseToken = transactionManager.fetchInTransaction(() -> {
                int[] segments = tokenStore.fetchSegments(name);
                TrackingToken lowerBound = NoToken.INSTANCE;
                for (int segmentId : segments) {
                    if (!workPackages.containsKey(segmentId)) {
                        try {
                            TrackingToken token = tokenStore.fetchToken(name, segmentId);
                            // TODO - Log a different message for the segments for which we already have a work package
                            logger.info("Token for {} segment {} claimed: [{}]. Preparing work package", name, segmentId, token);
                            Segment segment = Segment.computeSegment(segmentId, segments);
                            WorkPackage workPackage = workPackages.computeIfAbsent(segmentId, k -> workPackageFactory.apply(segment, token));
                            lowerBound = lowerBound == null ? null : lowerBound.lowerBound(workPackage.lastDeliveredToken());
                        } catch (UnableToClaimTokenException e) {
                            // too bad. next
                        }
                    }
                }
                return lowerBound;
            });
            if (!NoToken.INSTANCE.equals(baseToken)) {
                // we calculated a proper token to open the stream from...
                // TODO - If we already have a stream open, we should close that one.
                try {
                    stream = messageSource.openStream(baseToken);
                    this.availabilityCallbackSupported = stream.setOnAvailableCallback(() -> scheduleCoordinationTask(0, TimeUnit.MILLISECONDS));
                    if (!availabilityCallbackSupported) {
                        scheduleCoordinationTask(0, TimeUnit.MILLISECONDS);
                    } else {
                        scheduleDelayedCoordinationTask(5, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    abortAndScheduleRetry(e);
                }
            }
        }

        private void readEvents() throws InterruptedException {
            logger.info("Fetching events...");
            for (int t = 0; t < 1024 && isSpaceAvailable() && stream.hasNextAvailable(); t++) {
                TrackedEventMessage<?> event = stream.nextAvailable();
                for (WorkPackage wp : workPackages.values()) {
                    wp.scheduleEvent(event);
                }
            }

            for (WorkPackage wp : workPackages.values()) {
                if (wp.isAbortTriggered()) {
                    // apparently, the work package has been aborted by something else than the Coordinator. We should abandon it.
                    abortWorkPackage(wp, null);
                }
                wp.scheduleWorker();
            }
        }

        private void abortAndScheduleRetry(Exception cause) {
            logger.warn("Exception occurred while trying to fetch events for processing. Releasing claims and rescheduling for processing in {}ms", errorWaitBackOff, cause);

            errorWaitBackOff = Math.min(errorWaitBackOff * 2, 60000);
            abortWorkPackages(cause).thenRun(
                    () -> {
                        logger.info("Work packages have aborted. Scheduling new fetcher to run in {}ms", errorWaitBackOff);
                        executorService.schedule(new CoordinatorTask(), errorWaitBackOff, TimeUnit.MILLISECONDS);
                    }
            );
            IOUtils.closeQuietly(stream);
        }

        public void scheduleCoordinationTask(long delay, TimeUnit unit) {
            if (scheduledGate.compareAndSet(false, true)) {
                executorService.schedule(() -> {
                    scheduledGate.set(false);
                    this.run();
                }, delay, unit);
            }
        }

        private void scheduleDelayedCoordinationTask(int delay, TimeUnit unit) {
            // we only want to schedule a delayed task if there isn't another delayed task scheduled,
            // and preferably not if a regular task has already been scheduled (hence just a get() for that flag)
            if (!scheduledGate.get() && interruptibleScheduledGate.compareAndSet(false, true)) {
                executorService.schedule(() -> {
                    interruptibleScheduledGate.set(false);
                    this.run();
                }, delay, unit);
            }
        }

        private CompletableFuture<Void> abortWorkPackage(WorkPackage wp, Exception cause) {
            return wp.abort(cause)
                     .thenAccept(e -> logger.info("Worker {} has aborted. Releasing claim...", wp.getSegment().getSegmentId(), e))
                     .thenRun(() -> workPackages.remove(wp.getSegment().getSegmentId(), wp))
                     .thenRun(() -> transactionManager.executeInTransaction(
                             () -> tokenStore.releaseClaim(name, wp.getSegment().getSegmentId())));
        }

        private CompletableFuture<Void> abortWorkPackages(Exception cause) {
            return workPackages.values()
                               .stream()
                               .map(wp -> abortWorkPackage(wp, cause))
                               .reduce(CompletableFuture::allOf)
                               .orElse(CompletableFuture.completedFuture(null))
                               .thenRun(workPackages::clear);
        }

        private boolean isSpaceAvailable() {
            return workPackages.values().stream().allMatch(WorkPackage::hasRemainingCapacity);
        }

    }
}
