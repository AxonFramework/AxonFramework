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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.Converter;

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
 * Represents {@code MetaData} that is passed along with a payload in a {@link Message}.
 * <p>
 * Typically, the {@code MetaData} contains information about the message payload that isn't "domain-specific." Examples
 * are originating IP-address or executing User ID.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class MetaData implements Map<String, String> {

    private static final MetaData EMPTY_META_DATA = new MetaData();
    private static final String UNSUPPORTED_MUTATION_MSG = "MetaData is immutable.";

    private final Map<String, String> values;

    private MetaData() {
        values = Collections.emptyMap();
    }

    /**
     * Initializes a {@code MetaData} instance with the given {@code items} as content.
     * <p>
     * Note that the items are copied into the {@code MetaData}. Modifications in the {@code Map} of items will not
     * reflect is the {@code MetaData}, or vice versa. Modifications in the items themselves <em>are</em> reflected in
     * the {@code MetaData}.
     *
     * @param items The items to populate the {@code MetaData} with.
     */
    public MetaData(@Nonnull Map<String, String> items) {
        values = Collections.unmodifiableMap(new HashMap<>(items));
    }

    /**
     * Returns an empty {@code MetaData} instance.
     *
     * @return An empty {@code MetaData} instance.
     */
    public static MetaData emptyInstance() {
        return EMPTY_META_DATA;
    }

    /**
     * Creates a new {@code MetaData} instance from the given {@code metaDataEntries}.
     * <p>
     * If {@code metaDataEntries} is already a {@code MetaData} instance, it is returned as is. This makes this method
     * more suitable than the {@link #MetaData(java.util.Map)} copy-constructor.
     *
     * @param metaDataEntries The entries to populate the {@code MetaData} with.
     * @return A {@code MetaData}Data instance with the given {@code metaDataEntries} as content.
     */
    public static MetaData from(@Nullable Map<String, String> metaDataEntries) {
        if (metaDataEntries instanceof MetaData) {
            return (MetaData) metaDataEntries;
        } else if (metaDataEntries == null || metaDataEntries.isEmpty()) {
            return MetaData.emptyInstance();
        }
        return new MetaData(metaDataEntries);
    }

    /**
     * Creates a {@code MetaData} instances with a single entry from the given {@code key} and given {@code value}.
     *
     * @param key   The key for the entry.
     * @param value The value of the entry.
     * @return A {@code MetaData} instance with a single entry.
     */
    public static MetaData with(@Nonnull String key, @Nullable String value) {
        return MetaData.from(Collections.singletonMap(key, value));
    }

    /**
     * Creates a {@code MetaData} instances with a single entry from the given {@code key} and given {@code value}.
     * <p>
     * The {@code value} is converted into a {@link String} by the given {@code converter} <em>if</em> it isn't a
     * {@code String} already.
     *
     * @param key       The key for the entry.
     * @param value     The value of the entry.
     * @param converter The converter used to convert the given {@code value} into a {@link String}.
     * @return A {@code MetaData} instance with a single entry.
     */
    public static MetaData with(@Nonnull String key, @Nullable Object value, @Nonnull Converter converter) {
        return with(key, convertToString(value, converter));
    }

    /**
     * Returns a {@code MetaData} instances containing the current entries, <b>and</b> the given {@code key} and given
     * {@code value}.
     * <p>
     * If {@code key} already existed, it's old {@code value} is overwritten with the given {@code value}.
     *
     * @param key   The key for the entry.
     * @param value The value of the entry.
     * @return A {@code MetaData} instance with an additional entry.
     */
    public MetaData and(@Nonnull String key, @Nullable String value) {
        HashMap<String, String> newValues = new HashMap<>(values);
        newValues.put(key, value);
        return new MetaData(newValues);
    }

    /**
     * Returns a {@code MetaData} instances containing the current entries, <b>and</b> the given {@code key} and given
     * {@code value}.
     * <p>
     * The {@code value} is converted into a {@link String} by the given {@code converter} <em>if</em> it isn't a
     * {@code String} already.
     * <p>
     * If {@code key} already existed, it's old {@code value} is overwritten with the given {@code value}.
     *
     * @param key       The key for the entry.
     * @param value     The value of the entry.
     * @param converter The converter used to convert the given {@code value} into a {@link String}.
     * @return A {@code MetaData} instance with an additional entry.
     */
    public MetaData and(@Nonnull String key, @Nullable Object value, @Nonnull Converter converter) {
        HashMap<String, String> newValues = new HashMap<>(values);
        newValues.put(key, convertToString(value, converter));
        return new MetaData(newValues);
    }

    /**
     * Returns a {@code MetaData} instances containing the current entries, <b>and</b> the given {@code key} if it was
     * not yet present in this {@code MetaData}.
     * <p>
     * If the given {@code key} already existed, the current value will be used. Otherwise, the {@code value}
     * {@link Supplier} function will provide the value for {@code key}.
     *
     * @param key   The key for the entry.
     * @param value A {@code Supplier} function which provides the value.
     * @return A {@code MetaData} instance with an additional entry.
     */
    public MetaData andIfNotPresent(@Nonnull String key, @Nonnull Supplier<String> value) {
        return containsKey(key) ? this : this.and(key, value.get());
    }

    /**
     * Returns a {@code MetaData} instances containing the current entries, <b>and</b> the given {@code key} if it was
     * not yet present in this {@code MetaData}.
     * <p>
     * If the given {@code key} already existed, the current value will be used. Otherwise, the {@code value}
     * {@link Supplier} function will provide the value for {@code key}.
     * <p>
     * The result from the {@code value Supplier} is converted into a {@link String} by the given {@code converter}
     * <em>if</em> it isn't a {@code String} already.
     *
     * @param key       The key for the entry.
     * @param value     A {@code Supplier} function which provides the value.
     * @param converter The converter used to convert the outcome of the given {@code value} Supplier into a
     *                  {@link String}.
     * @return A {@code MetaData} instance with an additional entry.
     */
    public MetaData andIfNotPresent(@Nonnull String key,
                                    @Nonnull Supplier<Object> value,
                                    @Nonnull Converter converter) {
        return containsKey(key) ? this : this.and(key, convertToString(value.get(), converter));
    }

    private static String convertToString(Object value, Converter converter) {
        return value instanceof String ? (String) value : converter.convert(value, String.class);
    }

    /**
     * Returns a {@code MetaData} instance containing values of {@code this}, combined with the given
     * {@code additionalEntries}.
     * <p>
     * If any entries have identical keys, the values from the {@code additionalEntries} will take precedence.
     *
     * @param additionalEntries The additional entries for the new {@code MetaData}.
     * @return A {@code MetaData} instance containing values of {@code this}, combined with the given
     * {@code additionalEntries}.
     */
    public MetaData mergedWith(@Nonnull Map<String, String> additionalEntries) {
        if (additionalEntries.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return MetaData.from(additionalEntries);
        }
        Map<String, String> merged = new HashMap<>(values);
        merged.putAll(additionalEntries);
        return new MetaData(merged);
    }

    /**
     * Returns a {@code MetaData} instance for which the entries with given {@code keys} are removed.
     * <p>
     * Keys for which there is no assigned value are ignored. This MetaData instance is not influenced by this
     * operation.
     *
     * @param keys The keys of the entries to remove.
     * @return A {@code MetaData} instance without the given {@code keys}.
     */
    public MetaData withoutKeys(@Nonnull Set<String> keys) {
        if (keys.isEmpty()) {
            return this;
        }
        Map<String, String> modified = new HashMap<>(values);
        keys.forEach(modified::remove);
        return new MetaData(modified);
    }

    /**
     * Returns a {@code MetaData} instance containing a subset of the {@code keys} in this instance.
     * <p>
     * Keys for which there is no assigned value are ignored. This MetaData instance is not influenced by this
     * operation.
     *
     * @param keys The keys of the entries to remove.
     * @return A {@code MetaData} instance containing the given {@code keys} if these were already present.
     */
    public MetaData subset(String... keys) {
        return MetaData.from(Stream.of(keys)
                                   .filter(this::containsKey)
                                   .collect(new MetaDataCollector(this::get)));
    }

    @Override
    public String get(Object key) {
        return values.get(key);
    }

    /**
     * Returns the value stored under the given {@code key}, converting it to the given {@code valueType} with the given
     * {@code converter}.
     * <p>
     * The {@code converter} is not invoked when there is no value present for the given {@code key}.
     *
     * @param key       The key for which to retrieve a value.
     * @param valueType The desired type of the value to return, used during conversion.
     * @param converter The {@code Converter} used to convert the recovered value to the given {@code valueType}.
     * @param <R>       The desired value type to return.
     * @return The value present for the given {@code key}, converted to {@code R} by the given {@code converter}.
     */
    @Nullable
    public <R> R get(@Nonnull String key, @Nonnull Class<R> valueType, @Nonnull Converter converter) {
        String value = values.get(key);
        return value == null ? null : converter.convert(value, valueType);
    }

    /**
     * Returns the value stored under the given {@code key}, converting it to the given {@code valueType} with the given
     * {@code converter} if the {@code key} is contained in this instance.
     * <p>
     * If the given {@code key} does not exist in this collection, the {@code defaultValue} is returned. The
     * {@code converter} is not invoked when the {@code defaultValue} is returned.
     *
     * @param key          The key for which to retrieve a value.
     * @param valueType    The desired type of the value to return, used during conversion.
     * @param converter    The {@code Converter} used to convert the recovered value to the given {@code valueType}.
     * @param defaultValue The default value to return when there is no {@code key} present in this collection.
     * @param <R>          The desired value type to return.
     * @return The value present for the given {@code key}, converted to {@code R} by the given {@code converter}.
     */
    @Nonnull
    public <R> R getOrDefault(@Nonnull String key,
                              @Nonnull Class<R> valueType,
                              @Nonnull Converter converter,
                              @Nonnull R defaultValue) {
        return values.containsKey(key) ? converter.convert(values.get(key), valueType) : defaultValue;
    }

    /**
     * <strong>This operation is not supported since {@code MetaData} is an immutable object.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported since {@code MetaData} is an immutable object.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported since {@code MetaData} is an immutable object.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void putAll(@Nonnull Map<? extends String, ? extends String> m) {
        throw new UnsupportedOperationException(UNSUPPORTED_MUTATION_MSG);
    }

    /**
     * <strong>This operation is not supported since {@code MetaData} is an immutable object.</strong>
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
    private record MetaDataCollector(Function<String, String> valueProvider)
            implements Collector<String, Map<String, String>, MetaData> {

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
        public Function<Map<String, String>, MetaData> finisher() {
            return MetaData::from;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }
}
