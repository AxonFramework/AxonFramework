/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents MetaData that is passed along with a payload in a Message. Typically, the MetaData contains information
 * about the message payload that isn't "domain-specific". Examples are originating IP-address or executing User ID.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaData implements Map<String, Object>, Serializable {

    private static final MetaData EMPTY_META_DATA = new MetaData();
    private static final String UNSUPPORTED_MUTATION_MSG = "Metadata is immutable.";

    private final Map<String, Object> values;

    private MetaData() {
        values = Collections.emptyMap();
    }

    /**
     * Initializes a MetaData instance with the given {@code items} as content. Note that the items are copied into the
     * MetaData. Modifications in the Map of items will not reflect is the MetaData, or vice versa. Modifications in the
     * items themselves <em>are</em> reflected in the MetaData.
     *
     * @param items the items to populate the MetaData with
     */
    public MetaData(@Nonnull Map<String, ?> items) {
        values = Collections.unmodifiableMap(new HashMap<>(items));
    }

    /**
     * Returns an empty MetaData instance.
     *
     * @return an empty MetaData instance
     */
    public static MetaData emptyInstance() {
        return EMPTY_META_DATA;
    }

    /**
     * Creates a new MetaData instance from the given {@code metaDataEntries}. If {@code metaDataEntries} is already a
     * MetaData instance, it is returned as is. This makes this method more suitable than the {@link
     * #MetaData(java.util.Map)} copy-constructor.
     *
     * @param metaDataEntries the items to populate the MetaData with
     * @return a MetaData instance with the given {@code metaDataEntries} as content
     */
    public static MetaData from(@Nullable Map<String, ?> metaDataEntries) {
        if (metaDataEntries instanceof MetaData) {
            return (MetaData) metaDataEntries;
        } else if (metaDataEntries == null || metaDataEntries.isEmpty()) {
            return MetaData.emptyInstance();
        }
        return new MetaData(metaDataEntries);
    }

    /**
     * Creates a MetaData instances with a single entry, with the given {@code key} and given {@code value}.
     *
     * @param key   The key for the entry
     * @param value The value of the entry
     * @return a MetaData instance with a single entry
     */
    public static MetaData with(@Nonnull String key, @Nullable Object value) {
        return MetaData.from(Collections.singletonMap(key, value));
    }

    /**
     * Returns a MetaData instances containing the current entries, <b>and</b> the given {@code key} and given
     * {@code value}.
     * If {@code key} already existed, it's old {@code value} is overwritten with the given
     * {@code value}.
     *
     * @param key   The key for the entry
     * @param value The value of the entry
     * @return a MetaData instance with an additional entry
     */
    public MetaData and(@Nonnull String key, @Nullable Object value) {
        HashMap<String, Object> newValues = new HashMap<>(values);
        newValues.put(key, value);
        return new MetaData(newValues);
    }

    /**
     * Returns a MetaData instances containing the current entries, <b>and</b> the given {@code key} if it was
     * not yet present in this MetaData.
     * If {@code key} already existed, the current value will be used.
     * Otherwise the Supplier function will provide the {@code value} for {@code key}
     *
     * @param key   The key for the entry
     * @param value A Supplier function which provides the value
     * @return a MetaData instance with an additional entry
     */
    public MetaData andIfNotPresent(@Nonnull String key, @Nonnull Supplier<Object> value) {
        return containsKey(key) ? this : this.and(key, value.get());
    }

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void putAll(@Nonnull Map<? extends String, ?> m) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported.</strong>
     * <p>
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
     * Returns a MetaData instance containing values of {@code this}, combined with the given
     * {@code additionalEntries}. If any entries have identical keys, the values from the
     * {@code additionalEntries} will take precedence.
     *
     * @param additionalEntries The additional entries for the new MetaData
     * @return a MetaData instance containing values of {@code this}, combined with the given
     * {@code additionalEntries}
     */
    public MetaData mergedWith(@Nonnull Map<String, ?> additionalEntries) {
        if (additionalEntries.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return MetaData.from(additionalEntries);
        }
        Map<String, Object> merged = new HashMap<>(values);
        merged.putAll(additionalEntries);
        return new MetaData(merged);
    }

    /**
     * Returns a MetaData instance with the items with given {@code keys} removed. Keys for which there is no
     * assigned value are ignored.<br/>
     * This MetaData instance is not influenced by this operation.
     *
     * @param keys The keys of the entries to remove
     * @return a MetaData instance without the given {@code keys}
     */
    public MetaData withoutKeys(@Nonnull Set<String> keys) {
        if (keys.isEmpty()) {
            return this;
        }
        Map<String, ?> modified = new HashMap<>(values);
        keys.forEach(modified::remove);
        return new MetaData(modified);
    }

    /**
     * Returns a MetaData instance containing a subset of the {@code keys} in this instance.
     * Keys for which there is no assigned value are ignored.<
     *
     * @param keys The keys of the entries to remove
     * @return a MetaData instance containing the given {@code keys} if these were already present
     */
    public MetaData subset(String... keys) {
        return MetaData.from(Stream.of(keys).filter(this::containsKey).collect(new MetaDataCollector(this::get)));
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        values.forEach((k, v) -> sb.append(", '")
                                   .append(k)
                                   .append("'->'")
                                   .append(v)
                                   .append('\''));
        int skipInitialListingAppendString = 2;
        // Only skip if the StringBuilder actual has a field, as otherwise we'll receive an IndexOutOfBoundsException
        return values.isEmpty() ? sb.toString() : sb.substring(skipInitialListingAppendString);
    }

    /**
     * Collector implementation that, unlike {@link java.util.stream.Collectors#toMap(Function, Function)} allows
     * {@code null} values.
     */
    private static class MetaDataCollector implements Collector<String, Map<String, Object>, MetaData> {

        private final Function<String, Object> valueProvider;

        private MetaDataCollector(Function<String, Object> valueProvider) {
            this.valueProvider = valueProvider;
        }

        @Override
        public Supplier<Map<String, Object>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<String, Object>, String> accumulator() {
            return (map, key) -> map.put(key, valueProvider.apply(key));
        }

        @Override
        public BinaryOperator<Map<String, Object>> combiner() {
            return (m1, m2) -> {
                Map<String, Object> result = new HashMap<>(m1);
                result.putAll(m2);
                return result;
            };
        }

        @Override
        public Function<Map<String, Object>, MetaData> finisher() {
            return MetaData::from;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }
}
