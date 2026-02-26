/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.unitofwork;

import org.jspecify.annotations.NonNull;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A {@link ProcessingContext} implementation overriding a single resource from the wrapping {@code ProcessingContext}.
 * <p>
 * Should be used to instantiate a new {@code ProcessingContext} for which only a single resource should be adjusted
 * compared to its delegate.
 *
 * @param <R> The type of the resource this resource-overriding {@link ProcessingContext} overrides.
 * @author Allard Buijze
 * @since 5.0.0
 */
public class ResourceOverridingProcessingContext<R> implements ProcessingContext {

    private final ProcessingContext delegate;
    private final ResourceKey<R> key;
    private final AtomicReference<R> resource;

    /**
     * Constructs a resource-overriding {@link ProcessingContext} using the provided parameters.
     *
     * @param delegate The {@link ProcessingContext} to <em>only</em> override the given {@code key} for.
     * @param key      The {@link ResourceKey} to override in the {@code delegate} {@link ProcessingContext}.
     * @param resource The resource of type {@code R} that's overridden with the given {@code key}.
     */
    public ResourceOverridingProcessingContext(@NonNull ProcessingContext delegate,
                                               @NonNull ResourceKey<R> key, R resource) {
        this.delegate = delegate;
        this.key = key;
        this.resource = new AtomicReference<>(resource);
    }

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean isError() {
        return delegate.isError();
    }

    @Override
    public boolean isCommitted() {
        return delegate.isCommitted();
    }

    @Override
    public boolean isCompleted() {
        return delegate.isCompleted();
    }

    @Override
    public ProcessingLifecycle on(@NonNull Phase phase, @NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.on(phase, action);
    }

    @Override
    public ProcessingLifecycle runOn(@NonNull Phase phase, @NonNull Consumer<ProcessingContext> action) {
        return delegate.runOn(phase, action);
    }

    @Override
    public ProcessingLifecycle onPreInvocation(@NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPreInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnPreInvocation(@NonNull Consumer<ProcessingContext> action) {
        return delegate.runOnPreInvocation(action);
    }

    @Override
    public ProcessingLifecycle onInvocation(@NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnInvocation(@NonNull Consumer<ProcessingContext> action) {
        return delegate.runOnInvocation(action);
    }

    @Override
    public ProcessingLifecycle onPostInvocation(@NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPostInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnPostInvocation(@NonNull Consumer<ProcessingContext> action) {
        return delegate.runOnPostInvocation(action);
    }

    @Override
    public ProcessingLifecycle onPrepareCommit(@NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPrepareCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnPrepareCommit(@NonNull Consumer<ProcessingContext> action) {
        return delegate.runOnPrepareCommit(action);
    }

    @Override
    public ProcessingLifecycle onCommit(@NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnCommit(@NonNull Consumer<ProcessingContext> action) {
        return delegate.runOnCommit(action);
    }

    @Override
    public ProcessingLifecycle onAfterCommit(@NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onAfterCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnAfterCommit(@NonNull Consumer<ProcessingContext> action) {
        return delegate.runOnAfterCommit(action);
    }

    @Override
    public ProcessingLifecycle onError(@NonNull ErrorHandler action) {
        return delegate.onError(action);
    }

    @Override
    public ProcessingLifecycle whenComplete(@NonNull Consumer<ProcessingContext> action) {
        return delegate.whenComplete(action);
    }

    @Override
    public ProcessingLifecycle doFinally(@NonNull Consumer<ProcessingContext> action) {
        return delegate.doFinally(action);
    }

    @Override
    public boolean containsResource(@NonNull ResourceKey<?> key) {
        return this.key.equals(key) || delegate.containsResource(key);
    }

    @Override
    public <T> T getResource(@NonNull ResourceKey<T> key) {
        //noinspection unchecked
        return this.key.equals(key) ? (T) resource.get() : delegate.getResource(key);
    }

    @Override
    public Map<ResourceKey<?>, Object> resources() {
        var allResources = delegate.resources();
        allResources.put(key, resource.get());
        return allResources;
    }

    @Override
    public <T> T putResource(@NonNull ResourceKey<T> key,
                             @NonNull T resource) {
        //noinspection unchecked
        return this.key.equals(key)
                ? (T) this.resource.getAndSet((R) resource)
                : delegate.putResource(key, resource);
    }

    @Override
    public <T> T updateResource(@NonNull ResourceKey<T> key,
                                @NonNull UnaryOperator<T> resourceUpdater) {
        //noinspection unchecked
        return this.key.equals(key)
                ? (T) resource.updateAndGet((UnaryOperator<R>) resourceUpdater)
                : delegate.updateResource(key, resourceUpdater);
    }

    @Override
    public <T> T computeResourceIfAbsent(@NonNull ResourceKey<T> key,
                                         @NonNull Supplier<T> resourceSupplier) {
        if (this.key.equals(key)) {
            //noinspection unchecked
            return (T) resource.updateAndGet(current -> current == null ? (R) resourceSupplier.get() : current);
        }
        return delegate.computeResourceIfAbsent(key, resourceSupplier);
    }

    @Override
    public <T> T putResourceIfAbsent(@NonNull ResourceKey<T> key,
                                     @NonNull T resource) {
        if (this.key.equals(key)) {
            //noinspection unchecked
            return (T) this.resource.getAndUpdate(current -> current == null ? (R) resource : current);
        }
        return delegate.putResourceIfAbsent(key, resource);
    }

    @Override
    public <T> T removeResource(@NonNull ResourceKey<T> key) {
        if (!this.key.equals(key)) {
            return delegate.removeResource(key);
        }
        //noinspection unchecked
        return (T) resource.getAndSet(null);
    }

    @Override
    public <T> boolean removeResource(@NonNull ResourceKey<T> key,
                                      @NonNull T expectedResource) {
        //noinspection unchecked
        return this.key.equals(key)
                ? resource.compareAndSet((R) expectedResource, null)
                : delegate.removeResource(key, expectedResource);
    }

    @NonNull
    @Override
    public <C> C component(@NonNull Class<C> type) {
        return delegate.component(type);
    }

    @NonNull
    @Override
    public <C> C component(@NonNull Class<C> type, @Nullable String name) {
        return delegate.component(type, name);
    }
}
