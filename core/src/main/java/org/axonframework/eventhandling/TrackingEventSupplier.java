/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Supplies events to an event processor by opening a {@link TrackingEventStream} on the Event Bus and passing events in
 * the stream to the event processor one by one. Once the event processor successfully processes an event in the context
 * of a unit of work and the unit of work is committed the next event is supplied to the processor.
 * <p>
 * Each time an event is successfully processed the supplier updates the tracking token for the processor in the token
 * store.
 *
 * @author Rene de Waele
 */
public class TrackingEventSupplier {

    private static final Logger logger = LoggerFactory.getLogger(TrackingEventSupplier.class);
    private static final ThreadGroup threadGroup = new ThreadGroup(TrackingEventSupplier.class.getSimpleName());

    private final EventBus eventBus;
    private final EventProcessor eventProcessor;
    private final TokenStore tokenStore;
    private final int segment;
    private final ExecutorService executorService = newSingleThreadExecutor(new AxonThreadFactory(threadGroup));
    private TrackingEventStream eventStream;
    private volatile TrackingToken lastToken;

    public TrackingEventSupplier(EventBus eventBus, TokenStore tokenStore, EventProcessor eventProcessor) {
        this(eventBus, tokenStore, eventProcessor, 0);
    }

    public TrackingEventSupplier(EventBus eventBus, TokenStore tokenStore, EventProcessor eventProcessor, int segment) {
        this.eventBus = eventBus;
        this.tokenStore = tokenStore;
        this.eventProcessor = eventProcessor;
        this.segment = segment;
    }

    @PostConstruct
    public void initialize() {
        eventProcessor.registerInterceptor((unitOfWork, interceptorChain) -> {
            unitOfWork.onPrepareCommit(uow -> {
                EventMessage<?> event = uow.getMessage();
                if (event instanceof TrackedEventMessage<?> &&
                        ((TrackedEventMessage) event).trackingToken().equals(lastToken)) {
                    tokenStore.storeToken(eventProcessor.getName(), segment, lastToken);
                }
            });
            unitOfWork.afterCommit(uow -> {
                EventMessage<?> event = uow.getMessage();
                if (event instanceof TrackedEventMessage<?> &&
                        ((TrackedEventMessage) event).trackingToken().equals(lastToken)) {
                    supplyNextEvent();
                }
            });
            return interceptorChain.proceed();
        });
        supplyNextEvent();
    }

    @PreDestroy
    public void shutDown() {
        executorService.shutdown();
    }

    private void supplyNextEvent() {
        if (!executorService.isShutdown()) {
            executorService.submit(() -> {
                if (eventStream == null) {
                    TrackingToken startToken = tokenStore.fetchToken(eventProcessor.getName(), segment);
                    eventStream = eventBus.streamEvents(startToken);
                }
                try {
                    TrackedEventMessage<?> nextEvent = eventStream.nextAvailable();
                    if (executorService.isShutdown()) {
                        return;
                    }
                    lastToken = nextEvent.trackingToken();
                    eventProcessor.accept(nextEvent);
                } catch (InterruptedException e) {
                    logger.error(String.format("Event supplier thread for %s was interrupted. Shutting down.",
                                               eventProcessor.getName()), e);
                    Thread.currentThread().interrupt();
                }
            });
        }
    }
}
