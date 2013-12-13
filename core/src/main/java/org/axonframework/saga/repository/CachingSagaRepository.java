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

package org.axonframework.saga.repository;

import org.axonframework.common.Assert;
import org.axonframework.common.lock.IdentifierBasedLock;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;

import java.util.HashSet;
import java.util.Set;
import javax.cache.Cache;

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
public class CachingSagaRepository implements SagaRepository {

    private final SagaRepository delegate;
    private final IdentifierBasedLock associationsCacheLock = new IdentifierBasedLock();
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
    public CachingSagaRepository(SagaRepository delegate, Cache associationsCache, Cache sagaCache) {
        Assert.notNull(delegate, "You must provide a SagaRepository instance to delegate to");
        Assert.notNull(associationsCache, "You must provide a Cache instance to store the association values");
        Assert.notNull(sagaCache, "You must provide a Cache instance to store the sagas");
        this.delegate = delegate;
        this.associationsCache = associationsCache;
        this.sagaCache = sagaCache;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> find(Class<? extends Saga> type, AssociationValue associationValue) {
        final String key = cacheKey(associationValue, type.getName());
        // this is a dirty read, but a cache should be thread safe anyway
        Set<String> associations = (Set<String>) associationsCache.get(key);
        if (associations == null) {
            associations = feedCache(type, associationValue, key);
        }

        return new HashSet<String>(associations);
    }

    @SuppressWarnings("unchecked")
    private Set<String> feedCache(Class<? extends Saga> type, AssociationValue associationValue, String key) {
        associationsCacheLock.obtainLock(key);
        try {
            Set<String> associations = (Set<String>) associationsCache.get(key);
            if (associations == null) {
                associations = delegate.find(type, associationValue);
                associationsCache.put(key, associations);
            }
            return associations;
        } finally {
            associationsCacheLock.releaseLock(key);
        }
    }

    @Override
    public Saga load(String sagaIdentifier) {
        Saga saga = (Saga) sagaCache.get(sagaIdentifier);
        if (saga == null) {
            saga = delegate.load(sagaIdentifier);
            sagaCache.put(sagaIdentifier, saga);
        }
        return saga;
    }

    @Override
    public void commit(Saga saga) {
        final String sagaIdentifier = saga.getSagaIdentifier();
        sagaCache.put(sagaIdentifier, saga);
        if (saga.isActive()) {
            updateAssociations(saga, sagaIdentifier);
        } else {
            removeCachedAssociations(saga.getAssociationValues(), sagaIdentifier, saga.getClass().getName());
        }
        delegate.commit(saga);
    }

    @Override
    public void add(Saga saga) {
        final String sagaIdentifier = saga.getSagaIdentifier();
        sagaCache.put(sagaIdentifier, saga);
        updateAssociations(saga, sagaIdentifier);
        delegate.add(saga);
    }

    private void updateAssociations(Saga saga, String sagaIdentifier) {
        final String sagaType = saga.getClass().getName();
        addCachedAssociations(saga.getAssociationValues().addedAssociations(), sagaIdentifier, sagaType);
        removeCachedAssociations(saga.getAssociationValues().removedAssociations(), sagaIdentifier, sagaType);
    }

    @SuppressWarnings("unchecked")
    private void addCachedAssociations(Iterable<AssociationValue> associationValues,
                                       String sagaIdentifier, String sagaType) {
        for (AssociationValue associationValue : associationValues) {
            String key = cacheKey(associationValue, sagaType);
            associationsCacheLock.obtainLock(key);
            try {
                Set<String> identifiers = (Set<String>) associationsCache.get(key);
                if (identifiers != null && identifiers.add(sagaIdentifier)) {
                    associationsCache.put(key, identifiers);
                }
            } finally {
                associationsCacheLock.releaseLock(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void removeCachedAssociations(Iterable<AssociationValue> associationValues,
                                          String sagaIdentifier, String sagaType) {
        for (AssociationValue associationValue : associationValues) {
            String key = cacheKey(associationValue, sagaType);
            associationsCacheLock.obtainLock(key);
            try {
                Set<String> identifiers = (Set<String>) associationsCache.get(key);
                if (identifiers != null && identifiers.remove(sagaIdentifier)) {
                    associationsCache.put(key, identifiers);
                }
            } finally {
                associationsCacheLock.releaseLock(key);
            }
        }
    }

    private String cacheKey(AssociationValue associationValue, String sagaType) {
        return sagaType + "/" + associationValue.getKey() + "=" + associationValue.getValue();
    }
}
