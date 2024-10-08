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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Implementation of the {@link IndexedEventMessage} allowing a generic payload of type {@code P}.
 *
 * @param <P> The type of payload carried by this {@link EventMessage}.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class GenericIndexedEventMessage<P> implements IndexedEventMessage<P> {

    private final EventMessage<P> delegate;
    private final Set<Index> indices;

    /**
     * Construct an {@link IndexedEventMessage} using the given {@code delegate} for all {@link EventMessage} operations
     * and the given {@code indices} for the {@link #indices()} method.
     *
     * @param delegate The delegate {@link EventMessage} used for all {@code EventMessage} related operations.
     * @param indices  The {@link Set} of {@link Index Indices} relating to the given {@code delegate}.
     */
    public GenericIndexedEventMessage(@Nonnull EventMessage<P> delegate,
                                      @Nonnull Set<Index> indices) {
        this.delegate = delegate;
        this.indices = indices;
    }

    @Override
    public String getIdentifier() {
        return this.delegate.getIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return this.delegate.getMetaData();
    }

    @Override
    public P getPayload() {
        return this.delegate.getPayload();
    }

    @Override
    public Class<P> getPayloadType() {
        return this.delegate.getPayloadType();
    }

    @Override
    public Instant getTimestamp() {
        return this.delegate.getTimestamp();
    }

    @Override
    public Set<Index> indices() {
        return this.indices;
    }

    @Override
    public EventMessage<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        return getMetaData().equals(metaData)
                ? this
                : new GenericIndexedEventMessage<>(this.delegate.withMetaData(metaData), this.indices);
    }

    @Override
    public EventMessage<P> andMetaData(@Nonnull Map<String, ?> metaData) {
        return getMetaData().equals(metaData)
                ? this
                : new GenericIndexedEventMessage<>(this.delegate.andMetaData(metaData), this.indices);
    }

    @Override
    public IndexedEventMessage<P> updateIndices(@Nonnull Function<Set<Index>, Set<Index>> updater) {
        return new GenericIndexedEventMessage<>(this, updater.apply(this.indices));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GenericIndexedEventMessage<?> that = (GenericIndexedEventMessage<?>) o;
        return Objects.equals(delegate, that.delegate) && Objects.equals(indices, that.indices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, indices);
    }
}
