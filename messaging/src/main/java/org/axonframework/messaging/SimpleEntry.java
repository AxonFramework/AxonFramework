/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.common.Context;
import org.axonframework.common.SimpleContext;
import org.axonframework.messaging.MessageStream.Entry;

import java.util.Map;
import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Simple implementation of the {@link Entry} containing a single {@link Message} implementation of type {@code M} and a
 * {@link Context}.
 *
 * @param <M>     The type of {@link Message} contained in this {@link Entry} implementation.
 * @param message The {@link Message} of type {@code M} contained in this {@link Entry}.
 * @param context Maintains additional contextual information of this entry.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record SimpleEntry<M extends Message<?>>(@Nullable M message, @Nonnull Context context) implements Entry<M> {

    /**
     * Construct a {@link SimpleEntry} with the given {@code message}, setting the {@link Context} to a
     * {@link SimpleContext}.
     *
     * @param message The {@link Message} of type {@code M} contained in this {@link Entry}.
     */
    public SimpleEntry(@Nullable M message) {
        this(message, new SimpleContext());
    }

    public SimpleEntry {
        assertNonNull(context, "The context cannot be null");
    }

    @Override
    public <RM extends Message<?>> Entry<RM> map(@Nonnull Function<M, RM> mapper) {
        return new SimpleEntry<>(mapper.apply(message()), context);
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return this.context.containsResource(key);
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        return this.context.getResource(key);
    }

    @Override
    public <T> Context withResource(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        return this.context.withResource(key, resource);
    }

    @Override
    public void putAll(@Nonnull Context context) {
        this.context.putAll(context);
    }

    @Override
    public Map<ResourceKey<?>, ?> asMap() {
        return this.context.asMap();
    }
}
