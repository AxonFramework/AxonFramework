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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.configuration.EventProcessorsCustomization;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.TrackingTokenSource;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Configuration class used to instantiate a {@link PooledStreamingEventProcessor}.
 * <p>
 * Upon initialization of this configuration, the following fields are defaulted:
 * <ul>
 *     <li>The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}.</li>
 *     <li>The {@link MessageMonitor} defaults to a {@link NoOpMessageMonitor}.</li>
 *     <li>The {@code initialSegmentCount} defaults to {@code 16}.</li>
 *     <li>The {@code initialToken} function defaults to a {@link org.axonframework.eventhandling.ReplayToken} that starts streaming
 *          from the {@link StreamableEventSource#latestToken() tail} with the replay flag enabled until the
 *          {@link StreamableEventSource#firstToken() head} at the moment of initialization is reached.</li>
 *     <li>The {@code tokenClaimInterval} defaults to {@code 5000} milliseconds.</li>
 *     <li>The {@link MaxSegmentProvider} (used by {@link PooledStreamingEventProcessor#maxCapacity()}) defaults to {@link MaxSegmentProvider#maxShort()}.</li>
 *     <li>The {@code claimExtensionThreshold} defaults to {@code 5000} milliseconds.</li>
 *     <li>The {@code batchSize} defaults to {@code 1}.</li>
 *     <li>The {@link Clock} defaults to {@link GenericEventMessage#clock}.</li>
 *     <li>The {@link EventProcessorSpanFactory} defaults to a {@link org.axonframework.eventhandling.DefaultEventProcessorSpanFactory} backed by a {@link org.axonframework.tracing.NoOpSpanFactory}.</li>
 *     <li>The {@code coordinatorExtendsClaims} defaults to a {@code false}.</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
// todo: I believe customization is on the component level, but some Configuration needs to be higher level, on the module level and access components!!! like MessageMonitor etc.
public class PooledStreamingEventProcessorsCustomization extends EventProcessorsCustomization {

    // todo: tokenStore?

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
    private Consumer<? super EventMessage<?>> ignoredMessageHandler = eventMessage -> messageMonitor.onMessageIngested(eventMessage).reportIgnored();
    // todo: builder with monitor from config???


    public PooledStreamingEventProcessorsCustomization() {
        super();
    }

    public PooledStreamingEventProcessorsCustomization(
            @Nonnull EventProcessorsCustomization eventProcessorsCustomization) {
        super(eventProcessorsCustomization);
    }

    public PooledStreamingEventProcessorsCustomization initialSegmentCount(int initialSegmentCount) {
        this.initialSegmentCount = initialSegmentCount;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization initialToken(
            Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken) {
        this.initialToken = initialToken;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization tokenClaimInterval(long tokenClaimInterval) {
        this.tokenClaimInterval = tokenClaimInterval;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization maxSegmentProvider(MaxSegmentProvider maxSegmentProvider) {
        this.maxSegmentProvider = maxSegmentProvider;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization claimExtensionThreshold(long claimExtensionThreshold) {
        this.claimExtensionThreshold = claimExtensionThreshold;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization coordinatorExtendsClaims(boolean coordinatorExtendsClaims) {
        this.coordinatorExtendsClaims = coordinatorExtendsClaims;
        return this;
    }

    public PooledStreamingEventProcessorsCustomization eventCriteriaProvider(
            Function<Set<QualifiedName>, EventCriteria> eventCriteriaProvider) {
        this.eventCriteriaProvider = eventCriteriaProvider;
        return this;
    }

    public int initialSegmentCount() {
        return initialSegmentCount;
    }

    public Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken() {
        return initialToken;
    }

    public long tokenClaimInterval() {
        return tokenClaimInterval;
    }

    public MaxSegmentProvider maxSegmentProvider() {
        return maxSegmentProvider;
    }

    public long claimExtensionThreshold() {
        return claimExtensionThreshold;
    }

    public int batchSize() {
        return batchSize;
    }

    public Clock clock() {
        return clock;
    }

    public boolean coordinatorExtendsClaims() {
        return coordinatorExtendsClaims;
    }

    public Function<Set<QualifiedName>, EventCriteria> eventCriteriaProvider() {
        return eventCriteriaProvider;
    }

    public PooledStreamingEventProcessorsCustomization errorHandler(@Nonnull ErrorHandler errorHandler) {
        super.errorHandler(errorHandler);
        return this;
    }

    public EventProcessorsCustomization messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super.messageMonitor(messageMonitor);
        return this;
    }

    public EventProcessorsCustomization spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        super.spanFactory(spanFactory);
        return this;
    }

    public Consumer<? super EventMessage<?>> ignoredMessageHandler() {
        return ignoredMessageHandler;
    }

    public PooledStreamingEventProcessorsCustomization ignoredMessageHandler(
            Consumer<? super EventMessage<?>> ignoredMessageHandler) {
        this.ignoredMessageHandler = ignoredMessageHandler;
        return this;
    }
}
