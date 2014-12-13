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

package org.axonframework.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Abstract implementation of the Cache interface which makes it easier to implement Adapters.
 *
 * @param <L> The type of event listener the cache uses
 * @author Allard Buijze
 * @since 2.1.2
 */
public abstract class AbstractCacheAdapter<L> implements Cache {

    private final ConcurrentMap<EntryListener, L> registeredAdapters =
            new ConcurrentHashMap<>();

    /**
     * Creates an adapter for the given <code>cacheEntryListener</code>. The adapter must forward all incoming
     * notifications to the respective methods on the <code>cacheEntryListener</code>.
     *
     * @param cacheEntryListener The listener to create an adapter for
     * @return an adapter that forwards notifications
     */
    protected abstract L createListenerAdapter(EntryListener cacheEntryListener);

    @Override
    public void registerCacheEntryListener(EntryListener entryListener) {
        final L adapter = createListenerAdapter(entryListener);
        if (registeredAdapters.putIfAbsent(entryListener, adapter) == null) {
            doRegisterListener(adapter);
        }
    }

    @Override
    public void unregisterCacheEntryListener(EntryListener entryListener) {
        L adapter = registeredAdapters.remove(entryListener);
        if (adapter != null) {
            doUnregisterListener(adapter);
        }
    }

    /**
     * Unregisters the given <code>listener</code> with the cache
     *
     * @param listenerAdapter The listener to register with the cache
     */
    protected abstract void doUnregisterListener(L listenerAdapter);

    /**
     * Registers the given listener with the cache implementation
     *
     * @param listenerAdapter the listener to register
     */
    protected abstract void doRegisterListener(L listenerAdapter);
}
