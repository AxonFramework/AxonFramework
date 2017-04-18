/*
 * Copyright (c) 2010-2017. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
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
 * Note, the {@link #getName() name} of the EventProcessor is used to obtain the tracking token from the TokenStore, so
 * take care when renaming a TrackingEventProcessor.
 *
 * @author Rene de Waele
 */
public class TrackingEventProcessor extends AbstractEventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(TrackingEventProcessor.class);

    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;
    private final TokenStore tokenStore;
    private final SequencingPolicy<? super EventMessage<?>> sequentialPolicy;
    private final TransactionManager transactionManager;
    private final int batchSize;
    private final String name;
    private final AsyncTrackingEventProcessingStrategy processingStrategy;
    private volatile ThreadPoolExecutor executorService;
    private AtomicReference<State> state = new AtomicReference<>(State.NOT_STARTED);

    private int corePoolSize = 1;
    private int maxPoolSize = 1;

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code messageSource} for
     * events. Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     * <p>
     * The EventProcessor is initialized with a batch size of 1, a {@link PropagatingErrorHandler}, a {@link
     * RollbackConfigurationType#ANY_THROWABLE} and a {@link NoOpMessageMonitor}.
     *
     * @param name                The name of the event processor
     * @param eventHandlerInvoker The component that handles the individual events
     * @param messageSource       The message source (e.g. Event Bus) which this event processor will track
     * @param tokenStore          Used to store and fetch event tokens that enable the processor to track its progress
     * @param transactionManager  The transaction manager used when processing messages
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  StreamableMessageSource<TrackedEventMessage<?>> messageSource, TokenStore tokenStore,
                                  TransactionManager transactionManager) {
        this(name, eventHandlerInvoker, messageSource, tokenStore, transactionManager, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code messageSource} for
     * events. Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     * <p>
     * The EventProcessor is initialized with a batch size of 1, a {@link PropagatingErrorHandler}, a {@link
     * RollbackConfigurationType#ANY_THROWABLE} and a {@link NoOpMessageMonitor}.
     *
     * @param name                The name of the event processor
     * @param eventHandlerInvoker The component that handles the individual events
     * @param messageSource       The message source (e.g. Event Bus) which this event processor will track
     * @param tokenStore          Used to store and fetch event tokens that enable the processor to track its progress
     * @param transactionManager  The transaction manager used when processing messages
     * @param batchSize           The maximum number of events to process in a single batch
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  StreamableMessageSource<TrackedEventMessage<?>> messageSource, TokenStore tokenStore,
                                  TransactionManager transactionManager, int batchSize) {
        this(name, eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                messageSource, tokenStore, new SegmentingPerAggregatePolicy(), transactionManager, batchSize, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code messageSource} for
     * events. Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     * <p>
     * The EventProcessor is initialized with a batch size of 1, a {@link PropagatingErrorHandler} and a {@link
     * RollbackConfigurationType#ANY_THROWABLE}.
     *
     * @param name                The name of the event processor
     * @param eventHandlerInvoker The component that handles the individual events
     * @param messageSource       The message source (e.g. Event Bus) which this event processor will track
     * @param tokenStore          Used to store and fetch event tokens that enable the processor to track its progress
     * @param transactionManager  The transaction manager used when processing messages
     * @param messageMonitor      Monitor to be invoked before and after event processing
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  StreamableMessageSource<TrackedEventMessage<?>> messageSource, TokenStore tokenStore,
                                  TransactionManager transactionManager,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this(name, eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                messageSource, tokenStore, new SegmentingPerAggregatePolicy(), transactionManager, 1, messageMonitor);
    }

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code messageSource} for
     * events. Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     *
     * @param name                  The name of the event processor
     * @param eventHandlerInvoker   The component that handles the individual events
     * @param rollbackConfiguration Determines rollback behavior of the UnitOfWork while processing a batch of events
     * @param errorHandler          Invoked when a UnitOfWork is rolled back during processing
     * @param messageSource         The message source (e.g. Event Bus) which this event processor will track
     * @param tokenStore            Used to store and fetch event tokens that enable the processor to track its
     *                              progress
     * @param sequentialPolicy
     * @param transactionManager    The transaction manager used when processing messages
     * @param batchSize             The maximum number of events to process in a single batch
     * @param messageMonitor        Monitor to be invoked before and after event processing
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                  StreamableMessageSource<TrackedEventMessage<?>> messageSource, TokenStore tokenStore,
                                  SequencingPolicy<? super EventMessage<?>> sequentialPolicy, TransactionManager transactionManager, int batchSize,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(name, eventHandlerInvoker, rollbackConfiguration, errorHandler, messageMonitor);
        Assert.isTrue(batchSize > 0, () -> "batchSize needs to be greater than 0");
        this.messageSource = requireNonNull(messageSource);
        this.tokenStore = requireNonNull(tokenStore);
        this.sequentialPolicy = requireNonNull(sequentialPolicy);
        this.transactionManager = transactionManager;
        this.name = name;
        this.batchSize = batchSize;
        this.processingStrategy = new AsyncTrackingEventProcessingStrategy();

        registerInterceptor(new TransactionManagingInterceptor<>(transactionManager));
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
            ensureRunningExecutor();
            processingStrategy.startSegmentWorkers();
            // Delegate to strategy to dispatch workers.

//            executorService.submit(() -> {
//                try {
//                    this.processingLoop();
//                } catch (Throwable e) {
//                    logger.error("Processing loop ended due to uncaught exception. Processor pausing.", e);
//                    state.set(State.PAUSED_ERROR);
//                }
//            });
        }
    }

    private void ensureRunningExecutor() {
        if (this.executorService == null || this.executorService.isShutdown()) {
            this.executorService = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                    new AxonThreadFactory("TrackingEventProcessor - " + name));
        }
    }

    /**
     * Fetch and process event batches continuously for as long as the processor is not shutting down. The processor
     * will process events in batches. The maximum size of size of each event batch is configurable.
     * <p>
     * Events with the same tracking token (which is possible as result of upcasting) should always be processed in
     * the same batch. In those cases the batch size may be larger than the one configured.
     *
     * @param segmentWorker
     */
    protected void processingLoop(TrackingSegmentWorker segmentWorker) {
        MessageStream<TrackedEventMessage<?>> eventStream = null;
        long errorWaitTime = 1;
        try {
            while (state.get().isRunning()) {

                try{
                    if(segmentWorker.isDispatcher()){
                        processingStrategy.optimizeWorkers();
                    }
                }catch(Exception e){

                }


                try {
                    eventStream = ensureEventStreamOpened(eventStream);
                    processBatch(segmentWorker, eventStream);
                    errorWaitTime = 1;
                } catch (Exception e) {
                    // make sure to start with a clean event stream. The exception may have cause an illegal state
                    if (errorWaitTime == 1) {
                        logger.warn("Error occurred. Starting retry mode.", e);
                    }
                    logger.warn("Releasing claim on token and preparing for retry in {}s", errorWaitTime);
                    releaseToken(segmentWorker.getSegment());
                    closeQuietly(eventStream);
                    eventStream = null;
                    try {
                        Thread.sleep(errorWaitTime * 1000);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                        logger.warn("Thread interrupted. Preparing to shut down event processor");
                        // TODO, when in a worker thread, we can't shutdown the whole processing...
                        shutDown();
                    }
                    errorWaitTime = Math.min(errorWaitTime * 2, 60);
                }
            }
        } finally {
            closeQuietly(eventStream);
            releaseToken(segmentWorker.getSegment());
        }
    }

    private void releaseToken(Segment segment) {
        try {
            transactionManager.executeInTransaction(() -> tokenStore.releaseClaim(getName(), segment.getSegmentId()));
        } catch (Exception e) {
            // whatever.
        }
    }

    private void processBatch(TrackingSegmentWorker segmentWorker, MessageStream<TrackedEventMessage<?>> eventStream) throws Exception {
        List<TrackedEventMessage<?>> batch = new ArrayList<>();
        try {
            if (eventStream.hasNextAvailable(1, TimeUnit.SECONDS)) {
                while (batch.size() < batchSize && eventStream.hasNextAvailable()) {

                    final TrackedEventMessage<?> trackedEventMessage = eventStream.nextAvailable();

                    if (sequentialPolicy instanceof SegmentingPerAggregatePolicy) {
                        if (((SegmentingPerAggregatePolicy) sequentialPolicy).matches(segmentWorker.getSegment(), trackedEventMessage)) {
                            batch.add(trackedEventMessage);
                        }
                    } else {
                        batch.add(trackedEventMessage);
                    }
                }
            }
            if (batch.isEmpty()) {
                // refresh claim on token
                transactionManager.executeInTransaction(() -> tokenStore.extendClaim(getName(), segmentWorker.getSegment().getSegmentId()));
                return;
            }

            // make sure all subsequent events with the same token (if non-null) as the last are added as well.
            // These are the result of upcasting and should always be processed in the same batch.
            segmentWorker.setLastToken(batch.get(batch.size() - 1).trackingToken());
            final TrackingToken lastToken = segmentWorker.getLastToken();
            while (lastToken != null && eventStream.peek().filter(event -> lastToken.equals(event.trackingToken())).isPresent()) {
                // TODO, support sequential policy here as well.
                batch.add(eventStream.nextAvailable());
            }

            process(batch);

        } catch (InterruptedException e) {
            logger.error(String.format("Event processor [%s] was interrupted. Shutting down.", getName()), e);
            Thread.currentThread().interrupt();
            // TODO, when in a worker thread, we can't shutdown the whole processing...
            this.shutDown();
        }
    }

    private MessageStream<TrackedEventMessage<?>> ensureEventStreamOpened(
            MessageStream<TrackedEventMessage<?>> eventStreamIn) {
        MessageStream<TrackedEventMessage<?>> eventStream = eventStreamIn;
        if (eventStream == null && state.get().isRunning()) {
            eventStream = transactionManager.fetchInTransaction(
                    () -> messageSource.openStream(tokenStore.fetchToken(getName(), 0)));
        }
        return eventStream;
    }

    /**
     * Stops processing if it currently running, but doesn't stop free up the processing thread. If the processor is
     * not running, the state isn't changed.
     */
    public void pause() {
        this.state.updateAndGet(s -> s.isRunning() ? State.PAUSED : s);
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
     * Note that this method also returns {@code false} when the processor was paused using {@link #pause()}.
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
        if (state.getAndUpdate(s -> State.SHUT_DOWN) != State.SHUT_DOWN) {
            executorService.shutdown();
        }
    }

    /**
     * Returns an approximation of the number of threads currently processing events.
     *
     * @return an approximation of the number of threads currently processing events
     */
    public int activeProcessorThreads() {
        ThreadPoolExecutor currentService = this.executorService;
        return currentService == null ? 0 : currentService.getActiveCount();
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

    public void tweakThreadPool(int corePoolSize, int maxPoolSize) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        // Note will be consumed, when calling optimize on strategy....
    }

    protected enum State {

        NOT_STARTED(false), STARTED(true), PAUSED(false), SHUT_DOWN(false), PAUSED_ERROR(false);

        private final boolean allowProcessing;

        State(boolean allowProcessing) {
            this.allowProcessing = allowProcessing;
        }

        boolean isRunning() {
            return allowProcessing;
        }
    }

    public class TrackingSegmentWorker implements Runnable {

        private Segment segment;

        private volatile TrackingToken lastToken;

        // Tracks if this worker is also responsible for segmentation strategy.
        private boolean isDispachter;

        public TrackingSegmentWorker(Segment segment) {
            this.segment = segment;
        }

        public Segment getSegment() {
            return segment;
        }

        public TrackingToken getLastToken() {
            return lastToken;
        }

        public void setLastToken(TrackingToken lastToken) {
            this.lastToken = lastToken;
        }

        @Override
        public void run() {
            try {
                processingLoop(this);
//                System.out.println("processing loop done");
            } catch (Throwable e) {
                // TODO state change on a single worker...
                logger.error("Processing loop ended due to uncaught exception. Processor pausing.", e);
                state.set(State.PAUSED_ERROR);
            }
        }

        private MessageHandlerInterceptor<EventMessage<?>> getEventMessageMessageHandlerInterceptor() {
            return (unitOfWork, interceptorChain) -> {
                unitOfWork.onPrepareCommit(uow -> {
                    EventMessage<?> event = uow.getMessage();
                    if (event instanceof TrackedEventMessage<?> &&
                            Objects.nonNull(lastToken) && lastToken.equals(((TrackedEventMessage) event).trackingToken())) {
                        tokenStore.storeToken(lastToken, getName(), segment.getSegmentId());
                    }
                });
                return interceptorChain.proceed();
            };
        }

        public boolean isDispatcher() {
            return isDispachter;
        }
    }

    public class AsyncTrackingEventProcessingStrategy {

        int[] storedSegments;

        public void startSegmentWorkers() {

            // Dispatches SegmentWorkers.
            executorService.submit(() -> {

                // TODO, algo to determine the segmentation, We have 3 possible cases.
                // 1. segment == thread max pool size => One thread per segment.
                // 2. segment > thread max pool size => handle multiple identifiers..
                // 3. segment < thread max pool size => Start splitting segments, accross number of available threads.
                // Note, when the SequentialPolicy is not capable to distinguish messages, there is no point in segmenting the stream of messages!
                // Currently these two constructs are not synched.

                executorService.getCorePoolSize();

                storedSegments = tokenStore.fetchSegments(getName());
                Segment[] segments = Segment.computeSegments(storedSegments);

                if(segments.length == 1 && (threadsRemaining() + 1) > segments.length){
                    // Split our segment according to the thread count.
                    segments = segments[0].split();
                }

                for (Segment s : segments) {
                    final TrackingSegmentWorker trackingSegmentWorker = new TrackingSegmentWorker(s);
                    registerInterceptor(trackingSegmentWorker.getEventMessageMessageHandlerInterceptor());
                    if (threadsRemaining() > 0) {
                        executorService.submit(trackingSegmentWorker);
                    } else {
                        // Our dispatcher becomes a worker....no more dispatching....
                        trackingSegmentWorker.run();
                    }
                }
            });
        }

        public int threadsRemaining(){
            return executorService.getMaximumPoolSize() - executorService.getActiveCount();
        }

        public void optimizeWorkers(){

            final int activeCount = executorService.getActiveCount();
            if( storedSegments.length < activeCount){
                // stop our execution, and re-init with the new executor settings. (core and max pool size).
                executorService.shutdown();;
                ensureRunningExecutor();
                // do this stuff later.
            }
        }
    }
}