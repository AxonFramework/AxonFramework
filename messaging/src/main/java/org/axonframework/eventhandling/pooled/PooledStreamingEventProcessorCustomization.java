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

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.TrackingTokenSource;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * Configuration for the {@link PooledStreamingEventProcessor}.
 * <p>
 * Upon initialization of the configuration, the following fields are defaulted:
 * <ul>
 *     <li>The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}.</li>
 *     <li>The {@link MessageMonitor} defaults to a {@link NoOpMessageMonitor}.</li>
 *     <li>The {@code initialSegmentCount} defaults to {@code 16}.</li>
 *     <li>The {@code initialToken} function defaults to a {@link org.axonframework.eventhandling.ReplayToken} that starts streaming
 *          from the {@link StreamableEventSource#latestToken() tail} with the replay flag enabled until the
 *          {@link StreamableEventSource#firstToken() head} at the moment of initialization is reached.</li>
 *     <li>The {@code tokenClaimInterval} defaults to {@code 5000} milliseconds.</li>
 *     <li>The {@link MaxSegmentProvider} (used by {@link #maxCapacity()}) defaults to {@link MaxSegmentProvider#maxShort()}.</li>
 *     <li>The {@code claimExtensionThreshold} defaults to {@code 5000} milliseconds.</li>
 *     <li>The {@code batchSize} defaults to {@code 1}.</li>
 *     <li>The {@link Clock} defaults to {@link GenericEventMessage#clock}.</li>
 *     <li>The {@link EventProcessorSpanFactory} defaults to a {@link org.axonframework.eventhandling.DefaultEventProcessorSpanFactory} backed by a {@link org.axonframework.tracing.NoOpSpanFactory}.</li>
 *     <li>The {@code coordinatorExtendsClaims} defaults to a {@code false}.</li>
 * </ul>
 * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
 * <ul>
 *     <li>The name of this {@link EventProcessor}.</li>
 *     <li>An {@link EventHandlerInvoker} which will be given the events handled by this processor</li>
 *     <li>A {@link StreamableEventSource} used to retrieve events.</li>
 *     <li>A {@link TokenStore} to store the progress of this processor in.</li>
 *     <li>A {@link ScheduledExecutorService} to coordinate events and segment operations.</li>
 *     <li>A {@link ScheduledExecutorService} to process work packages.</li>
 * </ul>
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class PooledStreamingEventProcessorCustomization {

    private TokenStore tokenStore;
    private TransactionManager transactionManager;
    private Function<String, ScheduledExecutorService> coordinatorExecutorBuilder;
    private Function<String, ScheduledExecutorService> workerExecutorBuilder;
    private int initialSegmentCount = 16;
    private Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialToken =
            es -> es.firstToken().thenApply(ReplayToken::createReplayToken);
    private long tokenClaimInterval = 5000;
    private MaxSegmentProvider maxSegmentProvider = MaxSegmentProvider.maxShort();
    private long claimExtensionThreshold = 5000;
    private int batchSize = 1;
    private Clock clock = GenericEventMessage.clock;
    private boolean coordinatorExtendsClaims = false;
    private Function<Set<QualifiedName>, EventCriteria> eventCriteria =
            (supportedEvents) -> EventCriteria.havingAnyTag().andBeingOneOfTypes(supportedEvents);


}
