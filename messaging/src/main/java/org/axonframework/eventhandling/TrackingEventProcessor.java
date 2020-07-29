/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.common.ExceptionUtils;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ProcessUtils.executeWithRetry;
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
    private final Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenBuilder;
    private final TransactionManager transactionManager;
    private final int batchSize;
    private final int segmentsSize;

    private final ThreadFactory threadFactory;
    private final AtomicReference<State> state = new AtomicReference<>(State.NOT_STARTED);
    private final ConcurrentMap<Integer, TrackerStatus> activeSegments = new ConcurrentSkipListMap<>();
    private final ConcurrentMap<Integer, Long> segmentReleaseDeadlines = new ConcurrentSkipListMap<>();
    private final String segmentIdResourceKey;
    private final String lastTokenResourceKey;
    private final AtomicInteger availableThreads;
    private final long tokenClaimInterval;
    private final AtomicReference<String> tokenStoreIdentifier = new AtomicReference<>();

    private final ConcurrentMap<Integer, List<Instruction>> instructions = new ConcurrentHashMap<>();
    private final boolean storeTokenBeforeProcessing;
    private final int eventAvailabilityTimeout;
    private final EventTrackerStatusChangeListener trackerStatusChangeListener;

    /**
     * Instantiate a Builder to be able to create a {@link TrackingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the {@link
     * ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a {@link
     * NoOpMessageMonitor} and the {@link TrackingEventProcessorConfiguration} to a {@link
     * TrackingEventProcessorConfiguration#forSingleThreadedProcessing()} call. The Event Processor {@code name}, {@link
     * EventHandlerInvoker}, {@link StreamableMessageSource}, {@link TokenStore} and {@link TransactionManager} are
     * <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link TrackingEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link TrackingEventProcessor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker}, {@link StreamableMessageSource},
     * {@link TokenStore} and {@link TransactionManager} are not {@code null}, and will throw an {@link
     * AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link TrackingEventProcessor} instance
     */
    protected TrackingEventProcessor(Builder builder) {
        super(builder);
        TrackingEventProcessorConfiguration config = builder.trackingEventProcessorConfiguration;
        this.tokenClaimInterval = config.getTokenClaimInterval();
        this.eventAvailabilityTimeout = config.getEventAvailabilityTimeout();
        this.storeTokenBeforeProcessing = builder.storeTokenBeforeProcessing;
        this.batchSize = config.getBatchSize();

        this.messageSource = builder.messageSource;
        this.tokenStore = builder.tokenStore;

        this.segmentsSize = config.getInitialSegmentsCount();
        this.transactionManager = builder.transactionManager;

        this.availableThreads = new AtomicInteger(config.getMaxThreadCount());
        this.threadFactory = config.getThreadFactory(builder.name);
        this.segmentIdResourceKey = "Processor[" + builder.name + "]/SegmentId";
        this.lastTokenResourceKey = "Processor[" + builder.name + "]/Token";
        this.initialTrackingTokenBuilder = config.getInitialTrackingToken();
        this.trackerStatusChangeListener = config.getEventTrackerStatusChangeListener();

        registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (!(unitOfWork instanceof BatchingUnitOfWork) || ((BatchingUnitOfWork<?>) unitOfWork).isFirstMessage()) {
                Instant startTime = now();
                TrackingToken lastToken = unitOfWork.getResource(lastTokenResourceKey);
                if (storeTokenBeforeProcessing) {
                    tokenStore.storeToken(lastToken,
                                          builder.name,
                                          unitOfWork.getResource(segmentIdResourceKey));
                } else {
                    tokenStore.extendClaim(getName(), unitOfWork.getResource(segmentIdResourceKey));
                }
                unitOfWork.onPrepareCommit(uow -> {
                    if (!storeTokenBeforeProcessing) {
                        tokenStore.storeToken(lastToken,
                                              builder.name,
                                              unitOfWork.getResource(segmentIdResourceKey));
                    } else if (now().isAfter(startTime.plusMillis(eventAvailabilityTimeout))) {
                        tokenStore.extendClaim(getName(), unitOfWork.getResource(segmentIdResourceKey));
                    }
                });
            }
            return interceptorChain.proceed();
        });
    }

    private Instant now() {
        return GenericEventMessage.clock.instant();
    }

    /**
     * Start this processor. The processor will open an event stream on its message source in a new thread using {@link
     * StreamableMessageSource#openStream(TrackingToken)}. The {@link TrackingToken} used to open the stream will be
     * fetched from the {@link TokenStore}.
     * <p>
     * Upon start up of an application, this method will be invoked in the {@link Phase#INBOUND_EVENT_CONNECTORS}
     * phase.
     */
    @Override
    @StartHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void start() {
        State previousState = state.getAndSet(State.STARTED);
        if (!previousState.isRunning()) {
            startSegmentWorkers();
        }
    }

    /**
     * Instruct the processor to split the segment with given {@code segmentId} into two segments, allowing an
     * additional thread to start processing events concurrently.
     * <p>
     * To be able to split segments, the {@link TokenStore} configured with this processor must use explicitly
     * initialized tokens. See {@link TokenStore#requiresExplicitSegmentInitialization()}. Also, the given {@code
     * segmentId} must be currently processed by a thread owned by this processor instance.
     *
     * @param segmentId The identifier of the segment to split
     * @return a CompletableFuture providing the result of the split operation
     */
    public CompletableFuture<Boolean> splitSegment(int segmentId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!tokenStore.requiresExplicitSegmentInitialization()) {
            result.completeExceptionally(new UnsupportedOperationException(
                    "TokenStore must require explicit initialization to safely split tokens"
            ));
            return result;
        }

        if (!this.activeSegments.containsKey(segmentId)) {
            return CompletableFuture.completedFuture(false);
        }

        this.instructions.computeIfAbsent(segmentId, i -> new CopyOnWriteArrayList<>())
                         .add(new SplitSegmentInstruction(result, segmentId));
        return result;
    }

    /**
     * Returns the unique identifier of the TokenStore used by this EventProcessor.
     *
     * @return the unique identifier of the TokenStore used by this EventProcessor
     * @throws org.axonframework.eventhandling.tokenstore.UnableToRetrieveIdentifierException if the tokenStore was
     *                                                                                        unable to retrieve it
     */
    public String getTokenStoreIdentifier() {
        return tokenStoreIdentifier.updateAndGet(i -> i != null ? i : calculateIdentifier());
    }

    private String calculateIdentifier() {
        return transactionManager.fetchInTransaction(
                () -> tokenStore.retrieveStorageIdentifier().orElse("--unknown--")
        );
    }

    /**
     * Instruct the processor to merge the segment with given {@code segmentId} back with the segment that it was
     * originally split from. The processor must be able to claim the other segment, in order to merge it. Therefore,
     * this other segment must not have any active claims in the TokenStore.
     * <p>
     * The Processor must currently be actively processing the segment with given {@code segmentId}.
     * <p>
     * Use {@link #releaseSegment(int)} to force this processor to release any claims with tokens required to merge the
     * segments.
     * <p>
     * To find out which segment a given {@code segmentId} should be merged with, use the following procedure:
     * <pre>
     *     EventTrackerStatus status = processor.processingStatus().get(segmentId);
     *     if (status == null) {
     *         // this processor is not processing segmentId, and will not be able to merge
     *     }
     *     return status.getSegment().mergeableSegmentId();
     * </pre>
     *
     * @param segmentId The identifier of the segment to merge into this one.
     * @return a CompletableFuture indicating whether the merge was executed successfully
     */
    public CompletableFuture<Boolean> mergeSegment(int segmentId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!tokenStore.requiresExplicitSegmentInitialization()) {
            result.completeExceptionally(new UnsupportedOperationException(
                    "TokenStore must require explicit initialization to safely merge tokens"
            ));
            return result;
        }

        TrackerStatus segmentStatus = this.activeSegments.get(segmentId);
        if (segmentStatus == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (segmentId == segmentStatus.getSegment().mergeableSegmentId()) {
            logger.info("A merge request can only be fulfilled if there is more than one segment");
            return CompletableFuture.completedFuture(false);
        }

        int segmentToMerge = segmentStatus.getSegment().mergeableSegmentId();
        this.instructions.computeIfAbsent(segmentId, i -> new CopyOnWriteArrayList<>())
                         .add(new MergeSegmentInstruction(result, segmentId, segmentToMerge));
        return result;
    }

    /**
     * Fetch and process event batches continuously for as long as the processor is not shutting down. The processor
     * will process events in batches. The maximum size of size of each event batch is configurable.
     * <p>
     * Events with the same tracking token (which is possible as result of upcasting) should always be processed in the
     * same batch. In those cases the batch size may be larger than the one configured.
     *
     * @param segment The {@link Segment} of the Stream that should be processed.
     */
    protected void processingLoop(Segment segment) {
        BlockingStream<TrackedEventMessage<?>> eventStream = null;
        long errorWaitTime = 1;
        try {
            // only execute the loop when in running state, no processing instructions have been executed, and the
            // segment is not blacklisted for release
            while (state.get().isRunning() && !processInstructions(segment.getSegmentId())
                    && canClaimSegment(segment.getSegmentId())) {
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
                    updateActiveSegments(() -> activeSegments.computeIfPresent(
                            segment.getSegmentId(), (k, status) -> status.markError(e)
                    ));
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

    private boolean processInstructions(int segmentId) {
        List<Instruction> toExecute = instructions.getOrDefault(segmentId, Collections.emptyList());
        boolean instructionsPresent = !toExecute.isEmpty();
        for (Instruction instruction : toExecute) {
            toExecute.remove(instruction);

            instruction.run();
        }

        return instructionsPresent;
    }

    private void releaseToken(Segment segment) {
        try {
            transactionManager.executeInTransaction(() -> tokenStore.releaseClaim(getName(), segment.getSegmentId()));
            logger.info("Released claim");
        } catch (Exception e) {
            logger.info("Release claim failed", e);
            // Ignore exception
        }
    }

    /**
     * Indicates whether the {@code eventMessage} identified with given {@code token} should be processed as part of the
     * given {@code segment}. This implementation is away of merge tokens and will recursively detect the (sub)segment
     * in which an event should be handled.
     *
     * @param token   The token to check segment validity for
     * @param segment The segment to process the event in
     * @return {@code true} if this event should be handled, otherwise {@code false}
     */
    protected Set<Segment> processingSegments(TrackingToken token, Segment segment) {
        Optional<MergedTrackingToken> mergedToken = WrappedToken.unwrap(token, MergedTrackingToken.class);
        if (mergedToken.isPresent()) {
            Segment[] splitSegments = segment.split();
            Set<Segment> segments = new TreeSet<>();
            if (mergedToken.get().isLowerSegmentAdvanced()) {
                segments.addAll(processingSegments(mergedToken.get().lowerSegmentToken(), splitSegments[0]));
            }

            if (mergedToken.get().isUpperSegmentAdvanced()) {
                segments.addAll(processingSegments(mergedToken.get().upperSegmentToken(), splitSegments[1]));
            }
            return segments;
        }
        return singleton(segment);
    }

    private void processBatch(Segment segment, BlockingStream<TrackedEventMessage<?>> eventStream) throws Exception {
        List<TrackedEventMessage<?>> batch = new ArrayList<>();
        try {
            checkSegmentCaughtUp(segment, eventStream);
            TrackingToken lastToken;
            Collection<Segment> processingSegments;
            if (eventStream.hasNextAvailable(eventAvailabilityTimeout, MILLISECONDS)) {
                final TrackedEventMessage<?> firstMessage = eventStream.nextAvailable();
                lastToken = firstMessage.trackingToken();
                processingSegments = processingSegments(lastToken, segment);
                if (canHandle(firstMessage, processingSegments)) {
                    batch.add(firstMessage);
                } else {
                    canBlacklist(eventStream, firstMessage);
                    reportIgnored(firstMessage);
                }
                // besides checking batch sizes, we must also ensure that both the current message in the batch
                // and the next (if present) allow for processing with a batch
                for (int i = 0; isRegularProcessing(segment, processingSegments)
                        && i < batchSize * 10 && batch.size() < batchSize
                        && eventStream.peek().map(m -> isRegularProcessing(segment, m)).orElse(false); i++) {
                    final TrackedEventMessage<?> trackedEventMessage = eventStream.nextAvailable();
                    lastToken = trackedEventMessage.trackingToken();
                    if (canHandle(trackedEventMessage, processingSegments)) {
                        batch.add(trackedEventMessage);
                    } else {
                        canBlacklist(eventStream, trackedEventMessage);
                        reportIgnored(trackedEventMessage);
                    }
                }
                if (batch.isEmpty()) {
                    TrackingToken finalLastToken = lastToken;
                    transactionManager.executeInTransaction(
                            () -> tokenStore.storeToken(finalLastToken, getName(), segment.getSegmentId())
                    );
                    updateActiveSegments(() -> activeSegments.computeIfPresent(
                            segment.getSegmentId(), (k, v) -> v.advancedTo(finalLastToken)
                    ));
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
                if (canHandle(trackedEventMessage, processingSegments)) {
                    batch.add(trackedEventMessage);
                } else {
                    canBlacklist(eventStream, trackedEventMessage);
                    reportIgnored(trackedEventMessage);
                }
            }

            UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(batch);
            unitOfWork.attachTransaction(transactionManager);
            unitOfWork.resources().put(segmentIdResourceKey, segment.getSegmentId());
            unitOfWork.resources().put(lastTokenResourceKey, finalLastToken);
            processInUnitOfWork(batch, unitOfWork, processingSegments);

            updateActiveSegments(() -> activeSegments.computeIfPresent(
                    segment.getSegmentId(), (k, v) -> v.advancedTo(finalLastToken)
            ));
        } catch (InterruptedException e) {
            logger.error(String.format("Event processor [%s] was interrupted. Shutting down.", getName()), e);
            this.shutDown();
            Thread.currentThread().interrupt();
        }
    }

    private void canBlacklist(BlockingStream<TrackedEventMessage<?>> eventStream,
                              TrackedEventMessage<?> trackedEventMessage) {
        if (!canHandleType(trackedEventMessage.getPayloadType())) {
            eventStream.blacklist(trackedEventMessage);
        }
    }

    /**
     * Indicates whether any of the components handling events for this Processor are able to handle the given {@code
     * eventMessage} for any of the given {@code segments}.
     *
     * @param eventMessage The message to handle
     * @param segments     The segments to handle the message in
     * @return whether the given message should be handled as part of anyof the give segments
     * @throws Exception when an exception occurs evaluating the message
     */
    protected boolean canHandle(EventMessage<?> eventMessage, Collection<Segment> segments) throws Exception {
        for (Segment segment : segments) {
            if (canHandle(eventMessage, segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegularProcessing(Segment segment, TrackedEventMessage<?> nextMessage) {
        return nextMessage != null
                && isRegularProcessing(segment, processingSegments(nextMessage.trackingToken(), segment));
    }

    /**
     * Indicates if the given {@code processingSegments} should be considered normal processing for the given {@code
     * segment}. This is the case if only given {@code segment} is included in the {@code processingSegments}.
     *
     * @param segment            The segment assigned to this thread for processing
     * @param processingSegments The segments for which a received event should be processed
     * @return {@code true} if this is considered regular processor, {@code false} if this event should be treated
     * specially (in its own batch)
     */
    private boolean isRegularProcessing(Segment segment, Collection<Segment> processingSegments) {
        return processingSegments.size() == 1 && Objects.equals(processingSegments.iterator().next(), segment);
    }

    private void checkSegmentCaughtUp(Segment segment, BlockingStream<TrackedEventMessage<?>> eventStream) {
        if (!eventStream.hasNextAvailable()) {
            updateActiveSegments(() -> activeSegments.computeIfPresent(segment.getSegmentId(), (k, v) -> v.caughtUp()));
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
        if (trackingToken instanceof WrappedToken) {
            return new WrappedMessageStream(
                    (WrappedToken) trackingToken,
                    messageSource.openStream(WrappedToken.unwrapLowerBound(trackingToken))
            );
        }
        return messageSource.openStream(WrappedToken.unwrapLowerBound(trackingToken));
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
     * override any previous blacklist duration that existed for this segment. Providing a negative value will allow the
     * segment to be immediately claimed.
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
        return segmentReleaseDeadlines.getOrDefault(segmentId, Long.MIN_VALUE) < System.currentTimeMillis();
    }

    /**
     * Resets tokens to their initial state. This effectively causes a replay.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the same
     * logical processor that may be running in the cluster. Failure to do so will cause the reset to fail, as a
     * processor can only reset the tokens if it is able to claim them all.
     */
    public void resetTokens() {
        resetTokens(initialTrackingTokenBuilder);
    }

    /**
     * Resets tokens to their initial state. This effectively causes a replay. The given {@code resetContext} will be
     * used to support the (optional) reset operation in an Event Handling Component.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the same
     * logical processor that may be running in the cluster. Failure to do so will cause the reset to fail, as a
     * processor can only reset the tokens if it is able to claim them all.
     *
     * @param resetContext a {@code R} used to support the reset operation
     * @param <R>          the type of the provided {@code resetContext}
     */
    public <R> void resetTokens(R resetContext) {
        resetTokens(initialTrackingTokenBuilder, resetContext);
    }

    /**
     * Reset tokens to the position as return by the given {@code initialTrackingTokenSupplier}. This effectively causes
     * a replay since that position.
     * <p>
     * Note that the new token must represent a position that is <em>before</em> the current position of the processor.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the same
     * logical processor that may be running in the cluster. Failure to do so will cause the reset to fail, as a
     * processor can only reset the tokens if it is able to claim them all.
     *
     * @param initialTrackingTokenSupplier A function returning the token representing the position to reset to
     */
    public void resetTokens(
            Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenSupplier) {
        resetTokens(initialTrackingTokenSupplier.apply(messageSource));
    }

    /**
     * Reset tokens to the position as return by the given {@code initialTrackingTokenSupplier}. This effectively causes
     * a replay since that position. The given {@code resetContext} will be used to support the (optional) reset
     * operation in an Event Handling Component.
     * <p>
     * Note that the new token must represent a position that is <em>before</em> the current position of the processor.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the same
     * logical processor that may be running in the cluster. Failure to do so will cause the reset to fail, as a
     * processor can only reset the tokens if it is able to claim them all.
     *
     * @param initialTrackingTokenSupplier A function returning the token representing the position to reset to
     * @param resetContext                 a {@code R} used to support the reset operation
     * @param <R>                          the type of the provided {@code resetContext}
     */
    public <R> void resetTokens(
            Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenSupplier,
            R resetContext
    ) {
        resetTokens(initialTrackingTokenSupplier.apply(messageSource), resetContext);
    }

    /**
     * Resets tokens to the given {@code startPosition}. This effectively causes a replay of events since that
     * position.
     * <p>
     * Note that the new token must represent a position that is <em>before</em> the current position of the processor.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the same
     * logical processor that may be running in the cluster. Failure to do so will cause the reset to fail, as a
     * processor can only reset the tokens if it is able to claim them all.
     *
     * @param startPosition the token representing the position to reset the processor to
     */
    public void resetTokens(TrackingToken startPosition) {
        resetTokens(startPosition, null);
    }

    /**
     * Resets tokens to the given {@code startPosition}. This effectively causes a replay of events since that position.
     * The given {@code resetContext} will be used to support the (optional) reset operation in an Event Handling
     * Component.
     * <p>
     * Note that the new token must represent a position that is <em>before</em> the current position of the processor.
     * <p>
     * Before attempting to reset the tokens, the caller must stop this processor, as well as any instances of the same
     * logical processor that may be running in the cluster. Failure to do so will cause the reset to fail, as a
     * processor can only reset the tokens if it is able to claim them all.
     *
     * @param startPosition the token representing the position to reset the processor to
     * @param resetContext  a {@code R} used to support the reset operation
     * @param <R>           the type of the provided {@code resetContext}
     */
    public <R> void resetTokens(TrackingToken startPosition, R resetContext) {
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
            eventHandlerInvoker().performReset(resetContext);

            for (int i = 0; i < tokens.length; i++) {
                tokenStore.storeToken(ReplayToken.createReplayToken(tokens[i], startPosition), getName(), segments[i]);
            }
        });
    }

    /**
     * Indicates whether this tracking processor supports a "reset". Generally, a reset is supported if at least one of
     * the event handlers assigned to this processor supports it, and no handlers explicitly prevent the resets.
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
     * Returns the {@link StreamableMessageSource} this processor is using
     *
     * @return {@link StreamableMessageSource}
     */
    public StreamableMessageSource<? extends TrackedEventMessage<?>> getMessageSource() {
        return messageSource;
    }

    /**
     * Shuts down the processor. Blocks until shutdown is complete.
     */
    @Override
    public void shutDown() {
        setShutdownState();
        awaitTermination();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Will be shutdown on the {@link Phase#INBOUND_EVENT_CONNECTORS} phase.
     */
    @Override
    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public CompletableFuture<Void> shutdownAsync() {
        return super.shutdownAsync();
    }

    private void setShutdownState() {
        if (state.getAndSet(State.SHUT_DOWN).isRunning()) {
            logger.info("Shutdown state set for Processor '{}'.", getName());
        }
    }

    private void awaitTermination() {
        if (activeProcessorThreads() > 0) {
            logger.info("Processor '{}' awaiting termination...", getName());
            try {
                while (activeProcessorThreads() > 0) {
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
     * Returns the status for each of the Segments processed by the current processor. The key of the map represents the
     * SegmentID processed by this instance. The values of the returned Map represent the last known status of that
     * Segment.
     * <p>
     * Note that the returned Map is unmodifiable, but does reflect any changes made to the status as the processor is
     * processing Events.
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
     * Starts workers for a number of segments. When only the {@link Segment#ROOT_SEGMENT root } segment {@link
     * TokenStore#fetchSegments(String) exists} in the  TokenStore, it will be split in multiple segments as configured
     * by the {@link TrackingEventProcessorConfiguration#andInitialSegmentsCount(int)}, otherwise the existing segments
     * in the TokenStore will be used.
     * <p/>
     * An attempt will be made to instantiate a worker for each segment. This will succeed when the number of threads
     * matches the requested segments. The number of active threads can be configured with {@link
     * TrackingEventProcessorConfiguration#forParallelProcessing(int)}. When insufficient threads are available to serve
     * the number of segments, it will result in some segments not being processed.
     */
    protected void startSegmentWorkers() {
        threadFactory.newThread(new WorkerLauncher()).start();
    }

    /**
     * Instructs the current Thread to sleep until the given deadline. This method may be overridden to check for flags
     * that have been set to return earlier than the given deadline.
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

    private void updateActiveSegments(Runnable segmentUpdater) {
        Map<Integer, EventTrackerStatus> oldSegments = new HashMap<>(activeSegments);
        segmentUpdater.run();
        Map<Integer, EventTrackerStatus> newSegments = new HashMap<>(activeSegments);

        Map<Integer, EventTrackerStatus> adjustedSegments = compareSegments(oldSegments, newSegments);
        if (adjustedSegments.size() > 0) {
            trackerStatusChangeListener.onEventTrackerStatusChange(adjustedSegments);
        }
    }

    private Map<Integer, EventTrackerStatus> compareSegments(Map<Integer, EventTrackerStatus> oldSegments,
                                                             Map<Integer, EventTrackerStatus> newSegments) {
        Set<Integer> newSegmentIds = new HashSet<>(newSegments.keySet());
        newSegmentIds.removeAll(oldSegments.keySet());
        if (!newSegmentIds.isEmpty()) {
            return newSegmentIds.stream()
                                .collect(Collectors.toMap(
                                        segmentId -> segmentId,
                                        segmentId -> new AddedTrackerStatus(newSegments.get(segmentId))
                                ));
        }

        Set<Integer> oldSegmentIds = new HashSet<>(oldSegments.keySet());
        oldSegmentIds.removeAll(newSegments.keySet());
        if (!oldSegmentIds.isEmpty()) {
            return oldSegmentIds.stream()
                                .collect(Collectors.toMap(
                                        segmentId -> segmentId,
                                        segmentId -> new RemovedTrackerStatus(oldSegments.get(segmentId))
                                ));
        }

        Map<Integer, EventTrackerStatus> updatedSegments = new HashMap<>();
        for (Map.Entry<Integer, EventTrackerStatus> oldSegment : oldSegments.entrySet()) {
            Integer segmentId = oldSegment.getKey();
            EventTrackerStatus newSegment = newSegments.get(segmentId);
            if (oldSegment.getValue().isDifferent(newSegment, trackerStatusChangeListener.validatePositions())) {
                updatedSegments.put(segmentId, newSegment);
            }
        }
        return updatedSegments;
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

    private static final class TrackerStatus implements EventTrackerStatus {

        private final Segment segment;
        private final boolean caughtUp;
        private final TrackingToken trackingToken;
        private final Throwable errorState;

        private TrackerStatus(Segment segment, TrackingToken trackingToken) {
            this(segment, false, trackingToken, null);
        }

        private TrackerStatus(Segment segment, boolean caughtUp, TrackingToken trackingToken, Throwable errorState) {
            this.segment = segment;
            this.caughtUp = caughtUp;
            this.trackingToken = trackingToken;
            this.errorState = errorState;
        }

        private TrackerStatus caughtUp() {
            if (caughtUp) {
                return this;
            }
            return new TrackerStatus(segment, true, trackingToken, null);
        }

        private TrackerStatus advancedTo(TrackingToken trackingToken) {
            if (Objects.equals(this.trackingToken, trackingToken)) {
                return this;
            }
            return new TrackerStatus(segment, caughtUp, trackingToken, null);
        }

        private TrackerStatus markError(Throwable error) {
            return new TrackerStatus(segment, caughtUp, trackingToken, error);
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
            return ReplayToken.isReplay(trackingToken);
        }

        @Override
        public boolean isMerging() {
            return MergedTrackingToken.isMergeInProgress(trackingToken);
        }

        @Override
        public OptionalLong mergeCompletedPosition() {
            return MergedTrackingToken.mergePosition(trackingToken);
        }

        @Override
        public TrackingToken getTrackingToken() {
            return WrappedToken.unwrapLowerBound(trackingToken);
        }

        @Override
        public boolean isErrorState() {
            return errorState != null;
        }

        @Override
        public Throwable getError() {
            return errorState;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public OptionalLong getCurrentPosition() {
            if (isReplaying()) {
                return WrappedToken.unwrap(trackingToken, ReplayToken.class)
                                   .map(ReplayToken::position)
                                   .orElse(OptionalLong.empty());
            }

            if (isMerging()) {
                return WrappedToken.unwrap(trackingToken, MergedTrackingToken.class)
                                   .map(MergedTrackingToken::position)
                                   .orElse(OptionalLong.empty());
            }

            return (trackingToken == null) ? OptionalLong.empty() : trackingToken.position();
        }

        @Override
        public OptionalLong getResetPosition() {
            return ReplayToken.getTokenAtReset(trackingToken);
        }

        private TrackingToken getInternalTrackingToken() {
            return trackingToken;
        }

        /**
         * Splits the current status object to reflect the status of their underlying segments being split.
         *
         * @return an array with two status object, representing the status of the split segments.
         */
        public TrackerStatus[] split() {
            Segment[] newSegments = segment.split();
            TrackingToken tokenAtReset = null;
            TrackingToken workingToken = trackingToken;
            TrackingToken[] splitTokens = new TrackingToken[2];
            if (workingToken instanceof ReplayToken) {
                tokenAtReset = ((ReplayToken) workingToken).getTokenAtReset();
                workingToken = ((ReplayToken) workingToken).lowerBound();
            }
            if (workingToken instanceof MergedTrackingToken) {
                splitTokens[0] = ((MergedTrackingToken) workingToken).lowerSegmentToken();
                splitTokens[1] = ((MergedTrackingToken) workingToken).upperSegmentToken();
            } else {
                splitTokens[0] = workingToken;
                splitTokens[1] = workingToken;
            }

            if (tokenAtReset != null) {
                // we were in a replay. Need to re-initialize the replay wrapper
                splitTokens[0] = ReplayToken.createReplayToken(tokenAtReset, splitTokens[0]);
                splitTokens[1] = ReplayToken.createReplayToken(tokenAtReset, splitTokens[1]);
            }
            TrackerStatus[] newStatus = new TrackerStatus[2];
            newStatus[0] = new TrackerStatus(newSegments[0], splitTokens[0]);
            newStatus[1] = new TrackerStatus(newSegments[1], splitTokens[1]);
            return newStatus;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TrackerStatus that = (TrackerStatus) o;
            return caughtUp == that.caughtUp &&
                    Objects.equals(segment, that.segment) &&
                    Objects.equals(trackingToken, that.trackingToken) &&
                    Objects.equals(errorState, that.errorState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(segment, caughtUp, trackingToken, errorState);
        }

        @Override
        public String toString() {
            return "TrackerStatus{" +
                    "segment=" + getSegment() +
                    ", caughtUp=" + isCaughtUp() +
                    ", replaying=" + isReplaying() +
                    ", merging=" + isMerging() +
                    ", errorState=" + isErrorState() +
                    ", error=" + getError() +
                    ", trackingToken=" + getTrackingToken() +
                    ", currentPosition=" + getCurrentPosition() +
                    ", resetPosition=" + getResetPosition() +
                    ", mergeCompletedPosition=" + mergeCompletedPosition()
                    + "}";
        }
    }

    /**
     * Builder class to instantiate a {@link TrackingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the {@link
     * ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a {@link
     * NoOpMessageMonitor} and the {@link TrackingEventProcessorConfiguration} to a {@link
     * TrackingEventProcessorConfiguration#forSingleThreadedProcessing()} call. The Event Processor {@code name}, {@link
     * EventHandlerInvoker}, {@link StreamableMessageSource}, {@link TokenStore} and {@link TransactionManager} are
     * <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends AbstractEventProcessor.Builder {

        private StreamableMessageSource<TrackedEventMessage<?>> messageSource;
        private TokenStore tokenStore;
        private TransactionManager transactionManager;
        private TrackingEventProcessorConfiguration trackingEventProcessorConfiguration =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing();
        private Boolean storeTokenBeforeProcessing;

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
         * <p/>
         * Note that setting this value influences the behavior for storing tokens either at the start or at the end of
         * a batch.
         * If a TransactionManager other than a {@link NoTransactionManager} is configured, the default behavior is to
         * store the last token of the Batch to the Token Store before processing of events begins. If the
         * {@link NoTransactionManager} is provided, the default is to extend the claim at the start of the unit of
         * work, and update the token after processing Events.
         * When tokens are stored at the start of a batch, a claim extension will be sent at the end of the batch if
         * processing that batch took longer than the {@link
         * TrackingEventProcessorConfiguration#andEventAvailabilityTimeout(long, TimeUnit) tokenClaimUpdateInterval}.
         * <p>
         * Use {@link #storingTokensAfterProcessing()} to force storage of tokens at the end of a batch.
         *
         * @param transactionManager the {@link TransactionManager} used when processing {@link EventMessage}s
         *
         * @return the current Builder instance, for fluent interfacing
         * @see #storingTokensAfterProcessing()
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            if (storeTokenBeforeProcessing == null) {
                storeTokenBeforeProcessing = transactionManager != NoTransactionManager.instance();
            }
            return this;
        }

        /**
         * Sets the {@link TrackingEventProcessorConfiguration} containing the fine grained configuration options for a
         * {@link TrackingEventProcessor}. Defaults to a {@link TrackingEventProcessorConfiguration#forSingleThreadedProcessing()}
         * call.
         *
         * @param trackingEventProcessorConfiguration the {@link TrackingEventProcessorConfiguration} containing the
         *                                            fine grained configuration options for a {@link
         *                                            TrackingEventProcessor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder trackingEventProcessorConfiguration(
                TrackingEventProcessorConfiguration trackingEventProcessorConfiguration) {
            assertNonNull(trackingEventProcessorConfiguration, "TrackingEventProcessorConfiguration may not be null");
            this.trackingEventProcessorConfiguration = trackingEventProcessorConfiguration;
            return this;
        }

        /**
         * Set this processor to store Tracking Tokens only at the end of processing. This has an impact on performance,
         * as the processor will need to extend the claim at the start of the process, and then update the token at the
         * end. This causes 2 round-trips to the Token Store per batch of events.
         * <p>
         * Enable this when a Token Store cannot participate in a transaction, or when at-most-once-delivery semantics
         * are desired.
         * <p>
         * The default behavior is to store the last token of the Batch to the Token Store before processing of events
         * begins, if a TransactionManager is configured. If the {@link NoTransactionManager} is provided, the default
         * is to extend the claim at the start of the unit of work, and update the token after processing Events.
         * When tokens are stored at the start of a batch, a claim extension will be sent at the end of the batch if
         * processing that batch took longer than the {@link
         * TrackingEventProcessorConfiguration#andEventAvailabilityTimeout(long, TimeUnit) tokenClaimUpdateInterval}.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder storingTokensAfterProcessing() {
            this.storeTokenBeforeProcessing = false;
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
            if (storeTokenBeforeProcessing == null) {
                storeTokenBeforeProcessing = false;
            }
            assertNonNull(messageSource, "The StreamableMessageSource is a hard requirement and should be provided");
            assertNonNull(tokenStore, "The TokenStore is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }

    private abstract class Instruction implements Runnable {

        private final CompletableFuture<Boolean> result;

        public Instruction(CompletableFuture<Boolean> result) {
            this.result = result;
        }

        public void run() {
            try {
                executeWithRetry(() -> transactionManager.executeInTransaction(() -> result.complete(runSafe())),
                                 re -> ExceptionUtils.findException(re, UnableToClaimTokenException.class).isPresent(),
                                 tokenClaimInterval, MILLISECONDS, 10);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }

        protected abstract boolean runSafe();
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
                throw e;
            } finally {
                updateActiveSegments(() -> activeSegments.remove(segment.getSegmentId()));
                logger.info("Worker for segment {} stopped.", segment);
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
                    logger.debug("Fetch Segments failed because:", e);
                    doSleepFor(SECONDS.toMillis(waitTime));
                    waitTime = Math.min(waitTime * 2, 60);

                    continue;
                }

                // Submit segmentation workers matching the size of our thread pool (-1 for the current dispatcher).
                // Keep track of the last processed segments...
                TrackingSegmentWorker workingInCurrentThread = null;
                for (int i = 0; i < tokenStoreCurrentSegments.length && availableThreads.get() > 0; i++) {
                    int segmentId = tokenStoreCurrentSegments[i];

                    if (!activeSegments.containsKey(segmentId)
                            && canClaimSegment(segmentId)) {
                        try {
                            transactionManager.executeInTransaction(() -> {
                                TrackingToken token = tokenStore.fetchToken(processorName, segmentId);
                                int[] segmentIds = tokenStore.fetchSegments(processorName);
                                Segment segment = Segment.computeSegment(segmentId, segmentIds);
                                logger.info("Worker assigned to segment {} for processing", segment);
                                updateActiveSegments(
                                        () -> activeSegments.putIfAbsent(segmentId, new TrackerStatus(segment, token))
                                );
                            });
                        } catch (UnableToClaimTokenException ucte) {
                            // When not able to claim a token for a given segment, we skip the
                            logger.debug("Unable to claim the token for segment: {}. It is owned by another process",
                                         segmentId);
                            updateActiveSegments(() -> activeSegments.remove(segmentId));
                            continue;
                        } catch (Exception e) {
                            updateActiveSegments(() -> activeSegments.remove(segmentId));
                            if (AxonNonTransientException.isCauseOf(e)) {
                                logger.error(
                                        "An unrecoverable error has occurred wile attempting to claim a token "
                                                + "for segment: {}. Shutting down processor [{}].",
                                        segmentId,
                                        getName(),
                                        e
                                );
                                state.set(State.PAUSED_ERROR);
                                break;
                            }
                            logger.info(
                                    "An error occurred while attempting to claim a token for segment: {}. "
                                            + "Will retry later...",
                                    segmentId,
                                    e
                            );
                            break;
                        }

                        TrackingSegmentWorker trackingSegmentWorker =
                                new TrackingSegmentWorker(activeSegments.get(segmentId).getSegment());
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

    private static class WrappedMessageStream implements BlockingStream<TrackedEventMessage<?>> {

        private final BlockingStream<TrackedEventMessage<?>> delegate;
        private WrappedToken lastToken;

        public WrappedMessageStream(WrappedToken token, BlockingStream<TrackedEventMessage<?>> delegate) {
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
            this.lastToken = trackedEventMessage.trackingToken() instanceof WrappedToken
                    ? (WrappedToken) trackedEventMessage.trackingToken()
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

        public <T> TrackedEventMessage<T> alterToken(TrackedEventMessage<T> message) {
            if (lastToken == null) {
                return message;
            }
            return message.withTrackingToken(lastToken.advancedTo(message.trackingToken()));
        }
    }

    private class SplitSegmentInstruction extends Instruction {

        private final int segmentId;

        public SplitSegmentInstruction(CompletableFuture<Boolean> result, int segmentId) {
            super(result);
            this.segmentId = segmentId;
        }

        @Override
        protected boolean runSafe() {
            logger.info("Processing split instruction for segment [{}] in processor [{}]", segmentId, getName());
            TrackerStatus status = activeSegments.get(segmentId);
            TrackerStatus[] newStatus = status.split();
            int newSegmentId = newStatus[1].getSegment().getSegmentId();
            tokenStore.initializeSegment(newStatus[1].getTrackingToken(), getName(), newSegmentId);
            updateActiveSegments(() -> activeSegments.put(segmentId, newStatus[0]));
            return true;
        }
    }

    private class MergeSegmentInstruction extends Instruction {

        private final int segmentId;
        private final int otherSegment;

        public MergeSegmentInstruction(CompletableFuture<Boolean> result, int ownedSegment, int otherSegment) {
            super(result);
            this.segmentId = ownedSegment;
            this.otherSegment = otherSegment;
        }

        @Override
        protected boolean runSafe() {
            logger.info("Processing merge instruction for segments [{}] and [{}] in processor [{}]",
                        segmentId, otherSegment, getName());
            releaseSegment(otherSegment);
            while (activeSegments.containsKey(otherSegment)) {
                Thread.yield();
            }
            TrackerStatus status = activeSegments.get(segmentId);
            TrackingToken otherToken = tokenStore.fetchToken(getName(), otherSegment);
            Segment segmentToMergeWith = Segment.computeSegment(otherSegment, tokenStore.fetchSegments(getName()));
            if (!status.getSegment().isMergeableWith(segmentToMergeWith)) {
                tokenStore.releaseClaim(getName(), otherSegment);
                return false;
            }
            Segment newSegment = status.getSegment().mergedWith(segmentToMergeWith);
            //we want to keep the token with the segmentId obtained by the merge operation, and to delete the other
            int tokenToDelete = (newSegment.getSegmentId() == segmentId) ? otherSegment : segmentId;
            tokenStore.deleteToken(getName(), tokenToDelete);

            TrackingToken mergedToken = otherSegment < segmentId
                    ? new MergedTrackingToken(otherToken, status.getInternalTrackingToken())
                    : new MergedTrackingToken(status.getInternalTrackingToken(), otherToken);

            tokenStore.storeToken(mergedToken, getName(), newSegment.getSegmentId());
            return true;
        }
    }
}
