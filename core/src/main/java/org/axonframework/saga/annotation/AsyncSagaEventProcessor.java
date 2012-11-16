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
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public final class AsyncSagaEventProcessor implements EventHandler<AsyncSagaProcessingEvent> {

    private final UnitOfWorkFactory unitOfWorkFactory;
    private final SagaRepository sagaRepository;
    private final Map<String, Saga> processedSagas = new TreeMap<String, Saga>();
    private final int processorCount;
    private final int processorId;
    private UnitOfWork unitOfWork;
    private static final Logger logger = LoggerFactory.getLogger(AsyncSagaEventProcessor.class);

    /**
     * Creates the Disruptor Event Handlers for invoking Sagas. The size of the array returned is equal to the given
     * <code>processorCount</code>.
     *
     * @param sagaRepository    The repository which provides access to the Sagas
     * @param unitOfWorkFactory The factory to create Unit of Work instances with
     * @param processorCount    The number of processors to create
     * @return an array containing the Disruptor Event Handlers to invoke Sagas.
     */
    static EventHandler<AsyncSagaProcessingEvent>[] createInstances(SagaRepository sagaRepository,
                                                                    UnitOfWorkFactory unitOfWorkFactory,
                                                                    int processorCount) {
        AsyncSagaEventProcessor[] processors = new AsyncSagaEventProcessor[processorCount];
        for (int processorId = 0; processorId < processorCount; processorId++) {
            processors[processorId] = new AsyncSagaEventProcessor(sagaRepository,
                                                                  processorCount,
                                                                  processorId,
                                                                  unitOfWorkFactory);
        }
        return processors;
    }

    private AsyncSagaEventProcessor(SagaRepository sagaRepository, int processorCount,
                                    int processorId, UnitOfWorkFactory unitOfWorkFactory) {
        this.sagaRepository = sagaRepository;
        this.processorCount = processorCount;
        this.processorId = processorId;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    @Override
    public void onEvent(AsyncSagaProcessingEvent entry, long sequence, boolean endOfBatch) throws Exception {
        ensureLiveTransaction();
        boolean sagaInvoked = invokeExistingSagas(entry);
        AssociationValue associationValue;
        switch (entry.getHandler().getCreationPolicy()) {
            case ALWAYS:
                associationValue = entry.getAssociationValue();
                if (associationValue != null && ownedByCurrentProcessor(entry.getNewSaga().getSagaIdentifier())) {
                    processedSagas.put(entry.getNewSaga().getSagaIdentifier(), entry.getNewSaga());
                    entry.getNewSaga().handle(entry.getPublishedEvent());
                    entry.getNewSaga().associateWith(associationValue);
                    sagaRepository.add(entry.getNewSaga());
                }
                break;
            case IF_NONE_FOUND:
                associationValue = entry.getAssociationValue();
                boolean shouldCreate = associationValue != null && entry.waitForSagaCreationVote(
                        sagaInvoked, processorCount, ownedByCurrentProcessor(entry.getNewSaga().getSagaIdentifier()));
                if (shouldCreate) {
                    processedSagas.put(entry.getNewSaga().getSagaIdentifier(), entry.getNewSaga());
                    entry.getNewSaga().handle(entry.getPublishedEvent());
                    entry.getNewSaga().associateWith(associationValue);
                    sagaRepository.add(entry.getNewSaga());
                }
        }

        if (endOfBatch) {
            persistProcessedSagas();
        }
    }

    private void ensureLiveTransaction() {
        if (unitOfWork == null) {
            unitOfWork = unitOfWorkFactory.createUnitOfWork();
        }
    }

    @SuppressWarnings("unchecked")
    private void persistProcessedSagas() {
        if (!processedSagas.isEmpty()) {
            ensureLiveTransaction();
            for (Saga saga : processedSagas.values()) {
                sagaRepository.commit(saga);
            }
        }
        if (unitOfWork != null) {
            unitOfWork.commit();
            unitOfWork = null;
        }
        processedSagas.clear();
    }

    private boolean invokeExistingSagas(AsyncSagaProcessingEvent entry) {
        boolean sagaInvoked = false;
        final Class<? extends Saga> sagaType = entry.getSagaType();
        Set<String> sagaIds = sagaRepository.find(sagaType, entry.getAssociationValue());
        for (String sagaId : sagaIds) {
            if (ownedByCurrentProcessor(sagaId)) {
                if (!processedSagas.containsKey(sagaId)) {
                    processedSagas.put(sagaId, sagaRepository.load(sagaId));
                }
            }
        }
        for (Saga saga : processedSagas.values()) {
            if (sagaType.isInstance(saga) && saga.isActive()
                    && saga.getAssociationValues().contains(entry.getAssociationValue())) {
                try {
                    saga.handle(entry.getPublishedEvent());
                } catch (Exception e) {
                    logger.error("Saga threw an exception while handling an Event. Ignoring and moving on...", e);
                }
                sagaInvoked = true;
            }
        }
        return sagaInvoked;
    }

    private boolean ownedByCurrentProcessor(String sagaIdentifier) {
        return processedSagas.containsKey(sagaIdentifier)
                || Math.abs(sagaIdentifier.hashCode() & Integer.MAX_VALUE) % processorCount == processorId;
    }
}
