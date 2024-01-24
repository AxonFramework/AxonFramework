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

package org.axonframework.messaging.unitofwork;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ResourceOverridingProcessingContext<R> implements ProcessingContext {

    private final ProcessingContext delegate;
    private final ResourceKey<R> resourceKey;
    private final AtomicReference<R> resource;

    public ResourceOverridingProcessingContext(ProcessingContext delegate,
                                               ResourceKey<R> resourceKey,
                                               R initialResource) {
        this.delegate = delegate;
        this.resourceKey = resourceKey;
        this.resource = new AtomicReference<>(initialResource);
    }

    @Override
    public boolean containsResource(ResourceKey<?> key) {
        return resourceKey.equals(key) || delegate.containsResource(key);
    }

    @Override
    public <T> T updateResource(ResourceKey<T> key, Function<T, T> updateFunction) {
        if (!resourceKey.equals(key)) {
            return delegate.updateResource(key, updateFunction);
        }
        //noinspection unchecked
        return (T) resource.updateAndGet((UnaryOperator<R>) updateFunction);
    }

    @Override
    public <T> T getResource(ResourceKey<T> key) {
        //noinspection unchecked
        return resourceKey.equals(key) ? (T) resource.get() : delegate.getResource(key);
    }

    @Override
    public <T> T computeResourceIfAbsent(ResourceKey<T> key, Supplier<T> supplier) {
        if (!resourceKey.equals(key)) {
            return delegate.computeResourceIfAbsent(key, supplier);
        }
        //noinspection unchecked
        return (T) resource.updateAndGet(current -> current == null ? (R) supplier.get() : current);
    }

    @Override
    public <T> T putResource(ResourceKey<T> key, T instance) {
        if (!resourceKey.equals(key)) {
            return delegate.putResource(key, instance);
        }
        //noinspection unchecked
        return (T) resource.getAndSet((R) instance);
    }

    @Override
    public <T> T putResourceIfAbsent(ResourceKey<T> key, T newValue) {
        if (!resourceKey.equals(key)) {
            return delegate.putResourceIfAbsent(key, newValue);
        }
        //noinspection unchecked
        return (T) resource.getAndUpdate(current -> current == null ? (R) newValue : current);
    }

    @Override
    public <T> T removeResource(ResourceKey<T> key) {
        if (!resourceKey.equals(key)) {
            return delegate.removeResource(key);
        }
        //noinspection unchecked
        return (T) resource.getAndSet(null);
    }

    @Override
    public <T> boolean removeResource(ResourceKey<T> key, T expectedInstance) {
        if (!resourceKey.equals(key)) {
            return delegate.removeResource(key, expectedInstance);
        }
        //noinspection unchecked
        return resource.compareAndSet((R) expectedInstance, null);
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
    public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.on(phase, action);
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        return delegate.onError(action);
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        return delegate.whenComplete(action);
    }

    @Override
    public ProcessingLifecycle runOn(Phase phase, Consumer<ProcessingContext> action) {
        return delegate.runOn(phase, action);
    }

    @Override
    public ProcessingLifecycle onPreInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPreInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnPreInvocation(Consumer<ProcessingContext> action) {
        return delegate.runOnPreInvocation(action);
    }

    @Override
    public ProcessingLifecycle onInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnInvocation(Consumer<ProcessingContext> action) {
        return delegate.runOnInvocation(action);
    }

    @Override
    public ProcessingLifecycle onPostInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPostInvocation(action);
    }

    @Override
    public ProcessingLifecycle runOnPostInvocation(Consumer<ProcessingContext> action) {
        return delegate.runOnPostInvocation(action);
    }

    @Override
    public ProcessingLifecycle onPrepareCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onPrepareCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnPrepareCommit(Consumer<ProcessingContext> action) {
        return delegate.runOnPrepareCommit(action);
    }

    @Override
    public ProcessingLifecycle onCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnCommit(Consumer<ProcessingContext> action) {
        return delegate.runOnCommit(action);
    }

    @Override
    public ProcessingLifecycle onAfterCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return delegate.onAfterCommit(action);
    }

    @Override
    public ProcessingLifecycle runOnAfterCommit(Consumer<ProcessingContext> action) {
        return delegate.runOnAfterCommit(action);
    }

    @Override
    public ProcessingLifecycle doFinally(Consumer<ProcessingContext> action) {
        return delegate.doFinally(action);
    }
}
