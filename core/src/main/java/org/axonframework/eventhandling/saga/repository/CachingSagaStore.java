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

import org.axonframework.common.Assert;
import org.axonframework.common.caching.Cache;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Saga Repository implementation that adds caching behavior to the repository it wraps. Both associations and sagas
 * are cached, making loading them faster. Commits and adds are always delegated to the wrapped repository. Loads are
 * only delegated if the cache does not contain the necessary entries.
 * <p/>
 * Updating associations involves a read and a write, which are performed atomically. Therefore, it is unsafe to add or
 * remove specific associations outside of this instance. Obviously, clearing and evictions are safe.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CachingSagaStore<T> implements SagaStore<T> {

    private final SagaStore<T> delegate;
    // guarded by "associationsCacheLock"
    private final Cache associationsCache;
    private final Cache sagaCache;

    /**
     * Initializes an instance delegating to the given <code>delegate</code>, storing associations in the given
     * <code>associationsCache</code> and Saga instances in the given <code>sagaCache</code>.
     *
     * @param delegate          The repository instance providing access to (persisted) entries
     * @param associationsCache The cache to store association information is
     * @param sagaCache         The cache to store Saga instances in
     */
    public CachingSagaStore(SagaStore<T> delegate, Cache associationsCache, Cache sagaCache) {
        Assert.notNull(delegate, "You must provide a SagaRepository instance to delegate to");
        Assert.notNull(associationsCache, "You must provide a Cache instance to store the association values");
        Assert.notNull(sagaCache, "You must provide a Cache instance to store the sagas");
        this.delegate = delegate;
        this.associationsCache = associationsCache;
        this.sagaCache = sagaCache;
    }

    @Override
    public Set<String> findSagas(Class<? extends T> sagaType, AssociationValue associationValue) {
        final String key = cacheKey(associationValue, sagaType);
        // this is a dirty read, but a cache should be thread safe anyway
        Set<String> associations = associationsCache.get(key);
        if (associations == null) {
            associations = delegate.findSagas(sagaType, associationValue);
            associationsCache.put(key, associations);
        }
        return new HashSet<>(associations);
    }

    @Override
    public <S extends T> Entry<S> loadSaga(Class<S> sagaType, String sagaIdentifier) {
        Entry<S> saga = sagaCache.get(sagaIdentifier);
        if (saga == null) {
            saga = delegate.loadSaga(sagaType, sagaIdentifier);
            sagaCache.put(sagaIdentifier, new CacheEntry<T>(saga));
        }
        return saga;
    }

    @Override
    public void insertSaga(Class<? extends T> sagaType, String sagaIdentifier, T saga, TrackingToken token, Set<AssociationValue> associationValues) {
        delegate.insertSaga(sagaType, sagaIdentifier, saga, token, associationValues);
        sagaCache.put(sagaIdentifier, new CacheEntry<>(saga, token, associationValues));
        addCachedAssociations(associationValues, sagaIdentifier, sagaType);
    }

    @Override
    public void deleteSaga(Class<? extends T> sagaType, String sagaIdentifier, Set<AssociationValue> associationValues) {
        sagaCache.remove(sagaIdentifier);
        associationValues.forEach(av -> removeAssociationValueFromCache(sagaType, sagaIdentifier, av));
        delegate.deleteSaga(sagaType, sagaIdentifier, associationValues);
    }

    private void removeAssociationValueFromCache(Class<?> sagaType, String sagaIdentifier, AssociationValue associationValue) {
        String key = cacheKey(associationValue, sagaType);
        Set<String> associations = associationsCache.get(key);
        if (associations != null && associations.remove(sagaIdentifier)) {
            associationsCache.put(key, associations);
        }
    }

    protected void addCachedAssociations(Iterable<AssociationValue> associationValues,
                                         String sagaIdentifier, Class<?> sagaType) {
        for (AssociationValue associationValue : associationValues) {
            String key = cacheKey(associationValue, sagaType);
            Set<String> identifiers = associationsCache.get(key);
            if (identifiers != null && identifiers.add(sagaIdentifier)) {
                associationsCache.put(key, identifiers);
            }
        }
    }

    @Override
    public void updateSaga(Class<? extends T> sagaType, String sagaIdentifier, T saga,
                           TrackingToken token, AssociationValues associationValues) {
        sagaCache.put(sagaIdentifier, new CacheEntry<>(saga, token, associationValues.asSet()));
        delegate.updateSaga(sagaType, sagaIdentifier, saga, token, associationValues);
        associationValues.removedAssociations().forEach(av -> removeAssociationValueFromCache(sagaType, sagaIdentifier, av));
        addCachedAssociations(associationValues.addedAssociations(), sagaIdentifier, sagaType);
    }

    private String cacheKey(AssociationValue associationValue, Class<?> sagaType) {
        return sagaType.getName() + "/" + associationValue.getKey() + "=" + associationValue.getValue();
    }

    private static class CacheEntry<T> implements Entry<T>, Serializable {

        private final T saga;
        private final TrackingToken trackingToken;
        private final Set<AssociationValue> associationValues;

        public CacheEntry(T saga, TrackingToken TrackingToken, Set<AssociationValue> associationValues) {
            this.saga = saga;
            this.trackingToken = TrackingToken;
            this.associationValues = associationValues;
        }

        public <S extends T> CacheEntry(Entry<S> other) {
            this.saga = other.saga();
            this.trackingToken = other.trackingToken();
            this.associationValues = other.associationValues();
        }

        @Override
        public TrackingToken trackingToken() {
            return trackingToken;
        }

        @Override
        public Set<AssociationValue> associationValues() {
            return associationValues;
        }

        @Override
        public T saga() {
            return saga;
        }
    }
}
