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
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventhandling.saga.AnnotatedSaga;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.ResourceInjector;
import org.axonframework.eventhandling.saga.Saga;
import org.axonframework.eventhandling.saga.metamodel.DefaultSagaMetaModelFactory;
import org.axonframework.eventhandling.saga.metamodel.SagaModel;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Abstract implementation for saga repositories. This (partial) implementation will take care of the uniqueness of
 * saga
 * instances in the JVM. That means it will prevent multiple instances of the same conceptual Saga (i.e. with same
 * identifier) to exist within the JVM.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaRepository<T> extends LockingSagaRepository<T> {

    private final String unsavedSagasResourceKey;
    private final Map<String, AnnotatedSaga<T>> managedSagas;
    private final Class<T> sagaType;
    private final SagaStore<? super T> sagaStore;
    private final SagaModel<T> sagaModel;
    private final ResourceInjector injector;

    /**
     * Initializes an AnnotatedSagaRepository for given {@code sagaType} that stores sagas in the given {@code
     * sagaStore}. The repository will use a {@link DefaultSagaMetaModelFactory} and {@link NoResourceInjector} to
     * initialize {@link Saga} instances after a target instance is created or loaded from the store. This repository
     * uses a {@link PessimisticLockFactory} when a {@link Saga} is loaded.
     *
     * @param sagaType  the saga target type
     * @param sagaStore the saga store for saving and loading of sagas
     */
    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore) {
        this(sagaType, sagaStore, NoResourceInjector.INSTANCE);
    }

    /**
     * Initializes an AnnotatedSagaRepository for given {@code sagaType} that stores sagas in the given {@code
     * sagaStore}. The repository will use given {@code resourceInjector} and {@link DefaultSagaMetaModelFactory} to
     * initialize {@link Saga} instances after a target instance is created or loaded from the store. This repository
     * uses a {@link PessimisticLockFactory} when a {@link Saga} is loaded.
     *
     * @param sagaType         the saga target type
     * @param sagaStore        the saga store for saving and loading of sagas
     * @param resourceInjector the resource injector used to initialize {@link Saga Sagas} that delegate to the target
     */
    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore,
                                   ResourceInjector resourceInjector) {
        this(sagaType, sagaStore, new DefaultSagaMetaModelFactory().modelOf(sagaType), resourceInjector,
             new PessimisticLockFactory());
    }

    /**
     * Initializes an AnnotatedSagaRepository for given {@code sagaType} that stores sagas in the given {@code
     * sagaStore}. The repository will use given {@code resourceInjector} and {@link DefaultSagaMetaModelFactory} to
     * initialize {@link Saga} instances after a target instance is created or loaded from the store. This repository
     * uses a {@link PessimisticLockFactory} when a {@link Saga} is loaded.
     *
     * @param sagaType                 the saga target type
     * @param sagaStore                the saga store for saving and loading of sagas
     * @param resourceInjector         the resource injector used to initialize {@link Saga Sagas} that delegate to the target
     * @param parameterResolverFactory The ParameterResolverFactory instance to resolve parameter values for annotated
     *                                 handlers with
     */
    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore,
                                   ResourceInjector resourceInjector,
                                   ParameterResolverFactory parameterResolverFactory) {
        this(sagaType, sagaStore, new DefaultSagaMetaModelFactory(parameterResolverFactory).modelOf(sagaType), resourceInjector,
             new PessimisticLockFactory());
    }

    /**
     * Initializes an AnnotatedSagaRepository for given {@code sagaType} that stores sagas in the given {@code
     * sagaStore}. The repository will use the given {@code sagaModel} and {@code resourceInjector} to initialize
     * {@link Saga} instances after a target instance is created or loaded from the store.
     *
     * @param sagaType         the saga target type
     * @param sagaStore        the saga store for saving and loading of sagas
     * @param sagaModel        the saga model used to initialize {@link Saga Sagas} that delegate to the target
     * @param resourceInjector the resource injector used to initialize {@link Saga Sagas} that delegate to the target
     * @param lockFactory      lock factory used when a saga is loaded
     */
    public AnnotatedSagaRepository(Class<T> sagaType, SagaStore<? super T> sagaStore, SagaModel<T> sagaModel,
                                   ResourceInjector resourceInjector, LockFactory lockFactory) {
        super(lockFactory);
        this.injector = resourceInjector;
        this.sagaType = sagaType;
        this.sagaStore = sagaStore;
        this.sagaModel = sagaModel;
        this.managedSagas = new ConcurrentHashMap<>();
        this.unsavedSagasResourceKey = "Repository[" + sagaType.getSimpleName() + "]/UnsavedSagas";
    }

    @Override
    public AnnotatedSaga<T> doLoad(String sagaIdentifier) {
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get(), processRoot = unitOfWork.root();

        AnnotatedSaga<T> loadedSaga = managedSagas.computeIfAbsent(sagaIdentifier, id -> {
            AnnotatedSaga<T> result = doLoadSaga(sagaIdentifier);
            if (result != null) {
                processRoot.onCleanup(u -> managedSagas.remove(id));
            }
            return result;
        });

        if (loadedSaga != null && unsavedSagaResource(processRoot).add(sagaIdentifier)) {
            unitOfWork.onPrepareCommit(u -> {
                unsavedSagaResource(processRoot).remove(sagaIdentifier);
                commit(loadedSaga);
            });
        }
        return loadedSaga;
    }

    @Override
    public AnnotatedSaga<T> doCreateInstance(String sagaIdentifier, Supplier<T> sagaFactory) {
        try {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get(), processRoot = unitOfWork.root();
            T sagaRoot = sagaFactory.get();
            injector.injectResources(sagaRoot);
            AnnotatedSaga<T> saga =
                    new AnnotatedSaga<>(sagaIdentifier, Collections.emptySet(), sagaRoot, null, sagaModel);

            unsavedSagaResource(processRoot).add(sagaIdentifier);
            unitOfWork.onPrepareCommit(u -> {
                if (saga.isActive()) {
                    storeSaga(saga);
                    saga.getAssociationValues().commit();
                    unsavedSagaResource(processRoot).remove(sagaIdentifier);
                }
            });

            managedSagas.put(sagaIdentifier, saga);
            processRoot.onCleanup(u -> managedSagas.remove(sagaIdentifier));
            return saga;
        } catch (Exception e) {
            throw new SagaCreationException("An error occurred while attempting to create a new managed instance", e);
        }
    }

    /**
     * Returns a set of identifiers of sagas that may have changed in the context of the given {@code unitOfWork} and
     * have not been saved yet.
     *
     * @param unitOfWork the unit of work to inspect for unsaved sagas
     * @return set of saga identifiers of unsaved sagas
     */
    protected Set<String> unsavedSagaResource(UnitOfWork<?> unitOfWork) {
        return unitOfWork.getOrComputeResource(unsavedSagasResourceKey, i -> new HashSet<>());
    }

    /**
     * Commits the given modified {@code saga} to the underlying saga store. If the saga is not active anymore it will
     * be deleted. Otherwise the stored saga and its associations will be updated.
     *
     * @param saga the saga to commit to the store
     */
    protected void commit(AnnotatedSaga<T> saga) {
        if (!saga.isActive()) {
            deleteSaga(saga);
        } else {
            updateSaga(saga);
            saga.getAssociationValues().commit();
        }
    }

    @Override
    public Set<String> find(AssociationValue associationValue) {
        Set<String> sagasFound = new TreeSet<>();
        sagasFound.addAll(managedSagas.values().stream()
                                  .filter(saga -> saga.getAssociationValues().contains(associationValue))
                                  .map(Saga::getSagaIdentifier).collect(Collectors.toList()));
        sagasFound.addAll(sagaStore.findSagas(sagaType, associationValue));
        return sagasFound;
    }

    /**
     * Remove the given saga as well as all known association values pointing to it from the repository. If no such
     * saga exists, nothing happens.
     *
     * @param saga The saga instance to remove from the repository
     */
    protected void deleteSaga(AnnotatedSaga<T> saga) {
        Set<AssociationValue> associationValues = CollectionUtils
                .merge(saga.getAssociationValues().asSet(), saga.getAssociationValues().removedAssociations(),
                       HashSet::new);
        sagaStore.deleteSaga(sagaType, saga.getSagaIdentifier(), associationValues);
    }

    /**
     * Update a stored Saga, by replacing it with the given {@code saga} instance.
     *
     * @param saga The saga that has been modified and needs to be updated in the storage
     */
    protected void updateSaga(AnnotatedSaga<T> saga) {
        sagaStore.updateSaga(sagaType, saga.getSagaIdentifier(), saga.root(), saga.trackingToken(),
                             saga.getAssociationValues());
    }

    /**
     * Stores a newly created Saga instance.
     *
     * @param saga The newly created Saga instance to store.
     */
    protected void storeSaga(AnnotatedSaga<T> saga) {
        sagaStore.insertSaga(sagaType, saga.getSagaIdentifier(), saga.root(), saga.trackingToken(),
                             saga.getAssociationValues().asSet());
    }

    /**
     * Loads the saga with given {@code sagaIdentifier} from the underlying saga store and returns it as a {@link
     * AnnotatedSaga}. Resources of the saga will be injected using the {@link ResourceInjector} configured with the
     * repository.
     *
     * @param sagaIdentifier the identifier of the saga to load
     * @return AnnotatedSaga instance with the loaded saga
     */
    protected AnnotatedSaga<T> doLoadSaga(String sagaIdentifier) {
        SagaStore.Entry<T> entry = sagaStore.loadSaga(sagaType, sagaIdentifier);
        if (entry != null) {
            T saga = entry.saga();
            injector.injectResources(saga);
            return new AnnotatedSaga<>(sagaIdentifier, entry.associationValues(), saga, entry.trackingToken(),
                                       sagaModel);
        }
        return null;
    }
}
