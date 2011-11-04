/*
 * Copyright (c) 2010-2011. Axon Framework
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
import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaFactory;
import org.axonframework.saga.SagaRepository;

import java.util.Collections;
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
class AsyncSagaEventProcessor implements EventHandler<AsyncSagaProcessingEvent> {

    private final TransactionManager transactionManager;
    private final SagaRepository sagaRepository;
    private final Map<String, Saga> processedSagas = new TreeMap<String, Saga>();
    private final SagaFactory sagaFactory;
    private final int processorCount;
    private final int processorId;
    private TransactionStatus transactionStatus;

    static EventHandler<AsyncSagaProcessingEvent>[] createInstances(SagaRepository sagaRepository,
                                                                    SagaFactory sagaFactory,
                                                                    TransactionManager transactionManager,
                                                                    int processorCount) {
        AsyncSagaEventProcessor[] processors = new AsyncSagaEventProcessor[processorCount];
        for (int processorId = 0; processorId < processorCount; processorId++) {
            processors[processorId] = new AsyncSagaEventProcessor(sagaRepository,
                                                                  sagaFactory,
                                                                  processorCount,
                                                                  processorId,
                                                                  transactionManager);
        }
        return processors;
    }

    private AsyncSagaEventProcessor(SagaRepository sagaRepository, SagaFactory sagaFactory, int processorCount,
                                    int processorId, TransactionManager transactionManager) {
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
        this.processorCount = processorCount;
        this.processorId = processorId;
        this.transactionManager = transactionManager;
    }

    @Override
    public void onEvent(AsyncSagaProcessingEvent entry, long sequence, boolean endOfBatch) throws Exception {
        ensureLiveTransaction();
        boolean sagaInvoked = invokeExistingSagas(entry);
        switch (entry.getHandler().getCreationPolicy()) {
            case ALWAYS:
                if (ownedByCurrentProcessor(entry.getNewSaga().getSagaIdentifier())) {
                    processedSagas.put(entry.getNewSaga().getSagaIdentifier(), entry.getNewSaga());
                    entry.getNewSaga().handle(entry.getPublishedEvent());
                    entry.getNewSaga().associateWith(entry.getAssociationValue());
                    sagaRepository.add(entry.getNewSaga());
                }
                break;
            case IF_NONE_FOUND:
                persistProcessedSagas(true);
                boolean shouldCreate = entry.waitForSagaCreationVote(sagaInvoked, processorCount,
                                                                     ownedByCurrentProcessor(entry.getNewSaga()
                                                                                                  .getSagaIdentifier()));
                if (shouldCreate) {
                    processedSagas.put(entry.getNewSaga().getSagaIdentifier(), entry.getNewSaga());
                    entry.getNewSaga().handle(entry.getPublishedEvent());
                    entry.getNewSaga().associateWith(entry.getAssociationValue());
                    sagaRepository.add(entry.getNewSaga());
                }
        }

        if (endOfBatch) {
            persistProcessedSagas(false);
        }
    }

    private void ensureLiveTransaction() {
        if (transactionStatus == null) {
            transactionStatus = new TransactionStatus();
            transactionManager.beforeTransaction(transactionStatus);
        }
    }

    private void persistProcessedSagas(boolean ensureNewTransaction) {
        if (!processedSagas.isEmpty()) {
            ensureLiveTransaction();
            for (Saga saga : processedSagas.values()) {
                sagaRepository.commit(saga);
            }
        }
        if (transactionStatus != null) {
            transactionManager.afterTransaction(transactionStatus);
            transactionStatus = null;
        }
        processedSagas.clear();
        if (ensureNewTransaction) {
            ensureLiveTransaction();
        }
    }

    private boolean invokeExistingSagas(AsyncSagaProcessingEvent entry) {
        boolean sagaInvoked = false;
        Set<? extends Saga> sagas = sagaRepository.find(entry.getSagaType(),
                                                        Collections.singleton(entry.getAssociationValue()));
        for (Saga saga : sagas) {
            if (ownedByCurrentProcessor(saga.getSagaIdentifier())) {
                processedSagas.put(saga.getSagaIdentifier(), saga);
            }
        }
        for (Saga saga : processedSagas.values()) {
            if (saga.getAssociationValues().contains(entry.getAssociationValue())) {
                saga.handle(entry.getPublishedEvent());
                sagaInvoked = true;
            }
        }
        return sagaInvoked;
    }

    private boolean ownedByCurrentProcessor(String sagaIdentifier) {
        return processedSagas.containsKey(sagaIdentifier) ||
                Math.abs(sagaIdentifier.hashCode()) % processorCount == processorId;
    }
}
