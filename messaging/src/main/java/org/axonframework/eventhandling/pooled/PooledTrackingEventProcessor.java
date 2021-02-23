package org.axonframework.eventhandling.pooled;

import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.AbstractEventProcessor;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * A {@link StreamingEventProcessor} implementation which pools it's resources to enhance processing speed. It utilizes
 * a {@link Coordinator} as the means to stream events from a {@link StreamableMessageSource} and creates so called work
 * packages. Every work package is in charge of a {@link Segment} of the entire event stream. It is the {@code
 * Coordinator}'s job to retrieve the events from the source and provide the events to all the work packages it is in
 * charge of.
 * <p>
 * This approach utilizes two threads pools. One to retrieve the events to provide them to the work packages and another
 * to actual handle the events. Respectively, the coordinator thread pool and the work package thread pool. It is this
 * approach which allows for greater parallelization and processing speed than the {@link
 * org.axonframework.eventhandling.TrackingEventProcessor}.
 * <p>
 * If no {@link TrackingToken}s are present for this processor, the {@code PooledTrackingEventProcessor} will initialize
 * them in a given segment count. By default it will create {@code 32} segments, which can be configured through the
 * {@link Builder#initialSegmentCount(int)}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 4.5
 */
public class PooledTrackingEventProcessor extends AbstractEventProcessor implements StreamingEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;
    private final ScheduledExecutorService workerExecutor;
    private final Coordinator coordinator;
    private final int initialSegmentCount;
    private final Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken;
    private final long tokenClaimInterval;
    private final int maxCapacity;
    private final long claimExtensionThreshold;

    private final AtomicReference<String> tokenStoreIdentifier = new AtomicReference<>();
    private final Map<Integer, TrackerStatus> processingStatus = new ConcurrentHashMap<>();

    /**
     * Instantiate a Builder to be able to create a {@link PooledTrackingEventProcessor}.
     * <p>
     * Upon initialization of this builder, the following fields are defaulted:
     * <ul>
     *     <li>The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}.</li>
     *     <li>The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}.</li>
     *     <li>The {@link MessageMonitor} defaults to a {@link NoOpMessageMonitor}.</li>
     *     <li>A function building a single threaded {@link ScheduledExecutorService} used by the coordinator of this processor, based on this processor's name.</li>
     *     <li>A function building a single threaded {@link ScheduledExecutorService} given to the work packages created by this processor, based on this processor's name</li>
     *     <li>The {@code initialSegmentCount} defaults to {@code 32}.</li>
     *     <li>The {@code initialToken} function defaults to {@link StreamableMessageSource#createTailToken()}.</li>
     *     <li>The {@code tokenClaimInterval} defaults to {@code 5000} milliseconds.</li>
     *     <li>The {@code maxCapacity} (used by {@link #maxCapacity()}) defaults to {@link Short#MAX_VALUE}.</li>
     *     <li>The {@code claimExtensionThreshold} defaults to {@code 5000} milliseconds.</li>
     * </ul>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     *     <li>The name of this {@link EventProcessor}.</li>
     *     <li>An {@link EventHandlerInvoker} which will be given the events handled by this processor</li>
     *     <li>A {@link StreamableMessageSource} used to retrieve events.</li>
     *     <li>A {@link TokenStore} to store the progress of this processor in.</li>
     *     <li>A {@link TransactionManager} to perform all event handling inside transactions.</li>
     * </ul>
     *
     * @return a Builder to be able to create a {@link PooledTrackingEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link PooledTrackingEventProcessor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert the following for their presence prior to constructing this processor:
     * <ul>
     *     <li>The Event Processor's {@code name}.</li>
     *     <li>An {@link EventHandlerInvoker}.</li>
     *     <li>A {@link StreamableMessageSource}.</li>
     *     <li>A {@link TokenStore}.</li>
     *     <li>A {@link TransactionManager}.</li>
     * </ul>
     * If any of these is not present or does no comply to the requirements an {@link AxonConfigurationException} is thrown.
     *
     * @param builder the {@link Builder} used to instantiate a {@link PooledTrackingEventProcessor} instance
     */
    protected PooledTrackingEventProcessor(PooledTrackingEventProcessor.Builder builder) {
        super(builder);
        this.name = builder.name();
        this.messageSource = builder.messageSource;
        this.tokenStore = builder.tokenStore;
        this.transactionManager = builder.transactionManager;
        this.workerExecutor = builder.workerExecutorBuilder.apply(name);
        this.initialSegmentCount = builder.initialSegmentCount;
        this.initialToken = builder.initialToken;
        this.tokenClaimInterval = builder.tokenClaimInterval;
        this.maxCapacity = builder.maxCapacity;
        this.claimExtensionThreshold = builder.claimExtensionThreshold;

        this.coordinator = new Coordinator(
                name, messageSource, tokenStore, transactionManager,
                builder.coordinatorExecutorBuilder.apply(name), this::spawnWorker, this::statusUpdater,
                tokenClaimInterval
        );
    }

    @StartHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    @Override
    public void start() {
        logger.info("Starting PooledTrackingEventProcessor [{}].", name);
        transactionManager.executeInTransaction(() -> {
            int[] ints = tokenStore.fetchSegments(name);
            if (ints == null || ints.length == 0) {
                logger.info("Initializing segments for processor [{}] ({} segments)", name, initialSegmentCount);
                tokenStore.initializeTokenSegments(name, initialSegmentCount, initialToken.apply(messageSource));
            }
        });
        coordinator.start();
    }

    @Override
    public void shutDown() {
        shutdownAsync().join();
    }

    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    @Override
    public CompletableFuture<Void> shutdownAsync() {
        logger.info("Stopping processor [{}]", name);
        return coordinator.stop();
    }

    @Override
    public boolean isRunning() {
        return coordinator.isRunning();
    }

    @Override
    public boolean isError() {
        return coordinator.isError();
    }

    @Override
    public String getTokenStoreIdentifier() {
        return tokenStoreIdentifier.updateAndGet(i -> i != null ? i : calculateIdentifier());
    }

    private String calculateIdentifier() {
        return transactionManager.fetchInTransaction(
                () -> tokenStore.retrieveStorageIdentifier().orElse("--unknown--")
        );
    }

    @Override
    public void releaseSegment(int segmentId) {
        releaseSegment(segmentId, tokenClaimInterval * 2, MILLISECONDS);
    }

    @Override
    public void releaseSegment(int segmentId, long releaseDuration, TimeUnit unit) {
        coordinator.releaseUntil(
                segmentId, GenericEventMessage.clock.instant().plusMillis(unit.toMillis(releaseDuration))
        );
    }

    @Override
    public CompletableFuture<Boolean> splitSegment(int segmentId) {
        if (!tokenStore.requiresExplicitSegmentInitialization()) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            result.completeExceptionally(new UnsupportedOperationException(
                    "TokenStore must require explicit initialization to safely split tokens."
            ));
            return result;
        }

        return coordinator.splitSegment(segmentId);
    }

    @Override
    public CompletableFuture<Boolean> mergeSegment(int segmentId) {
        if (!tokenStore.requiresExplicitSegmentInitialization()) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            result.completeExceptionally(new UnsupportedOperationException(
                    "TokenStore must require explicit initialization to safely merge tokens."
            ));
            return result;
        }

        return coordinator.mergeSegment(segmentId);
    }

    @Override
    public boolean supportsReset() {
        return eventHandlerInvoker().supportsReset();
    }

    @Override
    public void resetTokens() {
        resetTokens(initialToken);
    }

    @Override
    public <R> void resetTokens(R resetContext) {
        resetTokens(initialToken, resetContext);
    }

    @Override
    public void resetTokens(
            Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenSupplier
    ) {
        resetTokens(initialTrackingTokenSupplier.apply(messageSource));
    }

    @Override
    public <R> void resetTokens(
            Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenSupplier,
            R resetContext
    ) {
        resetTokens(initialTrackingTokenSupplier.apply(messageSource), resetContext);
    }

    @Override
    public void resetTokens(TrackingToken startPosition) {
        resetTokens(startPosition, null);
    }

    @Override
    public <R> void resetTokens(TrackingToken startPosition, R resetContext) {
        Assert.state(supportsReset(), () -> "The handlers assigned to this Processor do not support a reset.");
        Assert.state(!isRunning(), () -> "The Processor must be shut down before triggering a reset.");

        transactionManager.executeInTransaction(() -> {
            // Find all segments and fetch all tokens
            int[] segments = tokenStore.fetchSegments(getName());
            TrackingToken[] tokens = Arrays.stream(segments)
                                           .mapToObj(segment -> tokenStore.fetchToken(getName(), segment))
                                           .toArray(TrackingToken[]::new);
            // Perform the reset on the EventHandlerInvoker
            eventHandlerInvoker().performReset(resetContext);
            // Update all tokens towards ReplayTokens
            IntStream.range(0, tokens.length)
                     .forEach(i -> tokenStore.storeToken(
                             ReplayToken.createReplayToken(tokens[i], startPosition), getName(), segments[i]
                     ));
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * The maximum capacity of the {@link PooledTrackingEventProcessor} defaults to {@link Short#MAX_VALUE}. If
     * required, this value can be adjusted through the {@link Builder#maxCapacity(int)} method.
     */
    @Override
    public int maxCapacity() {
        return maxCapacity;
    }

    @Override
    public Map<Integer, EventTrackerStatus> processingStatus() {
        return Collections.unmodifiableMap(processingStatus);
    }

    private WorkPackage spawnWorker(Segment segment, TrackingToken initialToken) {
        return WorkPackage.builder()
                          .name(name)
                          .tokenStore(tokenStore)
                          .transactionManager(transactionManager)
                          .executorService(workerExecutor)
                          .eventFilter(this::canHandle)
                          .batchProcessor(this::processInUnitOfWork)
                          .segment(segment)
                          .initialToken(initialToken)
                          .claimExtensionThreshold(claimExtensionThreshold)
                          .segmentStatusUpdater(singleStatusUpdater(
                                  segment.getSegmentId(), new TrackerStatus(segment, initialToken)
                          ))
                          .build();
    }

    /**
     * A {@link Consumer} of a {@link TrackerStatus} update method. To be used by a {@link WorkPackage} to update the
     * {@code TrackerStatus} of the {@link Segment} it is in charge of.
     *
     * @param segmentId     the {@link Segment} identifier for which the {@link TrackerStatus} should be updated
     * @param initialStatus the initial {@link TrackerStatus} if there's no {@code TrackerStatus} for the given {@code
     *                      segmentId}
     * @return a {@link Consumer} of a {@link TrackerStatus} update method
     */
    private Consumer<UnaryOperator<TrackerStatus>> singleStatusUpdater(int segmentId, TrackerStatus initialStatus) {
        return statusUpdater -> processingStatus.compute(
                segmentId,
                (s, status) -> statusUpdater.apply(status == null ? initialStatus : status)
        );
    }

    /**
     * Retrieves a {@link TrackerStatus} for the given {@code segmentId} for which the given {@code segmentUpdater}
     * should be invoked.
     *
     * @param segmentId      the {@link Segment} identifier who's the {@link TrackerStatus} should be updated
     * @param segmentUpdater the lambda which receives the current {@link TrackerStatus} and returns the updated {@code
     *                       TrackerStatus}
     */
    private void statusUpdater(int segmentId, UnaryOperator<TrackerStatus> segmentUpdater) {
        processingStatus.computeIfPresent(segmentId, (s, ts) -> segmentUpdater.apply(ts));
    }

    /**
     * Builder class to instantiate a {@link PooledTrackingEventProcessor}.
     * <p>
     * Upon initialization of this builder, the following fields are defaulted:
     * <ul>
     *     <li>The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}.</li>
     *     <li>The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}.</li>
     *     <li>The {@link MessageMonitor} defaults to a {@link NoOpMessageMonitor}.</li>
     *     <li>A function building a single threaded {@link ScheduledExecutorService} used by the coordinator of this processor, based on this processor's name.</li>
     *     <li>A function building a single threaded {@link ScheduledExecutorService} given to the work packages created by this processor, based on this processor's name</li>
     *     <li>The {@code initialSegmentCount} defaults to {@code 32}.</li>
     *     <li>The {@code initialToken} function defaults to {@link StreamableMessageSource#createTailToken()}.</li>
     *     <li>The {@code tokenClaimInterval} defaults to {@code 5000} milliseconds.</li>
     *     <li>The {@code maxCapacity} (used by {@link #maxCapacity()}) defaults to {@link Short#MAX_VALUE}.</li>
     *     <li>The {@code claimExtensionThreshold} defaults to {@code 5000} milliseconds.</li>
     * </ul>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     *     <li>The name of this {@link EventProcessor}.</li>
     *     <li>An {@link EventHandlerInvoker} which will be given the events handled by this processor</li>
     *     <li>A {@link StreamableMessageSource} used to retrieve events.</li>
     *     <li>A {@link TokenStore} to store the progress of this processor in.</li>
     *     <li>A {@link TransactionManager} to perform all event handling inside transactions.</li>
     * </ul>
     */
    public static class Builder extends AbstractEventProcessor.Builder {

        private StreamableMessageSource<TrackedEventMessage<?>> messageSource;
        private TokenStore tokenStore;
        private TransactionManager transactionManager;
        private Function<String, ScheduledExecutorService> coordinatorExecutorBuilder =
                n -> Executors.newScheduledThreadPool(1, new AxonThreadFactory("Coordinator[" + n + "]"));
        private Function<String, ScheduledExecutorService> workerExecutorBuilder =
                n -> Executors.newScheduledThreadPool(1, new AxonThreadFactory("WorkPackage[" + n + "]"));
        private int initialSegmentCount = 32;
        private Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken =
                StreamableMessageSource::createTailToken;
        private long tokenClaimInterval = 5000;
        private int maxCapacity = Short.MAX_VALUE;
        private long claimExtensionThreshold = 5000;

        protected Builder() {
            rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE);
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
         * Sets the {@link StreamableMessageSource} (e.g. the {@code EventStore}) which this {@link EventProcessor} will
         * track.
         *
         * @param messageSource the {@link StreamableMessageSource} (e.g. the {@code EventStore}) which this {@link
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
         * Specifies the {@link ScheduledExecutorService} used by the coordinator of this {@link
         * PooledTrackingEventProcessor}. Defaults to a {@code ScheduledExecutorService} with a single thread and an
         * {@link AxonThreadFactory} incorporating this processors name.
         *
         * @param coordinatorExecutor the {@link ScheduledExecutorService} to be used by the the coordinator of this
         *                            {@link PooledTrackingEventProcessor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder coordinatorExecutor(ScheduledExecutorService coordinatorExecutor) {
            assertNonNull(coordinatorExecutor, "The Coordinator's ScheduledExecutorService may not be null");
            this.coordinatorExecutorBuilder = ignored -> coordinatorExecutor;
            return this;
        }

        /**
         * Specifies the {@link ScheduledExecutorService} to be provided to the {@link WorkPackage}s created by this
         * {@link PooledTrackingEventProcessor}. Defaults to a {@code ScheduledExecutorService} with a single thread and
         * an {@link AxonThreadFactory} incorporating this processors name.
         *
         * @param workerExecutor the {@link ScheduledExecutorService} to be provided to the {@link WorkPackage}s created
         *                       by this {@link PooledTrackingEventProcessor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder workerExecutorService(ScheduledExecutorService workerExecutor) {
            assertNonNull(workerExecutor, "The Worker's ScheduledExecutorService may not be null");
            this.workerExecutorBuilder = ignored -> workerExecutor;
            return this;
        }

        /**
         * Sets the initial segment count used to create segments on start up. Only used whenever there are not segments
         * stored in the configured {@link TokenStore} upon start up of this {@link StreamingEventProcessor}. The given
         * value should at least be {@code 1}. Defaults to {@code 32}.
         *
         * @param initialSegmentCount an {@code int} specifying the initial segment count used to create segments on
         *                            start up
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder initialSegmentCount(int initialSegmentCount) {
            assertStrictPositive(initialSegmentCount, "The initial segment count should be a higher valuer than zero");
            this.initialSegmentCount = initialSegmentCount;
            return this;
        }

        /**
         * Specifies the {@link Function} used to generate the initial {@link TrackingToken}s. The function will be
         * given the configured {@link StreamableMessageSource}' so that its methods can be invoked for token creation.
         * Defaults to {@link StreamableMessageSource#createTailToken()}.
         *
         * @param initialToken a {@link Function} generating the initial {@link TrackingToken} based on a given {@link
         *                     StreamableMessageSource}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder initialToken(
                Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken
        ) {
            assertNonNull(initialToken, "The initial token builder Function may not be null");
            this.initialToken = initialToken;
            return this;
        }

        /**
         * Specifies the time in milliseconds the processor's coordinator should wait after a failed attempt to claim
         * any segments for processing. Generally, this means all segments are claimed. Defaults to {@code 5000}
         * milliseconds.
         *
         * @param tokenClaimInterval the time in milliseconds the processor's coordinator should wait after a failed
         *                           attempt to claim any segments for processing
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tokenClaimInterval(long tokenClaimInterval) {
            assertStrictPositive(tokenClaimInterval, "Token claim interval should be a higher valuer than zero");
            this.tokenClaimInterval = tokenClaimInterval;
            return this;
        }

        /**
         * Defines the maximum segment capacity of this {@link StreamingEventProcessor}, as will be returned by the
         * {@link #maxCapacity()}  method. Defaults to {@link Short#MAX_VALUE}.
         *
         * @param maxCapacity the maximum segment capacity of this {@link StreamingEventProcessor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder maxCapacity(int maxCapacity) {
            assertStrictPositive(maxCapacity, "Max capacity should be a higher valuer than zero");
            this.maxCapacity = maxCapacity;
            return this;
        }

        /**
         * Specifies a time in milliseconds the work packages of this processor should extend the claim on a {@link
         * TrackingToken}. The threshold will only be met in absence of regular event processing, since that updates the
         * {@code TrackingToken} automatically. Defaults to {@code 5000} milliseconds.
         *
         * @param claimExtensionThreshold a time in milliseconds the work packages of this processor should extend the
         *                                claim on a {@link TrackingToken}.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder claimExtensionThreshold(long claimExtensionThreshold) {
            assertStrictPositive(
                    claimExtensionThreshold, "The claim extension threshold should be a higher valuer than zero"
            );
            this.claimExtensionThreshold = claimExtensionThreshold;
            return this;
        }

        /**
         * Initializes a {@link PooledTrackingEventProcessor} as specified through this Builder.
         *
         * @return a {@link PooledTrackingEventProcessor} as specified through this Builder
         */
        public PooledTrackingEventProcessor build() {
            return new PooledTrackingEventProcessor(this);
        }

        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(messageSource, "The StreamableMessageSource is a hard requirement and should be provided");
            assertNonNull(tokenStore, "The TokenStore is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }

        /**
         * Returns the name of this {@link PooledTrackingEventProcessor}.
         *
         * @return the name of this {@link PooledTrackingEventProcessor}
         */
        protected String name() {
            return name;
        }
    }
}
