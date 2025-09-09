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

package org.axonframework.eventhandling.processors.streaming.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.errorhandling.ErrorHandler;
import org.axonframework.eventhandling.processors.errorhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.processors.streaming.StreamingEventProcessor;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.ReplayToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.tracing.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.tracing.EventProcessorSpanFactory;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.TrackingTokenSource;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * Configuration class for a {@link PooledStreamingEventProcessor}.
 * <p>
 * Upon initialization of this configuration, the following fields are defaulted:
 * <ul>
 *     <li>The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}.</li>
 *     <li>The {@link MessageMonitor} defaults to a {@link NoOpMessageMonitor}.</li>
 *     <li>The {@code initialSegmentCount} defaults to {@code 16}.</li>
 *     <li>The {@code initialToken} function defaults to a {@link ReplayToken} that starts streaming
 *          from the {@link StreamableEventSource#latestToken() tail} with the replay flag enabled until the
 *          {@link StreamableEventSource#firstToken() head} at the moment of initialization is reached.</li>
 *     <li>The {@code tokenClaimInterval} defaults to {@code 5000} milliseconds.</li>
 *     <li>The {@link MaxSegmentProvider} (used by {@link PooledStreamingEventProcessor#maxCapacity()}) defaults to {@link MaxSegmentProvider#maxShort()}.</li>
 *     <li>The {@code claimExtensionThreshold} defaults to {@code 5000} milliseconds.</li>
 *     <li>The {@code batchSize} defaults to {@code 1}.</li>
 *     <li>The {@link Clock} defaults to {@link GenericEventMessage#clock}.</li>
 *     <li>The {@link EventProcessorSpanFactory} defaults to a {@link DefaultEventProcessorSpanFactory} backed by a {@link org.axonframework.tracing.NoOpSpanFactory}.</li>
 *     <li>The {@code coordinatorExtendsClaims} defaults to a {@code false}.</li>
 * </ul>
 * The following fields of this configuration are <b>hard requirements</b> and as such should be provided:
 * <ul>
 *     <li>A {@link StreamableEventSource} used to retrieve events.</li>
 *     <li>A {@link TokenStore} to store the progress of this processor in.</li>
 *     <li>A {@link ScheduledExecutorService} to coordinate events and segment operations.</li>
 *     <li>A {@link ScheduledExecutorService} to process work packages.</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class PooledStreamingEventProcessorConfiguration extends EventProcessorConfiguration {

    private StreamableEventSource<? extends EventMessage> eventSource;
    private TokenStore tokenStore;
    private ScheduledExecutorService coordinatorExecutor;
    private ScheduledExecutorService workerExecutor;
    private int initialSegmentCount = 16;
    private Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
            es -> es.firstToken().thenApply(ReplayToken::createReplayToken);

    private long tokenClaimInterval = 5000;
    private MaxSegmentProvider maxSegmentProvider = MaxSegmentProvider.maxShort();
    private long claimExtensionThreshold = 5000;
    private int batchSize = 1;
    private Clock clock = GenericEventMessage.clock;
    private boolean coordinatorExtendsClaims = false;
    private Function<Set<QualifiedName>, EventCriteria> eventCriteriaProvider =
            (supportedEvents) -> EventCriteria.havingAnyTag().andBeingOneOfTypes(supportedEvents);
    private Consumer<? super EventMessage> ignoredMessageHandler = eventMessage -> messageMonitor.onMessageIngested(
            eventMessage).reportIgnored();

    /**
     * Constructs a new {@code PooledStreamingEventProcessorConfiguration} with default values.
     *
     * @param configuration The configuration, used to retrieve global default values, like
     *                      {@link MessageHandlerInterceptor MessageHandlerInterceptors}, from.
     */
    @Internal
    public PooledStreamingEventProcessorConfiguration(@Nonnull Configuration configuration) {
        super(configuration);
    }

    /**
     * Constructs a new {@code PooledStreamingEventProcessorConfiguration}.
     * <p>
     * This configuration will not have any of the default {@link MessageHandlerInterceptor MessageHandlerInterceptors}
     * for events. Please use {@link #PooledStreamingEventProcessorConfiguration(Configuration)} when those are desired.
     */
    public PooledStreamingEventProcessorConfiguration() {
        super(List.of());
    }

    /**
     * Constructs a new {@code PooledStreamingEventProcessorConfiguration} copying properties from the given
     * configuration.
     *
     * @param base The {@link EventProcessorConfiguration} to copy properties from.
     */
    @Internal
    public PooledStreamingEventProcessorConfiguration(@Nonnull EventProcessorConfiguration base) {
        super(base);
    }

    @Override
    public PooledStreamingEventProcessorConfiguration errorHandler(@Nonnull ErrorHandler errorHandler) {
        super.errorHandler(errorHandler);
        return this;
    }

    @Override
    public PooledStreamingEventProcessorConfiguration messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage> messageMonitor) {
        super.messageMonitor(messageMonitor);
        return this;
    }

    @Override
    public PooledStreamingEventProcessorConfiguration spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        super.spanFactory(spanFactory);
        return this;
    }

    @Override
    public PooledStreamingEventProcessorConfiguration unitOfWorkFactory(
            @Nonnull UnitOfWorkFactory unitOfWorkFactory) {
        super.unitOfWorkFactory(unitOfWorkFactory);
        return this;
    }

    /**
     * Sets the {@link StreamableEventSource} (e.g. the {@code EventStore}) which this {@link EventProcessor} will
     * track.
     *
     * @param eventSource The {@link StreamableEventSource} (e.g. the {@code EventStore}) which this
     *                    {@link EventProcessor} will track.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration eventSource(
            @Nonnull StreamableEventSource<? extends EventMessage> eventSource) {
        assertNonNull(eventSource, "StreamableEventSource may not be null");
        this.eventSource = eventSource;
        return this;
    }

    /**
     * Registers the given {@link EventMessage}-specific {@link MessageHandlerInterceptor} for the
     * {@link PooledStreamingEventProcessor} under construction.
     *
     * @param interceptor The {@link EventMessage}-specific {@link MessageHandlerInterceptor} to register for the
     *                    {@link PooledStreamingEventProcessor} under construction.
     * @return This {@code PooledStreamingEventProcessorConfiguration}, for fluent interfacing.
     */
    @Nonnull
    public PooledStreamingEventProcessorConfiguration withInterceptor(
            @Nonnull MessageHandlerInterceptor<EventMessage> interceptor
    ) {
        this.interceptors.add(interceptor);
        return this;
    }

    /**
     * Sets the {@link TokenStore} used to store and fetch event tokens that enable this {@link EventProcessor} to track
     * its progress.
     *
     * @param tokenStore The {@link TokenStore} used to store and fetch event tokens that enable this
     *                   {@link EventProcessor} to track its progress.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration tokenStore(@Nonnull TokenStore tokenStore) {
        assertNonNull(tokenStore, "TokenStore may not be null");
        this.tokenStore = tokenStore;
        return this;
    }

    /**
     * Specifies the {@link ScheduledExecutorService} used by the coordinator of this
     * {@link PooledStreamingEventProcessor}.
     *
     * @param coordinatorExecutor The {@link ScheduledExecutorService} to be used by the coordinator of this
     *                            {@link PooledStreamingEventProcessor}.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration coordinatorExecutor(
            @Nonnull ScheduledExecutorService coordinatorExecutor
    ) {
        assertNonNull(coordinatorExecutor, "The Coordinator's ScheduledExecutorService may not be null");
        this.coordinatorExecutor = coordinatorExecutor;
        return this;
    }

    /**
     * Specifies the {@link ScheduledExecutorService} to be provided to the {@link WorkPackage}s created by this
     * {@link PooledStreamingEventProcessor}.
     *
     * @param workerExecutor The {@link ScheduledExecutorService} to be provided to the {@link WorkPackage}s created by
     *                       this {@link PooledStreamingEventProcessor}.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration workerExecutor(
            @Nonnull ScheduledExecutorService workerExecutor
    ) {
        assertNonNull(workerExecutor, "The Worker's ScheduledExecutorService may not be null");
        this.workerExecutor = workerExecutor;
        return this;
    }

    /**
     * Sets the initial segment count used to create segments on start up. Only used whenever there are no segments
     * stored in the configured {@link TokenStore} upon start up of this {@link StreamingEventProcessor}. The given
     * value should at least be {@code 1}. Defaults to {@code 16}.
     *
     * @param initialSegmentCount The {@code int} specifying the initial segment count used to create segments on
     *                            startup.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration initialSegmentCount(int initialSegmentCount) {
        assertStrictPositive(initialSegmentCount, "The initial segment count should be a higher valuer than zero");
        this.initialSegmentCount = initialSegmentCount;
        return this;
    }

    /**
     * Specifies the {@link Function} used to generate the initial {@link TrackingToken}s. The function will be given
     * the configured {@link StreamableEventSource} so that its methods can be invoked for token creation.
     * <p>
     * Defaults to an automatic replay since the start of the stream.
     * <p>
     * More specifically, it defaults to a {@link ReplayToken} that starts streaming from the
     * {@link StreamableEventSource#latestToken() tail} with the replay flag enabled until the
     * {@link StreamableEventSource#firstToken() head} at the moment of initialization is reached.
     *
     * @param initialToken The {@link Function} generating the initial {@link TrackingToken} based on a given
     *                     {@link StreamableEventSource}.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration initialToken(
            @Nonnull Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken
    ) {
        assertNonNull(initialToken, "The initial token builder Function may not be null");
        this.initialToken = initialToken;
        return this;
    }

    /**
     * Specifies the time in milliseconds the processor's coordinator should wait after a failed attempt to claim any
     * segments for processing. Generally, this means all segments are claimed. Defaults to {@code 5000} milliseconds.
     *
     * @param tokenClaimInterval The time in milliseconds the processor's coordinator should wait after a failed attempt
     *                           to claim any segments for processing.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration tokenClaimInterval(long tokenClaimInterval) {
        assertStrictPositive(tokenClaimInterval, "Token claim interval should be a higher valuer than zero");
        this.tokenClaimInterval = tokenClaimInterval;
        return this;
    }

    /**
     * Sets the maximum number of segments this instance may claim.
     *
     * @param maxClaimedSegments The maximum number of segments this instance may claim.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration maxClaimedSegments(int maxClaimedSegments) {
        this.maxSegmentProvider = n -> maxClaimedSegments;
        return this;
    }

    /**
     * Defines the maximum number of segment this {@link StreamingEventProcessor} may claim per instance. Defaults to
     * {@link MaxSegmentProvider#maxShort()}.
     *
     * @param maxSegmentProvider A {@link MaxSegmentProvider} providing the maximum number segments this
     *                           {@link StreamingEventProcessor} may claim per instance.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration maxSegmentProvider(
            @Nonnull MaxSegmentProvider maxSegmentProvider
    ) {
        assertNonNull(maxSegmentProvider,
                      "The max segment provider may not be null. "
                              + "Provide a lambda of type (processorName: String) -> maxSegmentsToClaim");
        this.maxSegmentProvider = maxSegmentProvider;
        return this;
    }

    /**
     * Specifies a time in milliseconds the work packages of this processor should extend the claim on a
     * {@link TrackingToken}. The threshold will only be met in absence of regular event processing, since that updates
     * the {@code TrackingToken} automatically. Defaults to {@code 5000} milliseconds.
     *
     * @param claimExtensionThreshold The time in milliseconds the work packages of this processor should extend the
     *                                claim on a {@link TrackingToken}.
     * @return The current instance, for fluent interfacing
     */
    public PooledStreamingEventProcessorConfiguration claimExtensionThreshold(long claimExtensionThreshold) {
        assertStrictPositive(
                claimExtensionThreshold, "The claim extension threshold should be a higher valuer than zero"
        );
        this.claimExtensionThreshold = claimExtensionThreshold;
        return this;
    }

    /**
     * Specifies the number of events to be processed inside a single transaction. Defaults to a batch size of
     * {@code 1}.
     * <p>
     * Increasing this value with increase the processing speed dramatically, but requires certainty that the operations
     * performed during event handling can be rolled back.
     *
     * @param batchSize The number of events to be processed inside a single transaction.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration batchSize(int batchSize) {
        assertStrictPositive(batchSize, "The batch size should be a higher valuer than zero");
        this.batchSize = batchSize;
        return this;
    }

    /**
     * Defines the {@link Clock} used for time dependent operation by this {@link EventProcessor}. Used by the
     * {@link Coordinator} and {@link WorkPackage} threads to decide when to perform certain tasks, like updating
     * {@link TrackingToken} claims or when to unmark a {@link Segment} as "unclaimable". Defaults to
     * {@link GenericEventMessage#clock}.
     *
     * @param clock The {@link Clock} used for time dependent operation by this {@link EventProcessor}.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration clock(@Nonnull Clock clock) {
        assertNonNull(clock, "Clock may not be null");
        this.clock = clock;
        return this;
    }

    /**
     * Enables the {@link Coordinator} to {@link WorkPackage#extendClaimIfThresholdIsMet() extend the claims} of its
     * {@link WorkPackage WorkPackages}.
     * <p>
     * Enabling "coordinator claim extension" is an optimization as it relieves this effort from the
     * {@code WorkPackage}. Toggling this feature may be particularly useful whenever the event handling task of the
     * {@code WorkPackage} is <b>lengthy</b>. Either because of a hefty event handling component or because of a large
     * {@link PooledStreamingEventProcessorConfiguration#batchSize(int)}.
     * <p>
     * An example of a lengthy processing tasks is whenever handling a batch of events exceeds half the
     * {@code claimTimeout} of the {@link TokenStore}. The {@code claimTimeout} defaults to 10 seconds for all durable
     * {@code TokenStore} implementations.
     * <p>
     * In both scenarios, there's a window of opportunity that the {@code WorkPackage} is not fast enough in extending
     * the claim itself. Not being able to do so potentially causes token stealing by other instances of this
     * {@link PooledStreamingEventProcessor}, thus overburdening the overall event processing task.
     * <p>
     * Note that enabling this feature will result in more frequent invocation of the {@link TokenStore} to update the
     * tokens.
     *
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration enableCoordinatorClaimExtension() {
        this.coordinatorExtendsClaims = true;
        return this;
    }

    /**
     * Sets the handler, that is invoked when the event is ignored by all {@link WorkPackage}s this {@link Coordinator}
     * controls. Defaults to a no-op.
     *
     * @param ignoredMessageHandler The handler, that is invoked when the event is ignored by all * {@link WorkPackage}s
     *                              this {@link Coordinator} controls.
     * @return The current Builder instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration ignoredMessageHandler(
            Consumer<? super EventMessage> ignoredMessageHandler) {
        this.ignoredMessageHandler = ignoredMessageHandler;
        return this;
    }

    /**
     * Sets the function to build the {@link EventCriteria} used to filter events when opening the event source. The
     * function receives the set of supported event types from the assigned EventHandlingComponent.
     * <p>
     * <b>Intention:</b> This function is mainly intended to allow you to specify the tags for filtering or to
     * build more complex criteria. For example, if not all supported event types share the same tag, you may use
     * {@link EventCriteria#either(EventCriteria...)} to construct a disjunction of criteria for different event types
     * and tags. See {@link EventCriteria} for advanced usage and examples.
     * <p>
     * By default, it returns {@code EventCriteria.havingAnyTag().andBeingOneOfTypes(supportedEvents)}.
     *
     * @param eventCriteriaProvider The function to build the {@link EventCriteria} from supported event types.
     * @return The current instance, for fluent interfacing.
     */
    public PooledStreamingEventProcessorConfiguration eventCriteria(
            @Nonnull Function<Set<QualifiedName>, EventCriteria> eventCriteriaProvider) {
        assertNonNull(eventCriteriaProvider, "EventCriteria builder function may not be null");
        this.eventCriteriaProvider = eventCriteriaProvider;
        return this;
    }

    @Override
    protected void validate() throws AxonConfigurationException {
        super.validate();
        assertNonNull(eventSource, "The StreamableEventSource is a hard requirement and should be provided");
        assertNonNull(tokenStore, "The TokenStore is a hard requirement and should be provided");
        assertNonNull(unitOfWorkFactory, "The UnitOfWorkFactory is a hard requirement and should be provided");
        assertNonNull(
                coordinatorExecutor,
                "The Coordinator ScheduledExecutorService is a hard requirement and should be provided"
        );
        assertNonNull(
                workerExecutor,
                "The Worker ScheduledExecutorService is a hard requirement and should be provided"
        );
    }

    @Override
    public boolean streaming() {
        return true;
    }

    /**
     * Returns the {@link StreamableEventSource} used to track events.
     *
     * @return The {@link StreamableEventSource} for this processor.
     */
    public StreamableEventSource<? extends EventMessage> eventSource() {
        return eventSource;
    }

    /**
     * Returns the {@link TokenStore} used to store and fetch event tokens.
     *
     * @return The {@link TokenStore} for tracking progress.
     */
    public TokenStore tokenStore() {
        return tokenStore;
    }

    /**
     * Returns the coordinator's {@link ScheduledExecutorService}.
     *
     * @return The coordinator executor.
     */
    public ScheduledExecutorService coordinatorExecutor() {
        return coordinatorExecutor;
    }

    /**
     * Returns the worker's {@link ScheduledExecutorService}.
     *
     * @return The worker executor.
     */
    public ScheduledExecutorService workerExecutor() {
        return workerExecutor;
    }

    /**
     * Returns the initial segment count used on startup.
     *
     * @return The initial segment count.
     */
    public int initialSegmentCount() {
        return initialSegmentCount;
    }

    /**
     * Returns the function used to generate initial {@link TrackingToken}s.
     *
     * @return The initial token generation function.
     */
    public Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken() {
        return initialToken;
    }

    /**
     * Returns the token claim interval in milliseconds.
     *
     * @return The token claim interval.
     */
    public long tokenClaimInterval() {
        return tokenClaimInterval;
    }

    /**
     * Returns the {@link MaxSegmentProvider} defining maximum claimable segments.
     *
     * @return The {@link MaxSegmentProvider} for this processor.
     */
    public MaxSegmentProvider maxSegmentProvider() {
        return maxSegmentProvider;
    }

    /**
     * Returns the claim extension threshold in milliseconds.
     *
     * @return The claim extension threshold.
     */
    public long claimExtensionThreshold() {
        return claimExtensionThreshold;
    }

    /**
     * Returns the number of events processed in a single transaction.
     *
     * @return The batch size.
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * Returns the {@link Clock} used for time-dependent operations.
     *
     * @return The {@link Clock} for this processor.
     */
    public Clock clock() {
        return clock;
    }

    /**
     * Returns whether the coordinator extends claims for work packages.
     *
     * @return {@code true} if coordinator extends claims, {@code false} otherwise.
     */
    public boolean coordinatorExtendsClaims() {
        return coordinatorExtendsClaims;
    }

    /**
     * Returns the function to build {@link EventCriteria} from supported event types.
     *
     * @return The event criteria provider function.
     */
    public Function<Set<QualifiedName>, EventCriteria> eventCriteriaProvider() {
        return eventCriteriaProvider;
    }

    /**
     * Returns the handler for ignored messages.
     *
     * @return The ignored message handler.
     */
    public Consumer<? super EventMessage> ignoredMessageHandler() {
        return ignoredMessageHandler;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        super.describeTo(descriptor);
        descriptor.describeProperty("eventSource", eventSource);
        descriptor.describeProperty("tokenStore", tokenStore);
        descriptor.describeProperty("coordinatorExecutor", coordinatorExecutor);
        descriptor.describeProperty("workerExecutor", workerExecutor);
        descriptor.describeProperty("initialSegmentCount", initialSegmentCount);
        descriptor.describeProperty("initialToken", initialToken);
        descriptor.describeProperty("tokenClaimInterval", tokenClaimInterval);
        descriptor.describeProperty("maxSegmentProvider", maxSegmentProvider);
        descriptor.describeProperty("claimExtensionThreshold", claimExtensionThreshold);
        descriptor.describeProperty("batchSize", batchSize);
        descriptor.describeProperty("clock", clock);
        descriptor.describeProperty("coordinatorExtendsClaims", coordinatorExtendsClaims);
        descriptor.describeProperty("eventCriteriaProvider", eventCriteriaProvider);
    }
}
