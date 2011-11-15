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

package org.axonframework.domain;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents MetaData that is passed along with a payload in a Message. Typically, the MetaData contains information
 * about the message payload that isn't "domain-specific". Examples are originating IP-address or executing User ID.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaData implements Map<String, Object>, Serializable {

    //    private static final MetaData EMPTY_META_DATA = new MetaData();
    private final Map<String, Object> values = new HashMap<String, Object>();

    /**
     * Returns an empty MetaData instance.
     *
     * @return an empty MetaData instance
     */
    public static MetaData emptyInstance() {
        // TODO (see Issue #220): Once MetaData is immutable, return a singleton.
//        return EMPTY_META_DATA;
        return new MetaData();
    }

    private MetaData() {
    }

    /**
     * Initializes a MetaData instance with the given <code>items</code> as content. Note that the items are copied
     * into the MetaData. Modifications in the Map of items will not reflect is the MetaData, or vice versa.
     * Modifications in the items themselves <em>are</em> reflected in the MetaData.
     *
     * @param items the items to populate the MetaData with
     */
    public MetaData(Map<String, Object> items) {
        values.putAll(items);
    }

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated Future versions will refuse modifying MetaData in an existing Message instance
     */
    @Deprecated
    @Override
    public Object put(String key, Object value) {
        return values.put(key, value);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("Event meta-data is immutable.");
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException("Event meta-data is immutable.");
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("Event meta-data is immutable.");
    }

    @Override
    public boolean containsKey(Object key) {
        return values.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values.containsValue(value);
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(values.keySet());
    }

    @Override
    public Collection<Object> values() {
        return Collections.unmodifiableCollection(values.values());
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return Collections.unmodifiableSet(values.entrySet());
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MetaData that = (MetaData) o;

        if (!values.equals(that.values)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
