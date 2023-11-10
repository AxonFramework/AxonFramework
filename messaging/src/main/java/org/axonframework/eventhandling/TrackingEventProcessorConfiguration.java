/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.messaging.StreamableMessageSource;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.*;
import static org.axonframework.eventhandling.ReplayToken.createReplayToken;

/**
 * Configuration object for the {@link TrackingEventProcessor}. The {@code TrackingEventProcessorConfiguration} provides
 * access to the options to tweak various settings. Instances are not thread-safe and should not be altered after they
 * have been used to initialize a {@code TrackingEventProcessor}.
 *
 * @author Christophe Bouhier
 * @author Allard Buijze
 * @since 3.1
 */
public class TrackingEventProcessorConfiguration {

    private static final int DEFAULT_BATCH_SIZE = 1;
    private static final int DEFAULT_THREAD_COUNT = 1;
    private static final int DEFAULT_TOKEN_CLAIM_INTERVAL = 5000;
    private static final long DEFAULT_WORKER_TERMINATION_TIMEOUT_MS = 5000;

    private final int maxThreadCount;
    private int batchSize;
    private int initialSegmentCount;
    private Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenBuilder;
    private Function<String, ThreadFactory> threadFactory;
    private long tokenClaimInterval;
    private int eventAvailabilityTimeout = 1000;
    private EventTrackerStatusChangeListener eventTrackerStatusChangeListener = EventTrackerStatusChangeListener.noOp();
    private boolean autoStart;
    private long workerTerminationTimeout;

    /**
     * Initialize a configuration with single threaded processing.
     *
     * @return A Configuration prepared for single threaded processing.
     */
    public static TrackingEventProcessorConfiguration forSingleThreadedProcessing() {
        return new TrackingEventProcessorConfiguration(DEFAULT_THREAD_COUNT);
    }

    /**
     * Initialize a configuration instance with the given {@code threadCount}. This is both the number of threads that a
     * processor will start for processing, as well as the initial number of segments that will be created when the
     * processor is first started.
     *
     * @param threadCount The number of segments to process in parallel.
     * @return A newly created configuration.
     */
    public static TrackingEventProcessorConfiguration forParallelProcessing(int threadCount) {
        return new TrackingEventProcessorConfiguration(threadCount);
    }

    private TrackingEventProcessorConfiguration(int numberOfSegments) {
        this.batchSize = DEFAULT_BATCH_SIZE;
        this.initialSegmentCount = numberOfSegments;
        this.maxThreadCount = numberOfSegments;
        this.threadFactory = pn -> new AxonThreadFactory("EventProcessor[" + pn + "]");
        this.tokenClaimInterval = DEFAULT_TOKEN_CLAIM_INTERVAL;
        this.autoStart = true;
        this.workerTerminationTimeout = DEFAULT_WORKER_TERMINATION_TIMEOUT_MS;
        this.initialTrackingTokenBuilder = messageSource -> createReplayToken(messageSource.createHeadToken());
    }

    /**
     * Set the maximum number of events that may be processed in a single transaction. Defaults to {@code 1}.
     *
     * @param batchSize The maximum number of events to process in a single batch.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andBatchSize(int batchSize) {
        Assert.isTrue(batchSize > 0, () -> "Batch size must be greater or equal to 1");
        this.batchSize = batchSize;
        return this;
    }

    /**
     * Sets the initial number of segments for asynchronous processing. Will be combined with the
     * {@link #andInitialTrackingToken(Function) initial tracking token} builder method for fresh
     * {@link TrackingEventProcessor TrackingEventProcessors}.
     * <p>
     * This value is <em>only</em> used whenever there are no {@link TrackingToken TrackingTokens} present for the
     * {@code TrackingEventProcessor} this configuration is used on.
     *
     * @param segmentsSize The number of segments requested for handling asynchronous processing of events.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andInitialSegmentsCount(int segmentsSize) {
        this.initialSegmentCount = segmentsSize;
        return this;
    }

    /**
     * Sets the {@link ThreadFactory} to use to create the {@link Thread Threads} to process events on. Each segment
     * will be processed by a separate thread.
     *
     * @param threadFactory The {@link ThreadFactory} to create {@link Thread Threads} with.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andThreadFactory(
            @Nonnull Function<String, ThreadFactory> threadFactory
    ) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * Set the duration where a Tracking Processor will wait for the availability of Events, in each cycle, before
     * extending the claim on the tokens it owns.
     * <p>
     * Note that some storage engines for the EmbeddedEventStore do not support streaming. They may poll for messages
     * once on an {@link TrackingEventStream#hasNextAvailable(int, TimeUnit)} invocation, and wait for the timeout to
     * occur.
     * <p>
     * This value should be significantly shorter than the claim timeout configured on the Token Store. Failure to do so
     * may cause claims to be stolen while a tread is waiting for events. Also, with very long timeouts, it will take
     * longer for threads to pick up the instructions they need to process.
     * <p>
     * Defaults to 1 second.
     * <p>
     * The given value must be strictly larger than 0, and may not exceed {@code Integer.MAX_VALUE} milliseconds.
     *
     * @param interval The interval in which claims on segments need to be extended.
     * @param unit     The unit in which the interval is expressed.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andEventAvailabilityTimeout(long interval, TimeUnit unit) {
        long i = unit.toMillis(interval);
        assertThat(i, it -> it <= Integer.MAX_VALUE,
                   "Interval may not be longer than Integer.MAX_VALUE milliseconds long");
        assertThat(i, it -> it > 0, "Interval must be strictly positive");
        this.eventAvailabilityTimeout = (int) i;
        return this;
    }

    /**
     * Sets the builder to use to create the initial {@link TrackingToken}. This token is used by the processor as a
     * starting point.
     * <p>
     * Defaults to an automatic replay since the start of the stream.
     * <p>
     * More specifically, it defaults to a {@link org.axonframework.eventhandling.ReplayToken} that starts streaming
     * from the {@link StreamableMessageSource#createTailToken() tail} with the replay flag enabled until the
     * {@link StreamableMessageSource#createHeadToken() head} at the moment of initialization is reached.
     *
     * @param initialTrackingTokenBuilder The builder of the initial {@link TrackingToken}.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andInitialTrackingToken(
            @Nonnull Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenBuilder
    ) {
        this.initialTrackingTokenBuilder = initialTrackingTokenBuilder;
        return this;
    }

    /**
     * Sets the time to wait after a failed attempt to claim any token, before making another attempt.
     *
     * @param tokenClaimInterval The time to wait in between attempts to claim a token.
     * @param timeUnit           The unit of time.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andTokenClaimInterval(long tokenClaimInterval,
                                                                     @Nonnull TimeUnit timeUnit) {
        this.tokenClaimInterval = timeUnit.toMillis(tokenClaimInterval);
        return this;
    }

    /**
     * Whether to automatically start the processor when event processing is initialized. If set to {@code false}, the
     * application must explicitly start the processor. This can be useful if the application needs to perform its own
     * initialization before it begins processing new events.
     * <p>
     * The autostart setting does not impact the shutdown process of the processor. It will always be triggered when the
     * framework receives a signal to shut down.
     *
     * @param autoStart {@code true} to automatically start the processor (the default), {@code false} if the
     *                  application will start the processor itself.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
        return this;
    }

    /**
     * Sets the {@link EventTrackerStatusChangeListener} which will be called on {@link EventTrackerStatus} changes.
     * <p>
     * Defaults to {@link EventTrackerStatusChangeListener#noOp()}.
     *
     * @param eventTrackerStatusChangeListener The {@link EventTrackerStatusChangeListener} to use.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andEventTrackerStatusChangeListener(
            @Nonnull EventTrackerStatusChangeListener eventTrackerStatusChangeListener
    ) {
        assertNonNull(eventTrackerStatusChangeListener, "EventTrackerStatusChangeListener may not be null");
        this.eventTrackerStatusChangeListener = eventTrackerStatusChangeListener;
        return this;
    }

    /**
     * Sets the shutdown timeout to terminate active workers.
     * <p>
     * This is used for both the graceful termination and the potential forced termination of active workers. It is thus
     * possible that it is used twice during the shutdown phase. Defaults to 5000ms.
     *
     * @param workerTerminationTimeout The timeout for workers to terminate on a shutdown.
     * @param timeUnit                 The unit of time.
     * @return {@code this} for method chaining.
     */
    public TrackingEventProcessorConfiguration andWorkerTerminationTimeout(long workerTerminationTimeout,
                                                                           TimeUnit timeUnit) {
        assertStrictPositive(workerTerminationTimeout, "The worker termination timeout should be strictly positive");
        this.workerTerminationTimeout = timeUnit.toMillis(workerTerminationTimeout);
        return this;
    }

    /**
     * Return the maximum number of events to process in a single batch.
     *
     * @return The maximum number of events to process in a single batch.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Return the number of segments requested for handling asynchronous processing of events.
     *
     * @return The number of segments requested for handling asynchronous processing of events.
     */
    public int getInitialSegmentsCount() {
        return initialSegmentCount;
    }

    /**
     * Return the builder function of the initial {@link TrackingToken}.
     *
     * @return The builder of initial {@link TrackingToken}.
     */
    public Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> getInitialTrackingToken() {
        return initialTrackingTokenBuilder;
    }

    /**
     * Return the pool size of core threads as per {@link ThreadPoolExecutor#getCorePoolSize()}.
     *
     * @return the pool size of core threads as per {@link ThreadPoolExecutor#getCorePoolSize()}.
     */
    public int getMaxThreadCount() {
        return maxThreadCount;
    }

    /**
     * Return the time, in milliseconds, that a processor should wait for available events before going into a cycle of
     * updating claims and checking for incoming instructions.
     *
     * @return The time, in milliseconds, that a processor should wait for available events before going into a cycle of
     * updating claims and checking for incoming instructions.
     */
    public int getEventAvailabilityTimeout() {
        return eventAvailabilityTimeout;
    }

    /**
     * Provides the {@link ThreadFactory} to use to construct {@link Thread Threads} for the processor with given
     * {@code processorName}.
     *
     * @param processorName The name of the processor for which to return the {@link ThreadFactory}.
     * @return The configured {@link ThreadFactory}.
     */
    public ThreadFactory getThreadFactory(String processorName) {
        return threadFactory.apply(processorName);
    }

    /**
     * Returns the time, in milliseconds, the processor should wait after a failed attempt to claim any segments for
     * processing. Generally, this means all segments are claimed.
     *
     * @return The time, in milliseconds, to wait in between attempts to claim a token.
     * @see #andTokenClaimInterval(long, TimeUnit)
     */
    public long getTokenClaimInterval() {
        return tokenClaimInterval;
    }

    /**
     * Return a {@code boolean} dictating whether the processor should start automatically when the application starts.
     *
     * @return {@code true} if the processor should be started automatically by the framework.
     */
    public boolean isAutoStart() {
        return autoStart;
    }

    /**
     * Returns the {@link EventTrackerStatusChangeListener} defined in this configuration, to be called whenever an
     * {@link EventTrackerStatus} change occurs.
     *
     * @return The {@link EventTrackerStatusChangeListener} defined in this configuration.
     */
    public EventTrackerStatusChangeListener getEventTrackerStatusChangeListener() {
        return eventTrackerStatusChangeListener;
    }

    /**
     * Returns the timeout to terminate workers during a {@link TrackingEventProcessor#shutDown()}.
     *
     * @return The timeout to terminate workers during a {@link TrackingEventProcessor#shutDown()}.
     */
    public long getWorkerTerminationTimeout() {
        return workerTerminationTimeout;
    }
}
