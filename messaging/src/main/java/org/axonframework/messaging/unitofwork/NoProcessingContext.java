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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * No-op implementation of the {@link ProcessingContext}.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class NoProcessingContext implements ProcessingContext {

    /**
     * Constant of the {@link NoProcessingContext} to be used as a single reference.
     */
    public static final NoProcessingContext INSTANCE = new NoProcessingContext();

    private NoProcessingContext() {
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
        throw new UnsupportedOperationException("Cannot register lifecycle actions in this ProcessingContext");
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        throw new UnsupportedOperationException("Cannot register lifecycle actions in this ProcessingContext");
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        throw new UnsupportedOperationException("Cannot register lifecycle actions in this ProcessingContext");
    }

    @Override
    public boolean containsResource(ResourceKey<?> key) {
        return false;
    }

    @Override
    public <T> T getResource(ResourceKey<T> key) {
        return null;
    }

    @Override
    public <T> T putResource(ResourceKey<T> key, T resource) {
        throw new IllegalArgumentException("Cannot put resources in this ProcessingContext");
    }

    @Override
    public <T> T updateResource(ResourceKey<T> key, Function<T, T> resourceUpdater) {
        throw new IllegalArgumentException("Cannot update resources in this ProcessingContext");
    }

    @Override
    public <T> T putResourceIfAbsent(ResourceKey<T> key, T resource) {
        throw new IllegalArgumentException("Cannot put resources in this ProcessingContext");
    }

    @Override
    public <T> T computeResourceIfAbsent(ResourceKey<T> key, Supplier<T> resourceSupplier) {
        throw new IllegalArgumentException("Cannot compute resources in this ProcessingContext");
    }

    @Override
    public <T> T removeResource(ResourceKey<T> key) {
        return null;
    }

    @Override
    public <T> boolean removeResource(ResourceKey<T> key, T expectedResource) {
        return expectedResource == null;
    }
}
