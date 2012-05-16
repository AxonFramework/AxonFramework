/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.common;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheEntry;
import net.sf.jsr107cache.CacheException;
import net.sf.jsr107cache.CacheListener;
import net.sf.jsr107cache.CacheStatistics;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Cache implementation that does absolutely nothing. Objects aren't cached, making it a special case implementation for
 * the case when caching is disabled.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class NoCache implements Cache {

    /**
     * Creates a singleton reference the the NoCache implementation.
     */
    public static final NoCache INSTANCE = new NoCache();

    private NoCache() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set entrySet() {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set keySet() {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map t) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection values() {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(Object key) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map getAll(Collection keys) throws CacheException {
        return Collections.emptyMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(Object key) throws CacheException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadAll(Collection keys) throws CacheException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object peek(Object key) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object put(Object key, Object value) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheEntry getCacheEntry(Object key) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheStatistics getCacheStatistics() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object remove(Object key) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void evict() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(CacheListener listener) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeListener(CacheListener listener) {
    }
}
