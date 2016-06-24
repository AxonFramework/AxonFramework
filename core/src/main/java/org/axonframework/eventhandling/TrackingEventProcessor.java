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

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
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

    public TrackingEventProcessor(EventHandlerInvoker eventHandlerInvoker, EventBus eventBus, TokenStore tokenStore) {
        this(eventHandlerInvoker, eventBus, tokenStore, NoOpMessageMonitor.INSTANCE);
    }

    public TrackingEventProcessor(EventHandlerInvoker eventHandlerInvoker, EventBus eventBus, TokenStore tokenStore,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this(eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, NoOpErrorHandler.INSTANCE, eventBus,
             tokenStore, 1, messageMonitor);
    }

    public TrackingEventProcessor(EventHandlerInvoker eventHandlerInvoker, RollbackConfiguration rollbackConfiguration,
                                  ErrorHandler errorHandler, EventBus eventBus, TokenStore tokenStore, int batchSize,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(eventHandlerInvoker, rollbackConfiguration, errorHandler, messageMonitor);
        this.eventBus = requireNonNull(eventBus);
        this.tokenStore = requireNonNull(tokenStore);
        Assert.isTrue(batchSize > 0, "batchSize needs to be greater than 0");
        this.batchSize = batchSize;
    }

    public void start() {
        registerInterceptor((unitOfWork, interceptorChain) -> {
            unitOfWork.onPrepareCommit(uow -> {
                EventMessage<?> event = uow.getMessage();
                if (event instanceof TrackedEventMessage<?> &&
                        ((TrackedEventMessage) event).trackingToken().equals(lastToken)) {
                    tokenStore.storeToken(getName(), 0, lastToken);
                }
            });
            unitOfWork.afterCommit(uow -> {
                EventMessage<?> event = uow.getMessage();
                if (event instanceof TrackedEventMessage<?> &&
                        ((TrackedEventMessage) event).trackingToken().equals(lastToken)) {
                    supplyNextBatch();
                }
            });
            return interceptorChain.proceed();
        });
        supplyNextBatch();
    }

    public void shutDown() {
        executorService.shutdown();
    }

    private void supplyNextBatch() {
        if (!executorService.isShutdown()) {
            executorService.submit(this::doSupplyNextBatch);
        }
    }

    protected void doSupplyNextBatch() {
        if (eventStream == null) {
            TrackingToken startToken = tokenStore.fetchToken(getName(), 0);
            eventStream = eventBus.streamEvents(startToken);
        }
        List<TrackedEventMessage<?>> batch = new ArrayList<>();
        try {
            while (!executorService.isShutdown() &&
                    (batch.isEmpty() || (batch.size() < batchSize && eventStream.hasNextAvailable()))) {
                batch.add(eventStream.nextAvailable());
            }
        } catch (InterruptedException e) {
            logger.error(String.format("Event processor [%s] was interrupted. Shutting down.", getName()), e);
            Thread.currentThread().interrupt();
            return;
        }
        if (!batch.isEmpty()) {
            lastToken = batch.get(batch.size() - 1).trackingToken();
            accept(batch);
        }
    }
}
