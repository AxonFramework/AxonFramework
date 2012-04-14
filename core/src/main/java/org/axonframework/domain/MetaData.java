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

    private static final long serialVersionUID = -7892913866303912970L;
    private static final MetaData EMPTY_META_DATA = new MetaData();
    private static final String UNSUPPORTED_MUTATION_MSG = "Event meta-data is immutable.";

    private final Map<String, Object> values;

    /**
     * Returns an empty MetaData instance.
     *
     * @return an empty MetaData instance
     */
    public static MetaData emptyInstance() {
        return EMPTY_META_DATA;
    }

    private MetaData() {
        values = Collections.emptyMap();
    }

    /**
     * Initializes a MetaData instance with the given <code>items</code> as content. Note that the items are copied
     * into the MetaData. Modifications in the Map of items will not reflect is the MetaData, or vice versa.
     * Modifications in the items themselves <em>are</em> reflected in the MetaData.
     *
     * @param items the items to populate the MetaData with
     */
    public MetaData(Map<String, ?> items) {
        values = Collections.unmodifiableMap(new HashMap<String, Object>(items));
    }

    /**
     * Creates a new MetaData instance from the given <code>metaDataEntries</code>. If <code>metaDataEntries</code> is
     * already a MetaData instance, it is returned as is. This makes this method more suitable than the {@link
     * #MetaData(java.util.Map)} copy-constructor.
     *
     * @param metaDataEntries the items to populate the MetaData with
     * @return a MetaData instance with the given <code>metaDataEntries</code> as content
     */
    public static MetaData from(Map<String, ?> metaDataEntries) {
        if (metaDataEntries instanceof MetaData) {
            return (MetaData) metaDataEntries;
        } else if (metaDataEntries == null || metaDataEntries.isEmpty()) {
            return MetaData.emptyInstance();
        }
        return new MetaData(metaDataEntries);
    }

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
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
        return values.keySet();
    }

    @Override
    public Collection<Object> values() {
        return values.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return values.entrySet();
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
        if (!(o instanceof Map)) {
            return false;
        }

        Map that = (Map) o;

        return values.equals(that);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * Returns a MetaData instance containing values of <code>this</code>, combined with the given
     * <code>additionalEntries</code>. If any entries have identical keys, the values from the
     * <code>additionalEntries</code> will take precedence.
     *
     * @param additionalEntries The additional entries for the new MetaData
     * @return a MetaData instance containing values of <code>this</code>, combined with the given
     *         <code>additionalEntries</code>
     */
    public MetaData mergedWith(Map<String, Object> additionalEntries) {
        if (additionalEntries.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new HashMap<String, Object>(values);
        merged.putAll(additionalEntries);
        return new MetaData(merged);
    }

    /**
     * Returns a MetaData instance with the items with given <code>keys</code> removed. Keys for which there is no
     * assigned value are ignored.<br/>
     * This MetaData instance is not influenced by this operation.
     *
     * @param keys The keys of the entries to remove
     * @return a MetaData instance without the given <code>keys</code>
     */
    public MetaData withoutKeys(Set<String> keys) {
        if (keys.isEmpty()) {
            return this;
        }
        Map<String, ?> modified = new HashMap<String, Object>(values);
        for (String key : keys) {
            modified.remove(key);
        }
        return new MetaData(modified);
    }

    /**
     * Java Serialization specification method that will ensure that deserialization will maintain a single instance of
     * empty MetaData.
     *
     * @return the MetaData instance to use after deserialization
     */
    protected Object readResolve() {
        if (isEmpty()) {
            return MetaData.emptyInstance();
        }
        return this;
    }
}
