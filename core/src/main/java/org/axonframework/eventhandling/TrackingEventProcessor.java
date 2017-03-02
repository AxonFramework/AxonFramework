/*
 * Copyright (c) 2010-2016. Axon Framework
 *
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
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
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
    private final TransactionManager transactionManager;
    private final int batchSize;
    private final ExecutorService executorService;
    private volatile TrackingToken lastToken;
    private volatile State state = State.NOT_STARTED;

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
             messageSource, tokenStore, transactionManager, batchSize, NoOpMessageMonitor.INSTANCE);
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
             messageSource, tokenStore, transactionManager, 1, messageMonitor);
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
     * @param transactionManager    The transaction manager used when processing messages
     * @param batchSize             The maximum number of events to process in a single batch
     * @param messageMonitor        Monitor to be invoked before and after event processing
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                  StreamableMessageSource<TrackedEventMessage<?>> messageSource, TokenStore tokenStore,
                                  TransactionManager transactionManager, int batchSize,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(name, eventHandlerInvoker, rollbackConfiguration, errorHandler, messageMonitor);
        this.messageSource = requireNonNull(messageSource);
        this.tokenStore = requireNonNull(tokenStore);
        this.transactionManager = transactionManager;
        this.executorService = newSingleThreadExecutor(new AxonThreadFactory("TrackingEventProcessor - " + name));
        registerInterceptor(new TransactionManagingInterceptor<>(transactionManager));
        Assert.isTrue(batchSize > 0, () -> "batchSize needs to be greater than 0");
        this.batchSize = batchSize;
    }

    /**
     * Start this processor. The processor will open an event stream on its message source in a new thread using {@link
     * StreamableMessageSource#openStream(TrackingToken)}. The {@link TrackingToken} used to open the stream will be
     * fetched from the {@link TokenStore}.
     */
    @Override
    public void start() {
        if (state == State.NOT_STARTED) {
            state = State.STARTED;
            registerInterceptor((unitOfWork, interceptorChain) -> {
                unitOfWork.onPrepareCommit(uow -> {
                    EventMessage<?> event = uow.getMessage();
                    if (event instanceof TrackedEventMessage<?> &&
                            lastToken != null &&
                            lastToken.equals(((TrackedEventMessage) event).trackingToken())) {
                        tokenStore.storeToken(lastToken, getName(), 0);
                    }
                });
                return interceptorChain.proceed();
            });
            executorService.submit(() -> {
                try {
                    this.processingLoop();
                } catch (Throwable e) {
                    logger.error("Processing loop ended due to uncaught exception. Processor stopping.", e);
                }
            });
        }
    }

    /**
     * Shut down the processor.
     */
    @Override
    public void shutDown() {
        if (state != State.SHUT_DOWN) {
            state = State.SHUT_DOWN;
            executorService.shutdown();
        }
    }

    /**
     * Fetch and process event batches continuously for as long as the processor is not shutting down. The processor
     * will process events in batches. The maximum size of size of each event batch is configurable.
     * <p>
     * Events with the same tracking token (which is possible as result of upcasting) should always be processed in
     * the same batch. In those cases the batch size may be larger than the one configured.
     */
    protected void processingLoop() {
        MessageStream<TrackedEventMessage<?>> eventStream = null;
        long errorWaitTime = 1;
        try {
            while (state != State.SHUT_DOWN) {
                eventStream = ensureEventStreamOpened(eventStream);
                try {
                    processBatch(eventStream);
                    errorWaitTime = 1;
                } catch (Exception e) {
                    // make sure to start with a clean event stream. The exception may have cause an illegal state
                    if (errorWaitTime == 1) {
                        logger.warn("Error occurred. Starting retry mode.", e);
                    }
                    logger.warn("Releasing claim on token and preparing for retry in {}s", errorWaitTime);
                    releaseToken();
                    closeQuietly(eventStream);
                    eventStream = null;
                    try {
                        Thread.sleep(errorWaitTime * 1000);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                        logger.warn("Thread interrupted. Preparing to shut down event processor");
                        shutDown();
                    }
                    errorWaitTime = Math.min(errorWaitTime * 2, 60);
                }
            }
        } finally {
            closeQuietly(eventStream);
            releaseToken();
        }
    }

    private void releaseToken() {
        try {
            transactionManager.executeInTransaction(() -> tokenStore.releaseClaim(getName(), 0));
        } catch (Exception e) {
            // whatever.
        }
    }

    private void processBatch(MessageStream<TrackedEventMessage<?>> eventStream) throws Exception {
        List<TrackedEventMessage<?>> batch = new ArrayList<>();
        try {
            if (eventStream.hasNextAvailable(1, TimeUnit.SECONDS)) {
                while (batch.size() < batchSize && eventStream.hasNextAvailable()) {
                    batch.add(eventStream.nextAvailable());
                }
            }
            if (batch.isEmpty()) {
                // refresh claim on token
                transactionManager.executeInTransaction(() -> tokenStore.extendClaim(getName(), 0));
                return;
            }

            // make sure all subsequent events with the same token (if non-null) as the last are added as well.
            // These are the result of upcasting and should always be processed in the same batch.
            lastToken = batch.get(batch.size() - 1).trackingToken();
            while (lastToken != null && eventStream.peek().filter(event -> lastToken.equals(event.trackingToken())).isPresent()) {
                batch.add(eventStream.nextAvailable());
            }

            process(batch);

        } catch (InterruptedException e) {
            logger.error(String.format("Event processor [%s] was interrupted. Shutting down.", getName()), e);
            Thread.currentThread().interrupt();
            this.shutDown();
        }
    }

    private MessageStream<TrackedEventMessage<?>> ensureEventStreamOpened(
            MessageStream<TrackedEventMessage<?>> eventStreamIn) {
        MessageStream<TrackedEventMessage<?>> eventStream = eventStreamIn;
        while (eventStream == null && state == State.STARTED) {
            Transaction tx = transactionManager.startTransaction();
            try {
                TrackingToken startToken = tokenStore.fetchToken(getName(), 0);
                eventStream = messageSource.openStream(startToken);
                tx.commit();
            } catch (UnableToClaimTokenException e) {
                tx.rollback();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interrupt) {
                    logger.info("Thread interrupted while waiting for new attempt to claim token");
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                logger.warn("Unexpected exception while attempting to retrieve token and open stream. " +
                                    "Retrying in 5 seconds.", e);
                tx.rollback();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interrupt) {
                    logger.info("Thread interrupted while waiting for new attempt to claim token");
                    Thread.currentThread().interrupt();
                }
            }
        }
        return eventStream;
    }

    /**
     * Get the state of the event processor. This will indicate whether or not the processor has started or is shutting
     * down.
     *
     * @return the processor state
     */
    protected State getState() {
        return state;
    }

    private enum State {
        NOT_STARTED, STARTED, SHUT_DOWN
    }
}
