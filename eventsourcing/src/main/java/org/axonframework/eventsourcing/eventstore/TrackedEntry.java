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
import org.axonframework.common.Context;
import org.axonframework.common.SimpleContext;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A tracking-specific {@link Entry} implementation, combining an {@link EventMessage} and {@link TrackingToken}.
 * <p>
 * The {@code token} refers to the position of the given {@code message} in the {@link MessageStream} it originates from.
 *
 * @param <E> The type of {@link EventMessage} contained in this entry.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class TrackedEntry<E extends EventMessage<?>> implements Entry<E> {

    private final E event;
    private final Context context;

    /**
     * Construct a {@link TrackedEntry} with the given {@code event} and {@code token}.
     *
     * @param event The {@link EventMessage} contained in this entry.
     * @param token The {@link TrackingToken} defining the position of the given {@code event}.
     */
    TrackedEntry(E event, TrackingToken token) {
        this.event = event;
        this.context = new SimpleContext();
        TrackingToken.addToContext(context, token);
    }

    @Override
    public E message() {
        return event;
    }

    @Override
    public <RM extends Message<?>> Entry<RM> map(@Nonnull Function<E, RM> mapper) {
        Entry<RM> mappedMessageEntry = MessageStream.entryFor(mapper.apply(event));
        mappedMessageEntry.putAll(context);
        return mappedMessageEntry;
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
    public <T> T putResource(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        return this.context.putResource(key, resource);
    }

    @Override
    public void putAll(@Nonnull Context context) {
        this.context.putAll(context);
    }

    @Override
    public <T> T updateResource(@Nonnull ResourceKey<T> key, @Nonnull UnaryOperator<T> resourceUpdater) {
        return this.context.updateResource(key, resourceUpdater);
    }

    @Override
    public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        return this.context.putResourceIfAbsent(key, resource);
    }

    @Override
    public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull Supplier<T> resourceSupplier) {
        return this.context.computeResourceIfAbsent(key, resourceSupplier);
    }

    @Override
    public <T> T removeResource(@Nonnull ResourceKey<T> key) {
        return this.context.removeResource(key);
    }

    @Override
    public <T> boolean removeResource(@Nonnull ResourceKey<T> key, @Nonnull T expectedResource) {
        return this.context.removeResource(key, expectedResource);
    }

    @Override
    public Map<ResourceKey<?>, ?> asMap() {
        return this.context.asMap();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TrackedEntry<?> that = (TrackedEntry<?>) o;
        return Objects.equals(event, that.event) && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, context);
    }

    @Override
    public String toString() {
        return "TrackedEntry{" +
                "event=" + event +
                ", context=" + context +
                '}';
    }
}
