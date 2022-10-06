/*
 * Copyright (c) 2010-2022. Axon Framework
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

import java.util.function.UnaryOperator;

/**
 * Cache implementation that does absolutely nothing. Objects aren't cached, making it a special case implementation for
 * the case when caching is disabled.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public final class NoCache implements Cache {

    /**
     * Creates a singleton reference the NoCache implementation.
     */
    public static final NoCache INSTANCE = new NoCache();

    private NoCache() {
    }

    @Override
    public <K, V> V get(K key) {
        return null;
    }

    @Override
    public void put(Object key, Object value) {
    }

    @Override
    public boolean putIfAbsent(Object key, Object value) {
        return true;
    }

    @Override
    public boolean remove(Object key) {
        return false;
    }

    @Override
    public void removeAll() {
        // Do nothing
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public Registration registerCacheEntryListener(EntryListener cacheEntryListener) {
        return () -> true;
    }

    @Override
    public <V> void computeIfPresent(Object key, UnaryOperator<V> update) {
        // Do nothing
    }
}
