/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository;

import org.axonframework.saga.Saga;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Wrapper around a map of String to Saga that keeps Weak References to the saga instances. As long as any references to
 * a Saga exist, this SagaCache will return that instance when it is looked up using its identifier.
 * <p/>
 * When all references to a Saga are cleared, the garbage collector <em>may</em> clear any unreferenced saga instances
 * from this Cache.
 * <p/>
 * When a reference from this Cache is removed, the entry itself may still survive in the cache (albeit empty). To
 * remove any empty entries, use the {@link #purge()} method. Empty entries are also cleared when accessed (cache
 * misses).
 * <p/>
 * Note that the primary purpose of this cache is <em>not</em> to improve performance, but to prevent multiple instances
 * of the same conceptual saga (i.e. having the same identifier) from being active in the JVM.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SagaCache {

    private ConcurrentMap<String, Reference<Saga>> backingCache;

    /**
     * Initializes an empty cache.
     */
    public SagaCache() {
        backingCache = new ConcurrentHashMap<String, Reference<Saga>>();
    }

    /**
     * Retrieves the Saga instance with the given <code>sagaIdentifier</code>, or <code>null</code> if none was found.
     *
     * @param sagaIdentifier The identifier of the saga to return
     * @return the Saga with given identifier, or <code>null</code> if none was found.
     */
    public Saga get(String sagaIdentifier) {
        Reference<Saga> reference = backingCache.get(sagaIdentifier);
        return getOrPurge(sagaIdentifier, reference);
    }

    /**
     * Puts the given <code>saga</code> in this cache, if no saga with the same identifier already exists. The return
     * value provides a reference to the cached saga instance. This may either be the same as <code>saga</code> (in case
     * the given saga was successfully stored in the cache), or another instance.
     * <p/>
     * Callers of <code>put</code> should <em>always</em> use the returned reference for further processing and regard
     * the given <code>saga</code> as an unwanted duplicate.
     *
     * @param saga The saga instance to store in the cache
     * @return The cached instance of the saga
     */
    public Saga put(Saga saga) {
        backingCache.putIfAbsent(saga.getSagaIdentifier(), new WeakReference<Saga>(saga));
        Saga cachedSaga = get(saga.getSagaIdentifier());
        while (cachedSaga == null) {
            cachedSaga = put(saga);
        }
        return cachedSaga;
    }

    /**
     * Clears any entries whose saga instances have been cleaned up by the garbage collector. Purged entries will no
     * longer count against the {@link #size()} of the cache.
     */
    public void purge() {
        for (Map.Entry<String, Reference<Saga>> entry : backingCache.entrySet()) {
            Reference<Saga> value = entry.getValue();
            if (value == null || value.get() == null) {
                backingCache.remove(entry.getKey(), value);
            }
        }
    }

    /**
     * Returns an approximation of the number of items in the cache. The returned count includes empty entries (i.e.
     * entries pointing to sagas that have been garbage collected)
     *
     * @return the number of entries in the cache
     *
     * @see #purge()
     */
    public int size() {
        return backingCache.size();
    }

    /**
     * Indicates whether or not this cache is empty. The cache is considered not empty even if it only contains entries
     * to sagas that have been garbage collected.
     *
     * @return <code>true</code> if the cache is completely empty, <code>false</code> otherwise.
     *
     * @see #purge()
     */
    public boolean isEmpty() {
        return backingCache.isEmpty();
    }

    private Saga getOrPurge(String sagaIdentifier, Reference<Saga> reference) {
        if (reference == null) {
            return null;
        }
        Saga value = reference.get();
        if (value == null) {
            backingCache.remove(sagaIdentifier, reference);
        }
        return value;
    }
}
