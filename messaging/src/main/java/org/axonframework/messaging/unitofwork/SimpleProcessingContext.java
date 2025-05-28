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

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Implementation of the {@link ProcessingContext} that can only store resources.
 * Registering a lifecycle action will not take effect.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class SimpleProcessingContext implements ProcessingContext {
    private final Map<ResourceKey<?>, Object> resources = new ConcurrentHashMap<>();

    /**
     * Create a new simple {@link ProcessingContext} instance.
     * @return a new {@link SimpleProcessingContext} instance.
     */
    public static SimpleProcessingContext empty() {
        return new SimpleProcessingContext();
    }

    protected SimpleProcessingContext() {
        // No-arg constructor
    }

    @Override
    public boolean isStarted() {
        return false;
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
        return this;
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        return this;
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        return this;
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return resources.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        return (T) resources.get(key);
    }

    @Override
    public <T> T putResource(@Nonnull ResourceKey<T> key,
                             @Nonnull T resource) {
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
}
