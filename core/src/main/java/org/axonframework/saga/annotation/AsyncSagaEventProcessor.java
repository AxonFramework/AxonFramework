/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.saga.annotation;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import org.axonframework.common.AxonNonTransientException;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.messaging.CorrelationDataProvider;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Processes events by forwarding it to Saga instances "owned" by each processor. This processor uses a consistent
 * hashing algorithm to assign the owner of each Saga.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class AsyncSagaEventProcessor implements EventHandler<AsyncSagaProcessingEvent>, LifecycleAware {

    private static final Logger logger = LoggerFactory.getLogger(AsyncSagaEventProcessor.class);
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final SagaRepository sagaRepository;
    private final Map<String, Saga> processedSagas = new TreeMap<>();
    private final Map<String, Saga> newlyCreatedSagas = new TreeMap<>();
    private final ParameterResolverFactory parameterResolverFactory;
    private final int processorCount;
    private final int processorId;
    private final RingBuffer<AsyncSagaProcessingEvent> ringBuffer;
    private final AsyncAnnotatedSagaManager.SagaManagerStatus status;
    private final CorrelationDataProvider correlationDataProvider;
    private UnitOfWork unitOfWork;

    private AsyncSagaEventProcessor(SagaRepository sagaRepository, ParameterResolverFactory parameterResolverFactory,
                                    int processorCount, int processorId,
                                    UnitOfWorkFactory unitOfWorkFactory,
                                    RingBuffer<AsyncSagaProcessingEvent> ringBuffer,
                                    AsyncAnnotatedSagaManager.SagaManagerStatus status,
                                    CorrelationDataProvider correlationDataProvider) {
        this.sagaRepository = sagaRepository;
        this.parameterResolverFactory = parameterResolverFactory;
        this.processorCount = processorCount;
        this.processorId = processorId;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.ringBuffer = ringBuffer;
        this.status = status;
        this.correlationDataProvider = correlationDataProvider;
    }

    /**
     * Creates the Disruptor Event Handlers for invoking Sagas. The size of the array returned is equal to the given
     * <code>processorCount</code>.
     *
     * @param sagaRepository           The repository which provides access to the Sagas
     * @param parameterResolverFactory The parameter resolver to resolve parameters of annotated methods
     * @param unitOfWorkFactory        The factory to create Unit of Work instances with
     * @param processorCount           The number of processors to create
     * @param ringBuffer               The ringBuffer on which the Processor will operate
     * @param status                   The object providing insight in the status of the SagaManager     @return an
     *                                 array containing the Disruptor Event Handlers to invoke Sagas.
     * @param correlationDataProvider
     * @return the processor instances that will process the incoming events
     */
    static EventHandler<AsyncSagaProcessingEvent>[] createInstances(
            SagaRepository sagaRepository, ParameterResolverFactory parameterResolverFactory,
            UnitOfWorkFactory unitOfWorkFactory, int processorCount,
            RingBuffer<AsyncSagaProcessingEvent> ringBuffer, AsyncAnnotatedSagaManager.SagaManagerStatus status,
            CorrelationDataProvider correlationDataProvider) {
        AsyncSagaEventProcessor[] processors = new AsyncSagaEventProcessor[processorCount];
        for (int processorId = 0; processorId < processorCount; processorId++) {
            processors[processorId] = new AsyncSagaEventProcessor(sagaRepository,
                                                                  parameterResolverFactory,
                                                                  processorCount,
                                                                  processorId,
                                                                  unitOfWorkFactory,
                                                                  ringBuffer,
                                                                  status,
                                                                  correlationDataProvider);
        }
        return processors;
    }

    @Override
    public void onEvent(AsyncSagaProcessingEvent entry, long sequence, boolean endOfBatch) throws Exception {
        Map<String, ?> correlationData = correlationDataProvider.correlationDataFor(entry.getPublishedEvent());
        CorrelationDataHolder.setCorrelationData(correlationData);
        try {
            doProcessEvent(entry, sequence, endOfBatch);
        } finally {
            CorrelationDataHolder.clear();
        }
    }

    private void doProcessEvent(AsyncSagaProcessingEvent entry, long sequence, boolean endOfBatch)
            throws Exception {
        boolean sagaInvoked = invokeExistingSagas(entry);
        AssociationValue associationValue;
        switch (entry.getCreationHandler().getCreationPolicy()) {
            case ALWAYS:
                associationValue = entry.getInitialAssociationValue();
                if (associationValue != null && ownedByCurrentProcessor(entry.getNewSaga().getSagaIdentifier())) {
                    processNewSagaInstance(entry, associationValue);
                }
                break;
            case IF_NONE_FOUND:
                associationValue = entry.getInitialAssociationValue();
                boolean shouldCreate = associationValue != null && entry.waitForSagaCreationVote(
                        sagaInvoked, processorCount, ownedByCurrentProcessor(entry.getNewSaga()
                                                                                  .getSagaIdentifier()));
                if (shouldCreate) {
                    processNewSagaInstance(entry, associationValue);
                }
        }

        if (endOfBatch) {
            int attempts = 0;
            while (!persistProcessedSagas(attempts == 0) && isLastInBacklog(sequence) && status.isRunning()) {
                if (attempts == 0) {
                    logger.warn("Error committing Saga state to the repository. Starting retry procedure...");
                }
                attempts++;
                if (attempts > 1 && attempts < 5) {
                    logger.info("Waiting 100ms for next attempt");
                    Thread.sleep(100);
                } else if (attempts >= 5) {
                    logger.info("Waiting 2000ms for next attempt");
                    long timeToStop = System.currentTimeMillis() + 2000;
                    while (inFuture(timeToStop) && isLastInBacklog(sequence) && status.isRunning()) {
                        Thread.sleep(100);
                    }
                }
            }
        }
    }

    private boolean inFuture(long timestamp) {
        return System.currentTimeMillis() < timestamp;
    }

    private boolean invokeExistingSagas(AsyncSagaProcessingEvent entry) {
        boolean sagaInvoked = false;
        final Class<? extends Saga> sagaType = entry.getSagaType();
        Set<String> sagaIds = new HashSet<>();
        for (AssociationValue associationValue : entry.getAssociationValues()) {
            sagaIds.addAll(sagaRepository.find(sagaType, associationValue));
        }
        for (String sagaId : sagaIds) {
            if (ownedByCurrentProcessor(sagaId) && !processedSagas.containsKey(sagaId)) {
                ensureActiveUnitOfWork(entry.getPublishedEvent());
                final Saga saga = sagaRepository.load(sagaId);
                if (parameterResolverFactory != null) {
                    ((AbstractAnnotatedSaga) saga).registerParameterResolverFactory(parameterResolverFactory);
                }
                processedSagas.put(sagaId, saga);
            }
        }
        for (Saga saga : processedSagas.values()) {
            if (sagaType.isInstance(saga) && saga.isActive()
                    && containsAny(saga.getAssociationValues(), entry.getAssociationValues())) {
                try {
                    ensureActiveUnitOfWork(entry.getPublishedEvent());
                    saga.handle(entry.getPublishedEvent());
                } catch (Exception e) {
                    logger.error("Saga threw an exception while handling an Event. Ignoring and moving on...", e);
                }
                sagaInvoked = true;
            }
        }
        return sagaInvoked;
    }

    private boolean containsAny(AssociationValues associationValues, Collection<AssociationValue> toFind) {
        for (AssociationValue valueToFind : toFind) {
            if (associationValues.contains(valueToFind)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean persistProcessedSagas(boolean logExceptions) throws Exception {
        try {
            Set<String> committedSagas = new HashSet<>();
            if (!processedSagas.isEmpty()) {
                ensureActiveUnitOfWork(null);
                for (Saga saga : processedSagas.values()) {
                    if (newlyCreatedSagas.containsKey(saga.getSagaIdentifier())) {
                        sagaRepository.add(saga);
                    } else {
                        sagaRepository.commit(saga);
                    }
                    committedSagas.add(saga.getSagaIdentifier());
                }
            }
            if (unitOfWork != null) {
                unitOfWork.commit();
                unitOfWork = null;
            }
            processedSagas.keySet().removeAll(committedSagas);
            newlyCreatedSagas.keySet().removeAll(committedSagas);
            return true;
        } catch (Exception e) {
            if (AxonNonTransientException.isCauseOf(e)) {
                throw e;
            }
            if (logExceptions) {
                logger.warn("Exception while attempting to persist Sagas", e);
            }
            return false;
        }
    }

    private boolean isLastInBacklog(long sequence) {
        return ringBuffer.getCursor() <= sequence;
    }

    private void processNewSagaInstance(AsyncSagaProcessingEvent entry, AssociationValue associationValue) {
        ensureActiveUnitOfWork(entry.getPublishedEvent());
        final AbstractAnnotatedSaga newSaga = entry.getNewSaga();
        if (parameterResolverFactory != null) {
            newSaga.registerParameterResolverFactory(parameterResolverFactory);
        }
        newSaga.associateWith(associationValue);
        newSaga.handle(entry.getPublishedEvent());
        processedSagas.put(newSaga.getSagaIdentifier(), newSaga);
        newlyCreatedSagas.put(newSaga.getSagaIdentifier(), newSaga);
    }

    private void ensureActiveUnitOfWork(Message<?> message) {
        if (unitOfWork == null || !unitOfWork.isActive()) {
            // TODO: Implement batching support
            unitOfWork = unitOfWorkFactory.createUnitOfWork(message);
        }
    }

    private boolean ownedByCurrentProcessor(String sagaIdentifier) {
        return processedSagas.containsKey(sagaIdentifier)
                || Math.abs(sagaIdentifier.hashCode() & Integer.MAX_VALUE) % processorCount == processorId;
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onShutdown() {
        try {
            if (!persistProcessedSagas(true)) {
                logger.error(
                        "The processor was shut down while some Saga instances could not be persisted. As a result,"
                                + "persisted Saga state may not properly reflect the activity of those Sagas.");
            }
        } catch (Exception e) {
            logger.error("A fatal, non-transient exception occurred while attempting to persist Saga state", e);
        }
    }
}
