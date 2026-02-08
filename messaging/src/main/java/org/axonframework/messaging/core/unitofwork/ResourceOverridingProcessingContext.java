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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.ApplicationContext;

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
    public ResourceOverridingProcessingContext(@Nonnull ProcessingContext delegate,
                                               @Nonnull ResourceKey<R> key, R resource) {
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
    public ProcessingLifecycle on(@Nonnull Phase phase,
                                  @Nonnull Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.on(phase, ctx -> action.apply(this));
    }

    @Override
    public ProcessingLifecycle onError(@Nonnull ErrorHandler action) {
        return delegate.onError((ctx, phase, error) -> action.handle(this, phase, error));
    }

    @Override
    public ProcessingLifecycle whenComplete(@Nonnull Consumer<ProcessingContext> action) {
        return delegate.whenComplete(ctx -> action.accept(this));
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return this.key.equals(key) || delegate.containsResource(key);
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
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
    public <T> T putResource(@Nonnull ResourceKey<T> key,
                             @Nonnull T resource) {
        //noinspection unchecked
        return this.key.equals(key)
                ? (T) this.resource.getAndSet((R) resource)
                : delegate.putResource(key, resource);
    }

    @Override
    public <T> T updateResource(@Nonnull ResourceKey<T> key,
                                @Nonnull UnaryOperator<T> resourceUpdater) {
        //noinspection unchecked
        return this.key.equals(key)
                ? (T) resource.updateAndGet((UnaryOperator<R>) resourceUpdater)
                : delegate.updateResource(key, resourceUpdater);
    }

    @Override
    public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                         @Nonnull Supplier<T> resourceSupplier) {
        if (this.key.equals(key)) {
            //noinspection unchecked
            return (T) resource.updateAndGet(current -> current == null ? (R) resourceSupplier.get() : current);
        }
        return delegate.computeResourceIfAbsent(key, resourceSupplier);
    }

    @Override
    public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                     @Nonnull T resource) {
        if (this.key.equals(key)) {
            //noinspection unchecked
            return (T) this.resource.getAndUpdate(current -> current == null ? (R) resource : current);
        }
        return delegate.putResourceIfAbsent(key, resource);
    }

    @Override
    public <T> T removeResource(@Nonnull ResourceKey<T> key) {
        if (!this.key.equals(key)) {
            return delegate.removeResource(key);
        }
        //noinspection unchecked
        return (T) resource.getAndSet(null);
    }

    @Override
    public <T> boolean removeResource(@Nonnull ResourceKey<T> key,
                                      @Nonnull T expectedResource) {
        //noinspection unchecked
        return this.key.equals(key)
                ? resource.compareAndSet((R) expectedResource, null)
                : delegate.removeResource(key, expectedResource);
    }

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type) {
        ApplicationContext ctx = getResource(APPLICATION_CONTEXT);
        if (ctx != null) {
            return ctx.component(type);
        }
        return delegate.component(type);
    }

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type, @Nullable String name) {
        ApplicationContext ctx = getResource(APPLICATION_CONTEXT);
        if (ctx != null) {
            return ctx.component(type, name);
        }
        return delegate.component(type, name);
    }
}
