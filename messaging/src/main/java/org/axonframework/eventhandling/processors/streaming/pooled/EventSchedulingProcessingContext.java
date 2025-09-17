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

package org.axonframework.eventhandling.processors.streaming.pooled;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.ApplicationContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A concrete implementation of the {@link ProcessingContext} interface specifically designed for event scheduling in
 * the {@link WorkPackage}. This implementation allows retrieving components and manage resources while disallowing
 * lifecycle actions. Currently, the only usage of the context is for
 * {@link org.axonframework.eventhandling.EventHandlingComponent#sequenceIdentifierFor(EventMessage, ProcessingContext)}
 * execution.
 */
@Internal
class EventSchedulingProcessingContext implements ProcessingContext {

    private static final String UNSUPPORTED_MESSAGE = "Cannot register lifecycle actions in this ProcessingContext";
    private final ConcurrentMap<ResourceKey<?>, Object> resources = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    EventSchedulingProcessingContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public boolean isError() {
        return false;
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public boolean isCompleted() {
        return false;
    }

    @Override
    public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return resources.containsKey(key);
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.get(key);
    }

    @Override
    public Map<ResourceKey<?>, Object> resources() {
        return Map.copyOf(resources);
    }

    @Override
    public <T> T putResource(@Nonnull ResourceKey<T> key,
                             @Nonnull T resource) {
        //noinspection unchecked
        return (T) resources.put(key, resource);
    }

    @Override
    public <T> T updateResource(@Nonnull ResourceKey<T> key,
                                @Nonnull UnaryOperator<T> resourceUpdater) {
        //noinspection unchecked
        return (T) resources.compute(key, (k, v) -> resourceUpdater.apply((T) v));
    }

    @Override
    public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                     @Nonnull T resource) {
        //noinspection unchecked
        return (T) resources.putIfAbsent(key, resource);
    }

    @Override
    public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                         @Nonnull Supplier<T> resourceSupplier) {
        //noinspection unchecked
        return (T) resources.computeIfAbsent(key, t -> resourceSupplier.get());
    }

    @Override
    public <T> T removeResource(@Nonnull ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.remove(key);
    }

    @Override
    public <T> boolean removeResource(@Nonnull ResourceKey<T> key,
                                      @Nonnull T expectedResource) {
        return resources.remove(key, expectedResource);
    }

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type, @Nullable String name) {
        return applicationContext.component(type, name);
    }
}
