/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Processes events by forwarding it to Saga instances "owned" by each processor. This processor uses a consistent
 * hashing algorithm to assign the owner of each Saga.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class AsyncSagaEventProcessor implements EventHandler<AsyncSagaProcessingEvent>, LifecycleAware {

    private final UnitOfWorkFactory unitOfWorkFactory;
    private final SagaRepository sagaRepository;
    private final Map<String, Saga> processedSagas = new TreeMap<String, Saga>();
    private final Map<String, Saga> newlyCreatedSagas = new TreeMap<String, Saga>();
    private final int processorCount;
    private final int processorId;
    private final RingBuffer<AsyncSagaProcessingEvent> ringBuffer;
    private final AsyncAnnotatedSagaManager.SagaManagerStatus status;
    private UnitOfWork unitOfWork;
    private static final Logger logger = LoggerFactory.getLogger(AsyncSagaEventProcessor.class);

    /**
     * Creates the Disruptor Event Handlers for invoking Sagas. The size of the array returned is equal to the given
     * <code>processorCount</code>.
     *
     * @param sagaRepository    The repository which provides access to the Sagas
     * @param unitOfWorkFactory The factory to create Unit of Work instances with
     * @param processorCount    The number of processors to create
     * @param ringBuffer        The ringBuffer on which the Processor will operate
     * @param status            The object providing insight in the status of the SagaManager
     * @return an array containing the Disruptor Event Handlers to invoke Sagas.
     */
    static EventHandler<AsyncSagaProcessingEvent>[] createInstances(
            SagaRepository sagaRepository, UnitOfWorkFactory unitOfWorkFactory, int processorCount,
            RingBuffer<AsyncSagaProcessingEvent> ringBuffer, AsyncAnnotatedSagaManager.SagaManagerStatus status) {
        AsyncSagaEventProcessor[] processors = new AsyncSagaEventProcessor[processorCount];
        for (int processorId = 0; processorId < processorCount; processorId++) {
            processors[processorId] = new AsyncSagaEventProcessor(sagaRepository, processorCount, processorId,
                                                                  unitOfWorkFactory, ringBuffer, status);
        }
        return processors;
    }

    private AsyncSagaEventProcessor(SagaRepository sagaRepository, int processorCount, int processorId,
                                    UnitOfWorkFactory unitOfWorkFactory,
                                    RingBuffer<AsyncSagaProcessingEvent> ringBuffer,
                                    AsyncAnnotatedSagaManager.SagaManagerStatus status) {
        this.sagaRepository = sagaRepository;
        this.processorCount = processorCount;
        this.processorId = processorId;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.ringBuffer = ringBuffer;
        this.status = status;
    }

    @Override
    public void onEvent(AsyncSagaProcessingEvent entry, long sequence, boolean endOfBatch) throws Exception {
        boolean sagaInvoked = invokeExistingSagas(entry);
        AssociationValue associationValue;
        switch (entry.getHandler().getCreationPolicy()) {
            case ALWAYS:
                associationValue = entry.getAssociationValue();
                if (associationValue != null && ownedByCurrentProcessor(entry.getNewSaga().getSagaIdentifier())) {
                    processNewSagaInstance(entry, associationValue);
                }
                break;
            case IF_NONE_FOUND:
                associationValue = entry.getAssociationValue();
                boolean shouldCreate = associationValue != null && entry.waitForSagaCreationVote(
                        sagaInvoked, processorCount, ownedByCurrentProcessor(entry.getNewSaga().getSagaIdentifier()));
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
        Set<String> sagaIds = sagaRepository.find(sagaType, entry.getAssociationValue());
        for (String sagaId : sagaIds) {
            if (ownedByCurrentProcessor(sagaId) && !processedSagas.containsKey(sagaId)) {
                ensureActiveUnitOfWork();
                processedSagas.put(sagaId, sagaRepository.load(sagaId));
            }
        }
        for (Saga saga : processedSagas.values()) {
            if (sagaType.isInstance(saga) && saga.isActive()
                    && saga.getAssociationValues().contains(entry.getAssociationValue())) {
                try {
                    ensureActiveUnitOfWork();
                    saga.handle(entry.getPublishedEvent());
                } catch (Exception e) {
                    logger.error("Saga threw an exception while handling an Event. Ignoring and moving on...", e);
                }
                sagaInvoked = true;
            }
        }
        return sagaInvoked;
    }

    @SuppressWarnings("unchecked")
    private boolean persistProcessedSagas(boolean logExceptions) throws Exception {
        try {
            Set<String> committedSagas = new HashSet<String>();
            if (!processedSagas.isEmpty()) {
                ensureActiveUnitOfWork();
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
        ensureActiveUnitOfWork();
        entry.getNewSaga().handle(entry.getPublishedEvent());
        entry.getNewSaga().associateWith(associationValue);
        processedSagas.put(entry.getNewSaga().getSagaIdentifier(), entry.getNewSaga());
        newlyCreatedSagas.put(entry.getNewSaga().getSagaIdentifier(), entry.getNewSaga());
    }

    private void ensureActiveUnitOfWork() {
        if (unitOfWork == null || !unitOfWork.isStarted()) {
            unitOfWork = unitOfWorkFactory.createUnitOfWork();
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
