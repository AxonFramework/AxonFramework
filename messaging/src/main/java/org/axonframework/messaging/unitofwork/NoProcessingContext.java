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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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

    private static final String UNSUPPORTED_MESSAGE = "Cannot register lifecycle actions in this ProcessingContext";

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
        return false;
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        return null;
    }

    @Override
    public <T> T putResource(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        throw new IllegalArgumentException("Cannot put resources in this ProcessingContext");
    }

    @Override
    public <T> T updateResource(@Nonnull ResourceKey<T> key, @Nonnull UnaryOperator<T> resourceUpdater) {
        throw new IllegalArgumentException("Cannot update resources in this ProcessingContext");
    }

    @Override
    public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        throw new IllegalArgumentException("Cannot put resources in this ProcessingContext");
    }

    @Override
    public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull Supplier<T> resourceSupplier) {
        throw new IllegalArgumentException("Cannot compute resources in this ProcessingContext");
    }

    @Override
    public <T> T removeResource(@Nonnull ResourceKey<T> key) {
        return null;
    }

    @Override
    public <T> boolean removeResource(@Nonnull ResourceKey<T> key, @Nullable T expectedResource) {
        return expectedResource == null;
    }
}
