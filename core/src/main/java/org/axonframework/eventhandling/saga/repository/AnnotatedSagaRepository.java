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

package org.axonframework.eventhandling.saga.repository;

import org.axonframework.common.CollectionUtils;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.saga.AnnotatedSaga;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.ResourceInjector;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.metamodel.DefaultSagaMetaModelFactory;
import org.axonframework.eventhandling.saga.metamodel.SagaModel;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Abstract implementation for saga repositories. This (partial) implementation will take care of the uniqueness of
 * saga
 * instances in the JVM. That means it will prevent multiple instances of the same conceptual Saga (i.e. with same
 * identifier) to exist within the JVM.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaRepository<T> implements SagaRepository<T> {

    private final String unsavedSagasResourceKey;
    private final String managedSagaResourcePrefix;
    private final Class<T> sagaType;
    private final SagaStore<? super T> sagaStore;
    private final SagaModel<T> sagaModel;
    private final ResourceInjector injector;

    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore) {
        this(sagaType, sagaStore, new NoResourceInjector());
    }

    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore, ResourceInjector resourceInjector) {
        this(sagaType, sagaStore, new DefaultSagaMetaModelFactory().modelOf(sagaType), resourceInjector);
    }

    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore,
                                      SagaModel<T> sagaModel, ResourceInjector resourceInjector) {
        this.injector = resourceInjector;
        this.sagaType = sagaType;
        this.sagaStore = sagaStore;
        this.sagaModel = sagaModel;
        this.managedSagaResourcePrefix = "Repository[" + sagaType.getSimpleName() + "]/";
        this.unsavedSagasResourceKey = "Repository[" + sagaType.getSimpleName() + "]/UnsavedSagas";
    }

    @Override
    public AnnotatedSaga<T> load(String sagaIdentifier) {
        String resourceKey = managedSagaResourcePrefix + sagaIdentifier;
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();

        AnnotatedSaga<T> loadedSaga = unitOfWork.root()
                .getOrComputeResource(resourceKey, id ->  doLoadSaga(sagaIdentifier));

        if (loadedSaga != null && unsavedSagaResource(unitOfWork.root())
                .add(sagaIdentifier)) {
            unitOfWork.onPrepareCommit(u -> {
                unsavedSagaResource(u.root()).remove(sagaIdentifier);
                commit(loadedSaga);
            });
        }
        return loadedSaga;
    }

    @Override
    public AnnotatedSaga<T> newInstance(Callable<T> sagaFactory) {
        try {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            T sagaRoot = sagaFactory.call();
            injector.injectResources(sagaRoot);
            AnnotatedSaga<T> saga = new AnnotatedSaga<>(IdentifierFactory.getInstance().generateIdentifier(),
                                                        Collections.emptySet(), sagaRoot, sagaModel);
            unitOfWork.onPrepareCommit(u -> {
                if (saga.isActive()) {
                    storeSaga(saga);
                    saga.getAssociationValues().commit();

                    unsavedSagaResource(unitOfWork)
                            .remove(saga.getSagaIdentifier());
                }
            });

            unsavedSagaResource(unitOfWork).add(saga.getSagaIdentifier());
            unitOfWork.root().resources().put(managedSagaResourcePrefix + saga.getSagaIdentifier(), saga);
            return saga;
        } catch (Exception e) {
            throw new SagaCreationException("An error occurred while attempting to create a new managed instance", e);
        }
    }

    protected Set<String> unsavedSagaResource(UnitOfWork<?> unitOfWork) {
        return unitOfWork.getOrComputeResource(unsavedSagasResourceKey, i -> new HashSet<>());
    }

    protected void commit(AnnotatedSaga<T> saga) {
        if (!saga.isActive()) {
            deleteSaga(saga);
        } else {
            updateSaga(saga);
            saga.getAssociationValues().commit();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> find(AssociationValue associationValue) {
        Set<String> sagasFound = new HashSet<>();
        sagasFound.addAll(sagaStore.findSagas(sagaType, associationValue));
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();

        Set<String> unsavedSagas = unsavedSagaResource(unitOfWork);
        unsavedSagas.stream()
                .map(id -> (AnnotatedSaga<T>) unitOfWork.root().getResource(managedSagaResourcePrefix + id))
                .filter(saga -> saga != null)
                .filter(saga -> saga.getAssociationValues().contains(associationValue))
                .map(AnnotatedSaga::getSagaIdentifier)
                .forEach(sagasFound::add);
        return sagasFound;
    }

    /**
     * Remove the given saga as well as all known association values pointing to it from the repository. If no such
     * saga exists, nothing happens.
     *
     * @param saga The saga instance to remove from the repository
     */
    protected void deleteSaga(AnnotatedSaga<T> saga) {
        Set<AssociationValue> associationValues = CollectionUtils.merge(saga.getAssociationValues().asSet(), saga.getAssociationValues().removedAssociations(), HashSet::new);
        sagaStore.deleteSaga(sagaType, saga.getSagaIdentifier(), associationValues);
    }

    /**
     * Update a stored Saga, by replacing it with the given <code>saga</code> instance.
     *
     * @param saga The saga that has been modified and needs to be updated in the storage
     */
    protected void updateSaga(AnnotatedSaga<T> saga) {
        sagaStore.updateSaga(sagaType, saga.getSagaIdentifier(), saga.root(), saga.trackingToken(), saga.getAssociationValues());
    }

    /**
     * Stores a newly created Saga instance.
     *
     * @param saga The newly created Saga instance to store.
     */
    protected void storeSaga(AnnotatedSaga<T> saga) {
        sagaStore.insertSaga(sagaType, saga.getSagaIdentifier(), saga.root(), saga.trackingToken(), saga.getAssociationValues().asSet());
    }

    protected AnnotatedSaga<T> doLoadSaga(String sagaIdentifier) {
        SagaStore.Entry<T> entry = sagaStore.loadSaga(sagaType, sagaIdentifier);
        if (entry != null) {
            T saga = entry.saga();
            injector.injectResources(saga);
            return new AnnotatedSaga<>(sagaIdentifier, entry.associationValues(), saga, sagaModel);
        }
        return null;
    }
}
