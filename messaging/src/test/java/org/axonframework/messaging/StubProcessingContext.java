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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Stubbed implementation of the {@link ProcessingContext} used for testing purposes.
 *
 * @author Allard Buijze
 */
public class StubProcessingContext implements ProcessingContext {

    private final Map<ResourceKey<?>, Object> resources = new ConcurrentHashMap<>();
    private final Map<Phase, List<Function<ProcessingContext, CompletableFuture<?>>>> phaseActions = new ConcurrentHashMap<>();
    private final Phase currentPhase = DefaultPhases.PRE_INVOCATION;

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

    public CompletableFuture<Object> moveToPhase(Phase phase) {
        if (phase.isBefore(currentPhase)) {
            throw new IllegalArgumentException("Cannot move to a phase before the current phase");
        }
        if (!phase.isAfter(currentPhase)) {
            return CompletableFuture.completedFuture(null);
        }
        return phaseActions.keySet().stream()
                           .filter(p -> p.isAfter(currentPhase) && p.order() <= phase.order())
                           .sorted(Comparator.comparing(Phase::order))
                           .flatMap(p -> phaseActions.get(p).stream())
                           .reduce(CompletableFuture.completedFuture(null),
                                   (cf, action) -> cf.thenCompose(v -> (CompletableFuture<Object>) action.apply(this)),
                                   (cf1, cf2) -> cf2);
    }

    @Override
    public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        if(phase.order() <= currentPhase.order()) {
            throw new IllegalArgumentException("Cannot register an action for a phase that has already passed");
        }
        phaseActions.computeIfAbsent(phase, p -> new CopyOnWriteArrayList<>()).add(action);
        return this;
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        throw new UnsupportedOperationException("Lifecycle actions are not yet supported in the StubProcessingContext");
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        throw new UnsupportedOperationException("Lifecycle actions are not yet supported in the StubProcessingContext");
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
    public <T> ProcessingContext withResource(@Nonnull ResourceKey<T> key,
                                              @Nonnull T resource) {
        resources.put(key, resource);
        return this;
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
        return (T) resources.compute(key, (id, current) -> resourceUpdater.apply((T) current));
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
        return (T) resources.computeIfAbsent(key, k -> resourceSupplier.get());
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
