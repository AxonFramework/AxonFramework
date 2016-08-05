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
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
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

/**
 * EventProcessor implementation that {@link EventBus#streamEvents(TrackingToken) tracks} events published to the {@link
 * EventBus}.
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
    private static final ThreadGroup threadGroup = new ThreadGroup(TrackingEventProcessor.class.getSimpleName());

    private final EventBus eventBus;
    private final TokenStore tokenStore;
    private final int batchSize;
    private final ExecutorService executorService = newSingleThreadExecutor(new AxonThreadFactory(threadGroup));
    private TrackingEventStream eventStream;
    private volatile TrackingToken lastToken;
    private volatile State state = State.NOT_STARTED;

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code eventBus} for events.
     * Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     * <p>
     * The EventProcessor is initialized with a batch size of 1, a {@link NoOpErrorHandler}, a {@link
     * RollbackConfigurationType#ANY_THROWABLE} and a {@link NoOpMessageMonitor}.
     *
     * @param name                The name of the event processor
     * @param eventHandlerInvoker The component that handles the individual events
     * @param eventBus            The EventBus which this event processor will track
     * @param tokenStore          Used to store and fetch event tokens that enable the processor to track its progress
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker, EventBus eventBus,
                                  TokenStore tokenStore) {
        this(name, eventHandlerInvoker, eventBus, tokenStore, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code eventBus} for events.
     * Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     * <p>
     * The EventProcessor is initialized with a batch size of 1, a {@link NoOpErrorHandler} and a {@link
     * RollbackConfigurationType#ANY_THROWABLE}.
     *
     * @param name                The name of the event processor
     * @param eventHandlerInvoker The component that handles the individual events
     * @param eventBus            The EventBus which this event processor will track
     * @param tokenStore          Used to store and fetch event tokens that enable the processor to track its progress
     * @param messageMonitor      Monitor to be invoked before and after event processing
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker, EventBus eventBus,
                                  TokenStore tokenStore, MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this(name, eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, NoOpErrorHandler.INSTANCE, eventBus,
             tokenStore, 1, messageMonitor);
    }

    /**
     * Initializes an EventProcessor with given {@code name} that subscribes to the given {@code eventBus} for events.
     * Actual handling of event messages is deferred to the given {@code eventHandlerInvoker}.
     *
     * @param name                  The name of the event processor
     * @param eventHandlerInvoker   The component that handles the individual events
     * @param rollbackConfiguration Determines rollback behavior of the UnitOfWork while processing a batch of events
     * @param eventBus              The EventBus which this event processor will track
     * @param errorHandler          Invoked when a UnitOfWork is rolled back during processing
     * @param tokenStore            Used to store and fetch event tokens that enable the processor to track its
     *                              progress
     * @param batchSize             The maximum number of events to process in a single batch
     * @param messageMonitor        Monitor to be invoked before and after event processing
     */
    public TrackingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                  EventBus eventBus, TokenStore tokenStore, int batchSize,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(name, eventHandlerInvoker, rollbackConfiguration, errorHandler, messageMonitor);
        this.eventBus = requireNonNull(eventBus);
        this.tokenStore = requireNonNull(tokenStore);
        Assert.isTrue(batchSize > 0, "batchSize needs to be greater than 0");
        this.batchSize = batchSize;
    }

    /**
     * Start this processor. The processor will open an event stream on the {@link EventBus} in a new thread using
     * {@link EventBus#streamEvents(TrackingToken)}. The {@link TrackingToken} used to open the stream will be
     * fetched from the {@link TokenStore}.
     */
    public void start() {
        if (state == State.NOT_STARTED) {
            state = State.STARTED;
            registerInterceptor((unitOfWork, interceptorChain) -> {
                unitOfWork.onPrepareCommit(uow -> {
                    EventMessage<?> event = uow.getMessage();
                    if (event instanceof TrackedEventMessage<?> &&
                            ((TrackedEventMessage) event).trackingToken().equals(lastToken)) {
                        tokenStore.storeToken(lastToken, getName(), 0);
                    }
                });
                return interceptorChain.proceed();
            });
            executorService.submit(() -> {
                try {
                    this.doProcess();
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    /**
     * Shut down the processor.
     */
    public void shutDown() {
        if (state != State.SHUT_DOWN) {
            state = State.SHUT_DOWN;
            executorService.shutdown();
        }
    }

    /**
     * Fetch and process event batches continuously for as long as the processor is not shutting down. The processor
     * will process events in batches. The maximum size of size of each event batch is configurable.
     */
    protected void doProcess() {
        while (state != State.SHUT_DOWN) {
            if (eventStream == null) {
                TrackingToken startToken = tokenStore.fetchToken(getName(), 0);
                eventStream = eventBus.streamEvents(startToken);
            }
            List<TrackedEventMessage<?>> batch = new ArrayList<>();
            try {
                if (batch.isEmpty()) {
                    if (eventStream.hasNextAvailable(1, TimeUnit.SECONDS)) {
                        batch.add(eventStream.nextAvailable());
                    }
                }
                while (batch.size() < batchSize && eventStream.hasNextAvailable()) {
                    batch.add(eventStream.nextAvailable());
                }
            } catch (InterruptedException e) {
                logger.error(String.format("Event processor [%s] was interrupted. Shutting down.", getName()), e);
                Thread.currentThread().interrupt();
                return;
            }
            if (!batch.isEmpty()) {
                lastToken = batch.get(batch.size() - 1).trackingToken();
                process(batch);
            }
        }
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
