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

package org.axonframework.eventsourcing;

import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class StubProcessingContext implements ProcessingContext {

    private final Map<ResourceKey<?>, Object> resources = new ConcurrentHashMap<>();

    @Override
    public boolean containsResource(ResourceKey<?> key) {
        return resources.containsKey(key);
    }

    @Override
    public <T> T updateResource(ResourceKey<T> key, Function<T, T> updateFunction) {
        return (T) resources.compute(key, (id, current) -> updateFunction.apply((T) current));
    }

    @Override
    public <T> T getResource(ResourceKey<T> key) {
        return (T) resources.get(key);
    }

    @Override
    public <T> T computeResourceIfAbsent(ResourceKey<T> key, Supplier<T> supplier) {
        return (T) resources.computeIfAbsent(key, k -> supplier.get());
    }

    @Override
    public <T> T putResource(ResourceKey<T> key, T instance) {
        return (T) resources.put(key, instance);
    }

    @Override
    public <T> T putResourceIfAbsent(ResourceKey<T> key, T newValue) {
        return (T) resources.putIfAbsent(key, newValue);
    }

    @Override
    public <T> T removeResource(ResourceKey<T> key) {
        return (T) resources.remove(key);
    }

    @Override
    public <T> boolean removeResource(ResourceKey<T> key, T expectedInstance) {
        return resources.remove(key, expectedInstance);
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
        throw new UnsupportedOperationException("Lifecycle handlers not yet supported in StubProcessingContext");
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        throw new UnsupportedOperationException("Lifecycle handlers not yet supported in StubProcessingContext");
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        throw new UnsupportedOperationException("Lifecycle handlers not yet supported in StubProcessingContext");
    }
}
