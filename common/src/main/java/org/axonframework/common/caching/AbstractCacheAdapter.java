/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.caching;

import org.axonframework.common.Registration;

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
     * Creates an adapter for the given {@code cacheEntryListener}. The adapter must forward all incoming
     * notifications to the respective methods on the {@code cacheEntryListener}.
     *
     * @param cacheEntryListener The listener to create an adapter for
     * @return an adapter that forwards notifications
     */
    protected abstract L createListenerAdapter(EntryListener cacheEntryListener);

    @Override
    public Registration registerCacheEntryListener(EntryListener entryListener) {
        L adapter = createListenerAdapter(entryListener);
        Registration registration
                = registeredAdapters.putIfAbsent(entryListener, adapter) == null ? doRegisterListener(adapter) : null;
        return () -> {
            L removedAdapter = registeredAdapters.remove(entryListener);
            if (removedAdapter != null) {
                if (registration != null) {
                    registration.cancel();
                }
                return true;
            }
            return false;
        };
    }

    /**
     * Registers the given listener with the cache implementation
     *
     * @param listenerAdapter the listener to register
     * @return a handle to deregister the listener
     */
    protected abstract Registration doRegisterListener(L listenerAdapter);
}
