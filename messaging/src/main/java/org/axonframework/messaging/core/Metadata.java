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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

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

/**
 * Represents metadata that is passed along with a payload in a {@link Message}.
 * <p>
 * Typically, the metadata contains information about the message payload that isn't "domain-specific." Examples
 * are originating IP-address or executing User ID.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class Metadata implements Map<String, String> {

    private static final Metadata EMPTY_METADATA = new Metadata();
    private static final String UNSUPPORTED_MUTATION_MSG = "Metadata is immutable.";

    private final Map<String, String> values;

    private Metadata() {
        values = Collections.emptyMap();
    }

    /**
     * Initializes a {@code Metadata} instance with the given {@code items} as content.
     * <p>
     * Note that the items are copied into the {@code Metadata}. Modifications in the {@code Map} of items will not
     * reflect is the {@code Metadata}, or vice versa. Modifications in the items themselves <em>are</em> reflected in
     * the {@code Metadata}.
     *
     * @param items The items to populate the {@code Metadata} with.
     */
    public Metadata(@Nonnull Map<String, String> items) {
        values = Collections.unmodifiableMap(new HashMap<>(items));
    }

    /**
     * Returns an empty {@code Metadata} instance.
     *
     * @return An empty {@code Metadata} instance.
     */
    public static Metadata emptyInstance() {
        return EMPTY_METADATA;
    }

    /**
     * Creates a new {@code Metadata} instance from the given {@code metadataEntries}.
     * <p>
     * If {@code metadataEntries} is already a {@code Metadata} instance, it is returned as is. This makes this method
     * more suitable than the {@link #Metadata(java.util.Map)} copy-constructor.
     *
     * @param metadataEntries The entries to populate the {@code Metadata} with.
     * @return A {@code Metadata}Data instance with the given {@code MetadataEntries} as content.
     */
    public static Metadata from(@Nullable Map<String, String> metadataEntries) {
        if (metadataEntries instanceof Metadata) {
            return (Metadata) metadataEntries;
        } else if (metadataEntries == null || metadataEntries.isEmpty()) {
            return Metadata.emptyInstance();
        }
        return new Metadata(metadataEntries);
    }

    /**
     * Creates a {@code Metadata} instance with a single entry from the given {@code key} and given {@code value}.
     *
     * @param key   The key for the entry.
     * @param value The value of the entry.
     * @return A {@code Metadata} instance with a single entry.
     */
    public static Metadata with(@Nonnull String key, @Nullable String value) {
        return Metadata.from(Collections.singletonMap(key, value));
    }

    /**
     * Returns a {@code Metadata} instance containing the current entries, <b>and</b> the given {@code key} and given
     * {@code value}.
     * <p>
     * If {@code key} already existed, it's old {@code value} is overwritten with the given {@code value}.
     *
     * @param key   The key for the entry.
     * @param value The value of the entry.
     * @return A {@code Metadata} instance with an additional entry.
     */
    public Metadata and(@Nonnull String key, @Nullable String value) {
        HashMap<String, String> newValues = new HashMap<>(values);
        newValues.put(key, value);
        return new Metadata(newValues);
    }

    /**
     * Returns a {@code Metadata} instance containing the current entries, <b>and</b> the given {@code key} if it was
     * not yet present in this {@code Metadata}.
     * <p>
     * If the given {@code key} already existed, the current value will be used. Otherwise, the {@code value}
     * {@link Supplier} function will provide the value for {@code key}.
     *
     * @param key   The key for the entry.
     * @param value A {@code Supplier} function which provides the value.
     * @return A {@code Metadata} instance with an additional entry.
     */
    public Metadata andIfNotPresent(@Nonnull String key, @Nonnull Supplier<String> value) {
        return containsKey(key) ? this : this.and(key, value.get());
    }

    /**
     * Returns a {@code Metadata} instance containing values of {@code this}, combined with the given
     * {@code additionalEntries}.
     * <p>
     * If any entries have identical keys, the values from the {@code additionalEntries} will take precedence.
     *
     * @param additionalEntries The additional entries for the new {@code Metadata}.
     * @return A {@code Metadata} instance containing values of {@code this}, combined with the given
     * {@code additionalEntries}.
     */
    public Metadata mergedWith(@Nonnull Map<String, String> additionalEntries) {
        if (additionalEntries.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return Metadata.from(additionalEntries);
        }
        Map<String, String> merged = new HashMap<>(values);
        merged.putAll(additionalEntries);
        return new Metadata(merged);
    }

    /**
     * Returns a {@code Metadata} instance for which the entries with given {@code keys} are removed.
     * <p>
     * Keys for which there is no assigned value are ignored. This {@code Metadata} instance is not influenced by this
     * operation.
     *
     * @param keys The keys of the entries to remove.
     * @return A {@code Metadata} instance without the given {@code keys}.
     */
    public Metadata withoutKeys(@Nonnull Set<String> keys) {
        if (keys.isEmpty()) {
            return this;
        }
        Map<String, String> modified = new HashMap<>(values);
        keys.forEach(modified::remove);
        return new Metadata(modified);
    }

    /**
     * Returns a {@code Metadata} instance containing a subset of the {@code keys} in this instance.
     * <p>
     * Keys for which there is no assigned value are ignored. This {@code Metadata} instance is not influenced by this
     * operation.
     *
     * @param keys The keys of the entries to remove.
     * @return A {@code Metadata} instance containing the given {@code keys} if these were already present.
     */
    public Metadata subset(String... keys) {
        return Metadata.from(Stream.of(keys)
                                   .filter(this::containsKey)
                                   .collect(new MetadataCollector(this::get)));
    }

    @Override
    @Nullable
    public String get(Object key) {
        return values.get(key);
    }

    /**
     * <strong>This operation is not supported since {@code Metadata} is an immutable object.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported since {@code Metadata} is an immutable object.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported since {@code Metadata} is an immutable object.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void putAll(@Nonnull Map<? extends String, ? extends String> m) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported since {@code Metadata} is an immutable object.</strong>
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
    public Collection<String> values() {
        return values.values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
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
    private record MetadataCollector(Function<String, String> valueProvider)
            implements Collector<String, Map<String, String>, Metadata> {

        @Override
        public Supplier<Map<String, String>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<String, String>, String> accumulator() {
            return (map, key) -> map.put(key, valueProvider.apply(key));
        }

        @Override
        public BinaryOperator<Map<String, String>> combiner() {
            return (m1, m2) -> {
                Map<String, String> result = new HashMap<>(m1);
                result.putAll(m2);
                return result;
            };
        }

        @Override
        public Function<Map<String, String>, Metadata> finisher() {
            return Metadata::from;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }
}
