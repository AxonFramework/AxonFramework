/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonNonTransientException;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.io.IOUtils.closeQuietly;

/**
 * EventProcessor implementation that tracks events from a {@link StreamableMessageSource}.
 * <p>
 * A supplied {@link TokenStore} allows the EventProcessor to keep track of its position in the event log. After
 * processing an event batch the EventProcessor updates its tracking token in the TokenStore.
 * <p>
 * A TrackingEventProcessor is able to continue processing from the last stored token when it is restarted. It is also
 * capable of replaying events from any starting token. To replay the entire event log simply remove the tracking token
 * of this processor from the TokenStore. To replay from a given point first update the entry for this processor in the
 * TokenStore before starting this processor.
 * <p>
 * <p>
 * Note, the {@link #getName() name} of the EventProcessor is used to obtain the tracking token from the TokenStore, so
 * take care when renaming a TrackingEventProcessor.
 * <p/>
 *
 * @author Rene de Waele
 * @author Christophe Bouhier
 * @since 3.0
 */
public class TrackingEventProcessor extends AbstractEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TrackingEventProcessor.class);

    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;
    private final TokenStore tokenStore;
    private final Function<StreamableMessageSource, TrackingToken> initialTrackingTokenBuilder;
    private final TransactionManager transactionManager;
    private final int batchSize;
    private final int segmentsSize;

    private final ActivityCountingThreadFactory threadFactory;
    private final AtomicReference<State> state = new AtomicReference<>(State.NOT_STARTED);
    private final ConcurrentMap<Integer, TrackerStatus> activeSegments = new ConcurrentSkipListMap<>();
    private final ConcurrentMap<Integer, Long> segmentReleaseDeadlines = new ConcurrentSkipListMap<>();
    private final String segmentIdResourceKey;
    private final String lastTokenResourceKey;
    private final AtomicInteger availableThreads;
    private final long tokenClaimInterval;

    /**
     * Instantiate a {@link TrackingEventProcessor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker}, {@link StreamableMessageSource},
     * {@link TokenStore} and {@link TransactionManager} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link TrackingEventProcessor} instance
     */
    protected TrackingEventProcessor(Builder builder) {
        super(builder);
        TrackingEventProcessorConfiguration config = builder.trackingEventProcessorConfiguration;
        this.tokenClaimInterval = config.getTokenClaimInterval();
        this.batchSize = config.getBatchSize();

        this.messageSource = builder.messageSource;
        this.tokenStore = builder.tokenStore;

        this.segmentsSize = config.getInitialSegmentsCount();
        this.transactionManager = builder.transactionManager;

        this.availableThreads = new AtomicInteger(config.getMaxThreadCount());
        this.threadFactory = new ActivityCountingThreadFactory(config.getThreadFactory(builder.name));
        this.segmentIdResourceKey = "Processor[" + builder.name + "]/SegmentId";
        this.lastTokenResourceKey = "Processor[" + builder.name + "]/Token";
        this.initialTrackingTokenBuilder = config.getInitialTrackingToken();

        registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (!(unitOfWork instanceof BatchingUnitOfWork) || ((BatchingUnitOfWork) unitOfWork).isFirstMessage()) {
                tokenStore.extendClaim(getName(), unitOfWork.getResource(segmentIdResourceKey));
            }
            if (!(unitOfWork instanceof BatchingUnitOfWork) || ((BatchingUnitOfWork) unitOfWork).isLastMessage()) {
                unitOfWork.onPrepareCommit(uow -> tokenStore.storeToken(unitOfWork.getResource(lastTokenResourceKey),
                                                                        builder.name,
                                                                        unitOfWork.getResource(segmentIdResourceKey)));
            }
            return interceptorChain.proceed();
        });
    }

    /**
     * Instantiate a Builder to be able to create a {@link TrackingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor} and the {@link TrackingEventProcessorConfiguration} to a
     * {@link TrackingEventProcessorConfiguration#forSingleThreadedProcessing()} call. The Event Processor {@code name},
     * {@link EventHandlerInvoker}, {@link StreamableMessageSource}, {@link TokenStore} and {@link TransactionManager}
     * are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link TrackingEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start this processor. The processor will open an event stream on its message source in a new thread using {@link
     * StreamableMessageSource#openStream(TrackingToken)}. The {@link TrackingToken} used to open the stream will be
     * fetched from the {@link TokenStore}.
     */
    @Override
    public void start() {
        State previousState = state.getAndSet(State.STARTED);
        if (!previousState.isRunning()) {
            startSegmentWorkers();
        }
    }

    /**
     * Fetch and process event batches continuously for as long as the processor is not shutting down. The processor
     * will process events in batches. The maximum size of size of each event batch is configurable.
     * <p>
     * Events with the same tracking token (which is possible as result of upcasting) should always be processed in
     * the same batch. In those cases the batch size may be larger than the one configured.
     *
     * @param segment The {@link Segment} of the Stream that should be processed.
     */
    protected void processingLoop(Segment segment) {
        BlockingStream<TrackedEventMessage<?>> eventStream = null;
        long errorWaitTime = 1;
        try {
            while (state.get().isRunning() && canClaimSegment(segment.getSegmentId())) {
                try {
                    eventStream = ensureEventStreamOpened(eventStream, segment);
                    processBatch(segment, eventStream);
                    errorWaitTime = 1;
                } catch (UnableToClaimTokenException e) {
                    logger.info("Segment is owned by another node. Releasing thread to process another segment...");
                    releaseSegment(segment.getSegmentId());
                } catch (Exception e) {
                    // Make sure to start with a clean event stream. The exception may have caused an illegal state
                    if (errorWaitTime == 1) {
                        logger.warn("Error occurred. Starting retry mode.", e);
                    }
                    logger.warn("Releasing claim on token and preparing for retry in {}s", errorWaitTime);
                    releaseToken(segment);
                    closeQuietly(eventStream);
                    eventStream = null;
                    doSleepFor(SECONDS.toMillis(errorWaitTime));
                    errorWaitTime = Math.min(errorWaitTime * 2, 60);
                }
            }
        } finally {
            closeQuietly(eventStream);
            releaseToken(segment);
        }
    }

    private void releaseToken(Segment segment) {
        try {
            transactionManager.executeInTransaction(() -> tokenStore.releaseClaim(getName(), segment.getSegmentId()));
        } catch (Exception e) {
            // Ignore exception
        }
    }

    private void processBatch(Segment segment, BlockingStream<TrackedEventMessage<?>> eventStream) throws Exception {
        List<TrackedEventMessage<?>> batch = new ArrayList<>();
        try {
            checkSegmentCaughtUp(segment, eventStream);
            TrackingToken lastToken = null;
            if (eventStream.hasNextAvailable(1, SECONDS)) {
                for (int i = 0; i < batchSize * 10 && batch.size() < batchSize && eventStream.hasNextAvailable(); i++) {
                    final TrackedEventMessage<?> trackedEventMessage = eventStream.nextAvailable();
                    lastToken = trackedEventMessage.trackingToken();
                    if (canHandle(trackedEventMessage, segment)) {
                        batch.add(trackedEventMessage);
                    } else {
                        reportIgnored(trackedEventMessage);
                    }
                }
                if (batch.isEmpty()) {
                    TrackingToken finalLastToken = lastToken;
                    transactionManager.executeInTransaction(
                            () -> tokenStore.storeToken(finalLastToken, getName(), segment.getSegmentId())
                    );
                    return;
                }
            } else {
                // Refresh claim on token
                transactionManager.executeInTransaction(
                        () -> tokenStore.extendClaim(getName(), segment.getSegmentId())
                );
                return;
            }

            TrackingToken finalLastToken = lastToken;
            // Make sure all subsequent events with the same token (if non-null) as the last are added as well.
            // These are the result of upcasting and should always be processed in the same batch.
            while (lastToken != null
                    && eventStream.peek().filter(event -> finalLastToken.equals(event.trackingToken())).isPresent()) {
                final TrackedEventMessage<?> trackedEventMessage = eventStream.nextAvailable();
                if (canHandle(trackedEventMessage, segment)) {
                    batch.add(trackedEventMessage);
                }
            }

            UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(batch);
            unitOfWork.attachTransaction(transactionManager);
            unitOfWork.resources().put(segmentIdResourceKey, segment.getSegmentId());
            unitOfWork.resources().put(lastTokenResourceKey, finalLastToken);
            processInUnitOfWork(batch, unitOfWork, segment);

            activeSegments.computeIfPresent(segment.getSegmentId(), (k, v) -> v.advancedTo(finalLastToken));
        } catch (InterruptedException e) {
            logger.error(String.format("Event processor [%s] was interrupted. Shutting down.", getName()), e);
            this.shutDown();
            Thread.currentThread().interrupt();
        }
    }

    private void checkSegmentCaughtUp(Segment segment, BlockingStream<TrackedEventMessage<?>> eventStream) {
        if (!eventStream.hasNextAvailable()) {
            activeSegments.computeIfPresent(segment.getSegmentId(), (k, v) -> v.caughtUp());
        }
    }

    private BlockingStream<TrackedEventMessage<?>> ensureEventStreamOpened(
            BlockingStream<TrackedEventMessage<?>> eventStreamIn, Segment segment) {
        BlockingStream<TrackedEventMessage<?>> eventStream = eventStreamIn;
        if (eventStream == null && state.get().isRunning()) {
            final TrackingToken trackingToken = transactionManager.fetchInTransaction(
                    () -> tokenStore.fetchToken(getName(), segment.getSegmentId())
            );
            logger.info("Fetched token: {} for segment: {}", trackingToken, segment);
            eventStream = transactionManager.fetchInTransaction(
                    () -> doOpenStream(trackingToken));
        }
        return eventStream;
    }

    private BlockingStream<TrackedEventMessage<?>> doOpenStream(TrackingToken trackingToken) {
        if (trackingToken instanceof ReplayToken) {
            return new ReplayingMessageStream((ReplayToken) trackingToken,
                                              messageSource.openStream(((ReplayToken) trackingToken).unwrap()));
        }
        return messageSource.openStream(trackingToken);
    }

    /**
     * Instructs the processor to release the segment with given {@code segmentId}. This will also blacklist this
     * segment for twice the {@link TrackingEventProcessorConfiguration#getTokenClaimInterval() token claim interval},
     * to ensure it is not immediately reclaimed.
     *
     * @param segmentId the id of the segment to be blacklisted
     */
    public void releaseSegment(int segmentId) {
        releaseSegment(segmentId, tokenClaimInterval * 2, MILLISECONDS);
    }

    /**
     * Instructs the processor to release the segment with given {@code segmentId}. This will also blacklist this
     * segment for the given {@code blacklistDuration}, to ensure it is not immediately reclaimed. Note that this will
     * override any previous blacklist duration that existed for this segment. Providing a negative value will allow
     * the segment to be immediately claimed.
     * <p>
     * If the processor is not actively processing the segment with given {@code segmentId}, it will be blacklisted
     * nonetheless.
     *
     * @param segmentId         the id of the segment to be blacklisted
     * @param blacklistDuration the amount of time to blacklist this segment for processing by this processor instance
     * @param unit              the unit of time used to express the {@code blacklistDuration}
     */
    public void releaseSegment(int segmentId, long blacklistDuration, TimeUnit unit) {
        segmentReleaseDeadlines.put(segmentId, System.currentTimeMillis() + unit.toMillis(blacklistDuration));
    }

    private boolean canClaimSegment(int segmentId) {
        return !segmentReleaseDeadlines.containsKey(segmentId) ||
                segmentReleaseDeadlines.get(segmentId) < System.currentTimeMillis();
    }

    /**
     * Resets tokens to their initial state. This effectively causes a replay.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the
     * same logical processor that may be running in the cluster. Failure to do so will cause the reset to fail,
     * as a processor can only reset the tokens if it is able to claim them all.
     */
    public void resetTokens() {
        resetTokens(initialTrackingTokenBuilder);
    }

    /**
     * Reset tokens to the position as return by the given {@code initialTrackingTokenSupplier}. This effectively causes
     * a replay since that position.
     * <p>
     * Note that the new token must represent a position that is <em>before</em> the current position of the processor.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the
     * same logical processor that may be running in the cluster. Failure to do so will cause the reset to fail,
     * as a processor can only reset the tokens if it is able to claim them all.
     *
     * @param initialTrackingTokenSupplier A function returning the token representing the position to reset to
     */
    public void resetTokens(Function<StreamableMessageSource, TrackingToken> initialTrackingTokenSupplier) {
        resetTokens(initialTrackingTokenSupplier.apply(messageSource));
    }

    /**
     * Resets tokens to the given {@code startPosition}. This effectively causes a replay of events since that position.
     * <p>
     * Note that the new token must represent a position that is <em>before</em> the current position of the processor.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the
     * same logical processor that may be running in the cluster. Failure to do so will cause the reset to fail,
     * as a processor can only reset the tokens if it is able to claim them all.
     *
     * @param startPosition The token representing the position to reset the processor to.
     */
    public void resetTokens(TrackingToken startPosition) {
        Assert.state(supportsReset(), () -> "The handlers assigned to this Processor do not support a reset");
        Assert.state(!isRunning() && activeProcessorThreads() == 0,
                     () -> "TrackingProcessor must be shut down before triggering a reset");
        transactionManager.executeInTransaction(() -> {
            int[] segments = tokenStore.fetchSegments(getName());
            TrackingToken[] tokens = new TrackingToken[segments.length];
            for (int i = 0; i < segments.length; i++) {
                tokens[i] = tokenStore.fetchToken(getName(), segments[i]);
            }
            // we now have all tokens, hurray
            eventHandlerInvoker().performReset();

            for (int i = 0; i < tokens.length; i++) {
                tokenStore.storeToken(ReplayToken.createReplayToken(tokens[i], startPosition), getName(), segments[i]);
            }
        });
    }

    /**
     * Indicates whether this tracking processor supports a "reset". Generally, a reset is supported if at least one
     * of the event handlers assigned to this processor supports it, and no handlers explicitly prevent the resets.
     *
     * @return {@code true} if resets are supported, {@code false} otherwise
     */
    public boolean supportsReset() {
        return eventHandlerInvoker().supportsReset();
    }

    /**
     * Indicates whether this processor is currently running (i.e. consuming events from a stream).
     *
     * @return {@code true} when running, otherwise {@code false}
     */
    public boolean isRunning() {
        return state.get().isRunning();
    }

    /**
     * Indicates whether the processor has been paused due to an error. In such case, the processor has forcefully
     * paused, as it wasn't able to automatically recover.
     * <p>
     * Note that this method also returns {@code false} when the processor was stooped using {@link #shutDown()}.
     *
     * @return {@code true} when paused due to an error, otherwise {@code false}
     */
    public boolean isError() {
        return state.get() == State.PAUSED_ERROR;
    }

    /**
     * Shut down the processor.
     */
    @Override
    public void shutDown() {
        if (state.getAndSet(State.SHUT_DOWN).isRunning()) {
            logger.info("Shutdown state set for Processor '{}'. Awaiting termination...", getName());
            try {
                while (threadFactory.activeThreads() > 0) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                logger.info("Thread was interrupted while waiting for TrackingProcessor '{}' shutdown.", getName());
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns the number of threads this processor has available to assign segments. These threads may or may not
     * already be active.
     *
     * @return the number of threads this processor has available to assign segments.
     */
    public int availableProcessorThreads() {
        return availableThreads.get();
    }

    /**
     * Returns an approximation of the number of threads currently processing events.
     *
     * @return an approximation of the number of threads currently processing events
     */
    public int activeProcessorThreads() {
        return this.activeSegments.size();
    }

    /**
     * Returns the status for each of the Segments processed by the current processor. The key of the map represents
     * the SegmentID processed by this instance. The values of the returned Map represent the last known status of that
     * Segment.
     * <p>
     * Note that the returned Map in unmodifiable, but does reflect any changes made to the status as the processor
     * is processing Events.
     *
     * @return the status for each of the Segments processed by the current processor
     */
    public Map<Integer, EventTrackerStatus> processingStatus() {
        return Collections.unmodifiableMap(activeSegments);
    }

    /**
     * Get the state of the event processor. This will indicate whether or not the processor has started or is shutting
     * down.
     *
     * @return the processor state
     */
    protected State getState() {
        return state.get();
    }

    /**
     * Starts the {@link TrackingSegmentWorker workers} for a number of segments. When only the
     * {@link Segment#ROOT_SEGMENT root } segment {@link TokenStore#fetchSegments(String) exists} in the  TokenStore,
     * it will be split in multiple segments as configured by the
     * {@link TrackingEventProcessorConfiguration#andInitialSegmentsCount(int)}, otherwise the existing segments in
     * the TokenStore will be used.
     * <p/>
     * An attempt will be made to instantiate a {@link TrackingSegmentWorker} for each segment. This will succeed when
     * the number of threads matches the requested segments. The number of active threads can be configured with
     * {@link TrackingEventProcessorConfiguration#forParallelProcessing(int)}. When insufficient threads are available
     * to serve the number of segments, it will result in some segments not being processed.
     */
    protected void startSegmentWorkers() {
        threadFactory.newThread(new WorkerLauncher()).start();
    }

    /**
     * Instructs the current Thread to sleep until the given deadline. This method may be overridden to check for
     * flags that have been set to return earlier than the given deadline.
     * <p>
     * The default implementation will sleep in blocks of 100ms, intermittently checking for the processor's state. Once
     * the processor stops running, this method will return immediately (after detecting the state change).
     *
     * @param millisToSleep The number of milliseconds to sleep
     */
    protected void doSleepFor(long millisToSleep) {
        long deadline = System.currentTimeMillis() + millisToSleep;
        try {
            long timeLeft;
            while (getState().isRunning() && (timeLeft = deadline - System.currentTimeMillis()) > 0) {
                Thread.sleep(Math.min(timeLeft, 100));
            }
        } catch (InterruptedException e) {
            logger.warn("Thread interrupted. Preparing to shut down event processor");
            shutDown();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enum representing the possible states of the Processor
     */
    protected enum State {

        /**
         * Indicates the processor has not been started yet.
         */
        NOT_STARTED(false),
        /**
         * Indicates that the processor has started and is (getting ready to) processing events
         */
        STARTED(true),
        /**
         * Indicates that the processor has been paused. It can be restarted to continue processing.
         */
        PAUSED(false),
        /**
         * Indicates that the processor has been shut down. It can be restarted to continue processing.
         */
        SHUT_DOWN(false),
        /**
         * Indicates that the processor has been paused due to an error that it was unable to recover from. Restarting
         * is possible once the error has been resolved.
         */
        PAUSED_ERROR(false);

        private final boolean allowProcessing;

        State(boolean allowProcessing) {
            this.allowProcessing = allowProcessing;
        }

        boolean isRunning() {
            return allowProcessing;
        }
    }

    private static class ActivityCountingThreadFactory implements ThreadFactory {

        private final AtomicInteger threadCount = new AtomicInteger(0);
        private final ThreadFactory delegate;

        public ActivityCountingThreadFactory(ThreadFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public Thread newThread(Runnable r) {
            return delegate.newThread(new CountingRunnable(r, threadCount));
        }

        public int activeThreads() {
            return threadCount.get();
        }
    }

    private static class CountingRunnable implements Runnable {

        private final Runnable delegate;
        private final AtomicInteger counter;

        public CountingRunnable(Runnable delegate, AtomicInteger counter) {
            this.delegate = delegate;
            this.counter = counter;
        }

        @Override
        public void run() {
            counter.incrementAndGet();
            try {
                delegate.run();
            } finally {
                counter.decrementAndGet();
            }
        }
    }

    private static final class TrackerStatus implements EventTrackerStatus {

        private final Segment segment;
        private final boolean caughtUp;
        private final TrackingToken trackingToken;

        private TrackerStatus(Segment segment, TrackingToken trackingToken) {
            this(segment, false, trackingToken);
        }

        private TrackerStatus(Segment segment, boolean caughtUp, TrackingToken trackingToken) {
            this.segment = segment;
            this.caughtUp = caughtUp;
            this.trackingToken = trackingToken;
        }

        private TrackerStatus caughtUp() {
            if (caughtUp) {
                return this;
            }
            return new TrackerStatus(segment, true, trackingToken);
        }

        private TrackerStatus advancedTo(TrackingToken trackingToken) {
            if (Objects.equals(this.trackingToken, trackingToken)) {
                return this;
            }
            return new TrackerStatus(segment, caughtUp, trackingToken);
        }


        @Override
        public Segment getSegment() {
            return segment;
        }

        @Override
        public boolean isCaughtUp() {
            return caughtUp;
        }

        @Override
        public boolean isReplaying() {
            return trackingToken instanceof ReplayToken;
        }

        @Override
        public TrackingToken getTrackingToken() {
            if (trackingToken instanceof ReplayToken) {
                return ((ReplayToken) trackingToken).unwrap();
            }
            return trackingToken;
        }
    }

    private class TrackingSegmentWorker implements Runnable {

        private final Segment segment;

        public TrackingSegmentWorker(Segment segment) {
            this.segment = segment;
        }

        @Override
        public void run() {
            try {
                processingLoop(segment);
            } catch (Throwable e) {
                logger.error("Processing loop ended due to uncaught exception. Pausing processor in Error State.", e);
                state.set(State.PAUSED_ERROR);
            } finally {
                activeSegments.remove(segment.getSegmentId());
                if (availableThreads.getAndIncrement() == 0 && getState().isRunning()) {
                    logger.info("No Worker Launcher active. Using current thread to assign segments.");
                    new WorkerLauncher().run();
                }
            }
        }

        @Override
        public String toString() {
            return "TrackingSegmentWorker{" +
                    "processor=" + getName() +
                    ", segment=" + segment +
                    '}';
        }
    }

    private class WorkerLauncher implements Runnable {

        @Override
        public void run() {
            int waitTime = 1;
            String processorName = TrackingEventProcessor.this.getName();
            while (getState().isRunning()) {
                int[] tokenStoreCurrentSegments;

                try {
                    tokenStoreCurrentSegments = tokenStore.fetchSegments(processorName);

                    // When in an initial stage, split segments to the requested number.
                    if (tokenStoreCurrentSegments.length == 0 && segmentsSize > 0) {
                        tokenStoreCurrentSegments = transactionManager.fetchInTransaction(
                                () -> {
                                    TrackingToken initialToken = initialTrackingTokenBuilder.apply(messageSource);
                                    tokenStore.initializeTokenSegments(processorName, segmentsSize, initialToken);
                                    return tokenStore.fetchSegments(processorName);
                                });
                    }
                    waitTime = 1;
                } catch (Exception e) {
                    logger.warn("Fetch Segments for Processor '{}' failed: {}. Preparing for retry in {}s",
                                processorName, e.getMessage(), waitTime);
                    doSleepFor(SECONDS.toMillis(waitTime));
                    waitTime = Math.min(waitTime * 2, 60);

                    continue;
                }

                Segment[] segments = Segment.computeSegments(tokenStoreCurrentSegments);

                // Submit segmentation workers matching the size of our thread pool (-1 for the current dispatcher).
                // Keep track of the last processed segments...
                TrackingSegmentWorker workingInCurrentThread = null;
                for (int i = 0; i < segments.length && availableThreads.get() > 0; i++) {
                    Segment segment = segments[i];

                    if (!activeSegments.containsKey(segment.getSegmentId())
                            && canClaimSegment(segment.getSegmentId())) {
                        try {
                            transactionManager.executeInTransaction(() -> {
                                TrackingToken token = tokenStore.fetchToken(processorName, segment.getSegmentId());
                                activeSegments.putIfAbsent(segment.getSegmentId(), new TrackerStatus(segment, token));
                            });
                        } catch (UnableToClaimTokenException ucte) {
                            // When not able to claim a token for a given segment, we skip the
                            logger.debug("Unable to claim the token for segment: {}. It is owned by another process",
                                         segment.getSegmentId());
                            activeSegments.remove(segment.getSegmentId());
                            continue;
                        } catch (Exception e) {
                            activeSegments.remove(segment.getSegmentId());
                            if (AxonNonTransientException.isCauseOf(e)) {
                                logger.error(
                                        "An unrecoverable error has occurred wile attempting to claim a token "
                                                + "for segment: {}. Shutting down processor [{}].",
                                        segment.getSegmentId(),
                                        getName(),
                                        e
                                );
                                state.set(State.PAUSED_ERROR);
                                break;
                            }
                            logger.info(
                                    "An error occurred while attempting to claim a token for segment: {}. "
                                            + "Will retry later...",
                                    segment.getSegmentId(),
                                    e
                            );
                            continue;
                        }

                        TrackingSegmentWorker trackingSegmentWorker = new TrackingSegmentWorker(segment);
                        if (availableThreads.decrementAndGet() > 0) {
                            logger.info("Dispatching new tracking segment worker: {}", trackingSegmentWorker);
                            threadFactory.newThread(trackingSegmentWorker).start();
                        } else {
                            workingInCurrentThread = trackingSegmentWorker;
                            break;
                        }
                    }
                }

                // We're not able to spawn new threads, so this thread should also start processing.
                if (nonNull(workingInCurrentThread)) {
                    logger.info("Using current Thread for last segment worker: {}", workingInCurrentThread);
                    workingInCurrentThread.run();
                    return;
                }
                doSleepFor(tokenClaimInterval);
            }
        }
    }

    private class ReplayingMessageStream implements BlockingStream<TrackedEventMessage<?>> {

        private final BlockingStream<TrackedEventMessage<?>> delegate;
        private ReplayToken lastToken;

        public ReplayingMessageStream(ReplayToken token, BlockingStream<TrackedEventMessage<?>> delegate) {
            this.delegate = delegate;
            this.lastToken = token;
        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            return delegate.peek();
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            return delegate.hasNextAvailable(timeout, unit);
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            TrackedEventMessage<?> trackedEventMessage = alterToken(delegate.nextAvailable());
            this.lastToken = trackedEventMessage.trackingToken() instanceof ReplayToken
                    ? (ReplayToken) trackedEventMessage.trackingToken()
                    : null;
            return trackedEventMessage;
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public boolean hasNextAvailable() {
            return delegate.hasNextAvailable();
        }

        @SuppressWarnings("unchecked")
        public <T> TrackedEventMessage<T> alterToken(TrackedEventMessage<T> message) {
            if (lastToken == null) {
                return message;
            }
            if (message instanceof DomainEventMessage) {
                return new GenericTrackedDomainEventMessage<>(lastToken.advancedTo(message.trackingToken()),
                                                              (DomainEventMessage<T>) message);
            } else {
                return new GenericTrackedEventMessage<>(lastToken.advancedTo(message.trackingToken()), message);
            }
        }
    }

    /**
     * Builder class to instantiate a {@link TrackingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor} and the {@link TrackingEventProcessorConfiguration} to a
     * {@link TrackingEventProcessorConfiguration#forSingleThreadedProcessing()} call. The Event Processor {@code name},
     * {@link EventHandlerInvoker}, {@link StreamableMessageSource}, {@link TokenStore} and {@link TransactionManager}
     * are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends AbstractEventProcessor.Builder {

        private StreamableMessageSource<TrackedEventMessage<?>> messageSource;
        private TokenStore tokenStore;
        private TransactionManager transactionManager;
        private TrackingEventProcessorConfiguration trackingEventProcessorConfiguration =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing();

        public Builder() {
            super.rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE);
        }

        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder eventHandlerInvoker(EventHandlerInvoker eventHandlerInvoker) {
            super.eventHandlerInvoker(eventHandlerInvoker);
            return this;
        }

        /**
         * {@inheritDoc}. Defaults to a {@link RollbackConfigurationType#ANY_THROWABLE})
         */
        @Override
        public Builder rollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
            super.rollbackConfiguration(rollbackConfiguration);
            return this;
        }

        @Override
        public Builder errorHandler(ErrorHandler errorHandler) {
            super.errorHandler(errorHandler);
            return this;
        }

        @Override
        public Builder messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        /**
         * Sets the {@link StreamableMessageSource} (e.g. the {@link EventBus}) which this {@link EventProcessor} will
         * track.
         *
         * @param messageSource the {@link StreamableMessageSource} (e.g. the {@link EventBus}) which this {@link
         *                      EventProcessor} will track
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageSource(StreamableMessageSource<TrackedEventMessage<?>> messageSource) {
            assertNonNull(messageSource, "StreamableMessageSource may not be null");
            this.messageSource = messageSource;
            return this;
        }

        /**
         * Sets the {@link TokenStore} used to store and fetch event tokens that enable this {@link EventProcessor} to
         * track its progress.
         *
         * @param tokenStore the {@link TokenStore} used to store and fetch event tokens that enable this {@link
         *                   EventProcessor} to track its progress
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tokenStore(TokenStore tokenStore) {
            assertNonNull(tokenStore, "TokenStore may not be null");
            this.tokenStore = tokenStore;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used when processing {@link EventMessage}s.
         *
         * @param transactionManager the {@link TransactionManager} used when processing {@link EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link TrackingEventProcessorConfiguration} containing the fine grained configuration options for a
         * {@link TrackingEventProcessor}. Defaults to a
         * {@link TrackingEventProcessorConfiguration#forSingleThreadedProcessing()} call.
         *
         * @param trackingEventProcessorConfiguration the {@link TrackingEventProcessorConfiguration} containing the
         *                                            fine grained configuration options for a
         *                                            {@link TrackingEventProcessor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder trackingEventProcessorConfiguration(
                TrackingEventProcessorConfiguration trackingEventProcessorConfiguration) {
            assertNonNull(trackingEventProcessorConfiguration, "TrackingEventProcessorConfiguration may not be null");
            this.trackingEventProcessorConfiguration = trackingEventProcessorConfiguration;
            return this;
        }

        /**
         * Initializes a {@link TrackingEventProcessor} as specified through this Builder.
         *
         * @return a {@link TrackingEventProcessor} as specified through this Builder
         */
        public TrackingEventProcessor build() {
            return new TrackingEventProcessor(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(messageSource, "The StreamableMessageSource is a hard requirement and should be provided");
            assertNonNull(tokenStore, "The TokenStore is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
