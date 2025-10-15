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
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.processors.EventProcessingException;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.ProcessorEventHandlingComponents;
import org.axonframework.eventhandling.processors.errorhandling.ErrorContext;
import org.axonframework.eventhandling.processors.streaming.StreamingEventProcessor;
import org.axonframework.eventhandling.processors.streaming.segmenting.EventTrackerStatus;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.segmenting.TrackerStatus;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.replay.ResetNotSupportedException;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.TrackingTokenSource;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;

/**
 * A {@link StreamingEventProcessor} implementation which pools its resources to enhance processing speed. It utilizes a
 * {@link Coordinator} as the means to stream events from a {@link StreamableEventSource} and creates so-called work
 * packages. Every work package is in charge of a {@link Segment} of the entire event stream. It is the
 * {@code Coordinator}'s job to retrieve the events from the source and provide the events to all the work packages it
 * is in charge of.
 * <p>
 * This approach utilizes two threads pools. One to retrieve the events to provide them to the work packages and another
 * to actual handle the events. Respectively, the coordinator thread pool and the work package thread pool. It is this
 * approach which allows for greater parallelization and processing speed than the TrackingEventProcessor (removed in
 * 5.0.0).
 * <p>
 * If no {@link TrackingToken}s are present for this processor, the {@code PooledStreamingEventProcessor} will
 * initialize them in a given segment count. By default, it will create {@code 16} segments, which can be configured
 * through the {@link PooledStreamingEventProcessorConfiguration#initialSegmentCount(int)}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 4.5.0
 */
public class PooledStreamingEventProcessor implements StreamingEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final PooledStreamingEventProcessorConfiguration configuration;
    private final StreamableEventSource<? extends EventMessage> eventSource;
    private final ProcessorEventHandlingComponents eventHandlingComponents;
    private final EventCriteria eventCriteria;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final TokenStore tokenStore;
    private final ScheduledExecutorService workerExecutor;
    private final Coordinator coordinator;
    private final WorkPackage.EventFilter workPackageEventFilter;

    private final AtomicReference<String> tokenStoreIdentifier = new AtomicReference<>();
    private final Map<Integer, TrackerStatus> processingStatus = new ConcurrentHashMap<>();

    /**
     * Instantiate a {@code PooledStreamingEventProcessor} with given {@code name}, {@code eventHandlingComponents} and
     * based on the fields contained in the {@link PooledStreamingEventProcessorConfiguration}.
     * <p>
     * Will assert the following for their presence in the configuration, prior to constructing this processor:
     * <ul>
     *     <li>A {@link StreamableEventSource}.</li>
     *     <li>A {@link TokenStore}.</li>
     *     <li>A {@link UnitOfWorkFactory}.</li>
     *     <li>A {@link ScheduledExecutorService} for coordination.</li>
     *     <li>A {@link ScheduledExecutorService} to process work packages.</li>
     * </ul>
     * If any of these is not present or does not comply to the requirements an {@link AxonConfigurationException} is thrown.
     *
     * @param name                    A {@link String} defining this {@link EventProcessor} instance.
     * @param eventHandlingComponents The {@link EventHandlingComponent}s which will handle all the individual
     *                                {@link EventMessage}s.
     * @param configuration           The {@link PooledStreamingEventProcessorConfiguration} used to configure a
     *                                {@code PooledStreamingEventProcessor} instance.
     */
    public PooledStreamingEventProcessor(
            @Nonnull String name,
            @Nonnull List<EventHandlingComponent> eventHandlingComponents,
            @Nonnull PooledStreamingEventProcessorConfiguration configuration
    ) {
        this.name = Objects.requireNonNull(name, "Name may not be null");
        assertThat(name, n -> Objects.nonNull(n) && !n.isEmpty(), "Event Processor name may not be null or empty");
        this.configuration = Objects.requireNonNull(configuration, "Configuration may not be null");
        configuration.validate();
        this.eventSource = configuration.eventSource();
        this.tokenStore = configuration.tokenStore();
        this.unitOfWorkFactory = configuration.unitOfWorkFactory();

        this.eventHandlingComponents = new ProcessorEventHandlingComponents(eventHandlingComponents);
        this.workPackageEventFilter = new DefaultWorkPackageEventFilter(
                this.name,
                this.eventHandlingComponents,
                configuration.errorHandler()
        );
        this.workerExecutor = configuration.workerExecutor();
        var supportedEvents = this.eventHandlingComponents.supportedEvents();
        this.eventCriteria = Objects.requireNonNull(
                configuration.eventCriteriaProvider().apply(supportedEvents),
                "EventCriteriaProvider function must not return null"
        );

        this.coordinator = Coordinator.builder()
                                      .name(name)
                                      .eventSource(eventSource)
                                      .tokenStore(tokenStore)
                                      .unitOfWorkFactory(unitOfWorkFactory)
                                      .executorService(configuration.coordinatorExecutor())
                                      .workPackageFactory(this::spawnWorker)
                                      .onMessageIgnored(configuration.ignoredMessageHandler())
                                      .processingStatusUpdater(this::statusUpdater)
                                      .tokenClaimInterval(configuration.tokenClaimInterval())
                                      .claimExtensionThreshold(configuration.claimExtensionThreshold())
                                      .clock(configuration.clock())
                                      .maxSegmentProvider(configuration.maxSegmentProvider())
                                      .initialSegmentCount(configuration.initialSegmentCount())
                                      .initialToken(configuration.initialToken())
                                      .coordinatorClaimExtension(configuration.coordinatorExtendsClaims())
                                      .eventCriteria(eventCriteria)
                                      // .segmentReleasedAction(segment -> eventHandlerInvoker().segmentReleased(segment)) // TODO #3304 - Integrate event replay logic into Event Handling Component
                                      .build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CompletableFuture<Void> start() {
        logger.info("Starting PooledStreamingEventProcessor [{}].", name);
        coordinator.start();
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        logger.info("Stopping PooledStreamingEventProcessor [{}]", name);
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
        var unitOfWork = unitOfWorkFactory.create();
        return joinAndUnwrap(unitOfWork.executeWithResult(tokenStore::retrieveStorageIdentifier))
                .orElse("--unknown--");
    }

    @Override
    public CompletableFuture<Void> releaseSegment(int segmentId) {
        var tokenClaimInterval = this.configuration.tokenClaimInterval();
        return releaseSegment(segmentId, tokenClaimInterval * 2, MILLISECONDS);
    }

    @Override
    public CompletableFuture<Void> releaseSegment(int segmentId, long releaseDuration, TimeUnit unit) {
        coordinator.releaseUntil(
                segmentId, GenericEventMessage.clock.instant().plusMillis(unit.toMillis(releaseDuration))
        );
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public CompletableFuture<Boolean> claimSegment(int segmentId) {
        return coordinator.claimSegment(segmentId);
    }

    @Override
    public CompletableFuture<Boolean> splitSegment(int segmentId) {
        return coordinator.splitSegment(segmentId);
    }

    @Override
    public CompletableFuture<Boolean> mergeSegment(int segmentId) {
        return coordinator.mergeSegment(segmentId);
    }

    @Override
    public boolean supportsReset() {
        return false;
        // TODO #3304 - Integrate event replay logic into Event Handling Component
        //return eventHandlerInvoker().supportsReset();
    }

    @Override
    public CompletableFuture<Void> resetTokens() {
        var initialToken = configuration.initialToken();
        return resetTokens(initialToken);
    }

    @Override
    public <R> CompletableFuture<Void> resetTokens(R resetContext) {
        var initialToken = configuration.initialToken();
        return resetTokens(initialToken, resetContext);
    }

    @Override
    public CompletableFuture<Void> resetTokens(
            @Nonnull Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialTrackingTokenSupplier
    ) {
        return initialTrackingTokenSupplier.apply(eventSource).thenCompose(this::resetTokens);
    }

    @Override
    public <R> CompletableFuture<Void> resetTokens(
            @Nonnull Function<TrackingTokenSource, CompletableFuture<TrackingToken>> initialTrackingTokenSupplier,
            R resetContext
    ) {
        return initialTrackingTokenSupplier.apply(eventSource).thenCompose(r -> resetTokens(r, resetContext));
    }

    @Override
    public CompletableFuture<Void> resetTokens(@Nonnull TrackingToken startPosition) {
        return resetTokens(startPosition, null);
    }

    @Override
    public <R> CompletableFuture<Void> resetTokens(@Nonnull TrackingToken startPosition, R resetContext) {
        // TODO #3304 - Integrate event replay logic into Event Handling Component
        var exception = new ResetNotSupportedException(
                "TODO #3304 - Integrate event replay logic into Event Handling Component");
        return CompletableFuture.failedFuture(exception);
//        Assert.state(supportsReset(), () -> "The handlers assigned to this Processor do not support a reset.");
//        Assert.state(!isRunning(), () -> "The Processor must be shut down before triggering a reset.");
//
//        var unitOfWork = unitOfWorkFactory.create();
//        var resetTokensFuture = unitOfWork.executeWithResult((processingContext) -> {
//            // Find all segments and fetch all tokens
//            int[] segments = tokenStore.fetchSegments(getName());
//            logger.debug("Processor [{}] will try to reset tokens for segments [{}].", name, segments);
//            TrackingToken[] tokens = Arrays.stream(segments)
//                                           .mapToObj(segment -> tokenStore.fetchToken(getName(), segment))
//                                           .toArray(TrackingToken[]::new);
//            // Perform the reset on the EventHandlerInvoker
//            eventHandlerInvoker().performReset(resetContext, null);
//            // Update all tokens towards ReplayTokens
//            IntStream.range(0, tokens.length)
//                     .forEach(i -> tokenStore.storeToken(
//                             ReplayToken.createReplayToken(tokens[i], startPosition, resetContext),
//                             getName(),
//                             segments[i]
//                     ));
//            logger.info("Processor [{}] successfully reset tokens for segments [{}].", name, segments);
//            return CompletableFuture.completedFuture(null);
//        });
//        joinAndUnwrap(resetTokensFuture);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The maximum capacity of the {@code PooledStreamingEventProcessor} defaults to {@value Short#MAX_VALUE}. If
     * required, this value can be adjusted through the
     * {@link PooledStreamingEventProcessorConfiguration#maxClaimedSegments(int)} method.
     */
    @Override
    public int maxCapacity() {
        var maxSegmentProvider = configuration.maxSegmentProvider();
        return maxSegmentProvider.getMaxSegments(name);
    }

    @Override
    public Map<Integer, EventTrackerStatus> processingStatus() {
        return Collections.unmodifiableMap(processingStatus);
    }

    private WorkPackage spawnWorker(Segment segment, TrackingToken initialToken) {
        WorkPackage.BatchProcessor batchProcessor = (events, context) -> processWithErrorHandling(events, context);
        var batchSize = configuration.batchSize();
        var claimExtensionThreshold = configuration.claimExtensionThreshold();
        var clock = configuration.clock();
        return WorkPackage.builder()
                          .name(name)
                          .tokenStore(tokenStore)
                          .unitOfWorkFactory(unitOfWorkFactory)
                          .executorService(workerExecutor)
                          .eventFilter(workPackageEventFilter)
                          .batchProcessor(batchProcessor)
                          .segment(segment)
                          .initialToken(initialToken)
                          .batchSize(batchSize)
                          .claimExtensionThreshold(claimExtensionThreshold)
                          .segmentStatusUpdater(singleStatusUpdater(
                                  segment.getSegmentId(), new TrackerStatus(segment, initialToken)
                          ))
                          .clock(clock)
                          .schedulingProcessingContextProvider(configuration.schedulingProcessingContextProvider())
                          .build();
    }

    private MessageStream.Empty<Message> processWithErrorHandling(List<? extends EventMessage> events,
                                                                  ProcessingContext context) {
        return eventHandlingComponents.handle(events, context)
                                      .onErrorContinue(ex -> {
                                          try {
                                              configuration.errorHandler()
                                                           .handleError(new ErrorContext(name, ex, events, context));
                                          } catch (RuntimeException re) {
                                              return MessageStream.failed(re);
                                          } catch (Exception e) {
                                              return MessageStream.failed(new EventProcessingException(
                                                      "Exception occurred while processing events", e
                                              ));
                                          }
                                          return MessageStream.empty().cast();
                                      })
                                      .ignoreEntries()
                                      .cast();
    }

    /**
     * A {@link Consumer} of a {@link TrackerStatus} update method. To be used by a {@link WorkPackage} to update the
     * {@code TrackerStatus} of the {@link Segment} it is in charge of.
     *
     * @param segmentId     the {@link Segment} identifier for which the {@link TrackerStatus} should be updated
     * @param initialStatus the initial {@link TrackerStatus} if there's no {@code TrackerStatus} for the given
     *                      {@code segmentId}
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
     * @param segmentUpdater the lambda which receives the current {@link TrackerStatus} and returns the updated
     *                       {@code TrackerStatus}
     */
    private void statusUpdater(int segmentId, UnaryOperator<TrackerStatus> segmentUpdater) {
        processingStatus.computeIfPresent(segmentId, (s, ts) -> segmentUpdater.apply(ts));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("mode", "pooled");
        descriptor.describeProperty("eventHandlingComponents", eventHandlingComponents);
        descriptor.describeProperty("eventCriteria", eventCriteria);
        descriptor.describeProperty("configuration", configuration);
        descriptor.describeProperty("processingStatus", processingStatus);
    }
}